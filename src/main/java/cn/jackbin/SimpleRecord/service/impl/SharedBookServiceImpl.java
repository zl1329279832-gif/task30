package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.SharedBookMemberMapper;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.SharedBookAuditLogService;
import cn.jackbin.SimpleRecord.service.SharedBookService;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 共享账本服务实现
 */
@Service
public class SharedBookServiceImpl extends ServiceImpl<SharedBookMemberMapper, SharedBookMemberDO>
        implements SharedBookService {

    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final long INVITE_CODE_EXPIRE = 86400L * 30; // 30天
    private static final long PERM_CACHE_EXPIRE = 300L; // 5分钟

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private RecordBookService recordBookService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    @Transactional
    public RecordBookDO createSharedBook(Integer userId, String name, String remark) {
        // 创建账本
        RecordBookDO book = new RecordBookDO();
        book.setUserId(userId);
        book.setName(name);
        book.setRemark(remark);
        book.setIsUserDefault(RecordConstant.NOT_USER_DEFAULT);
        book.setOrderNo(0);
        book.setStatus(0);
        book.setBookType(RecordConstant.BOOK_TYPE_SHARED);
        book.setOwnerUserId(userId);
        book.setInviteCode(generateUniqueInviteCode());
        recordBookService.save(book);

        // 添加创建者为owner (全权限)
        SharedBookMemberDO owner = SharedBookMemberDO.builder()
                .bookId(book.getId().intValue())
                .userId(userId)
                .permissions("entry,review,view,settlement")
                .nickname(null)
                .status(0)
                .build();
        save(owner);

        // 缓存邀请码
        redisUtil.set(RedisKey.INVITE_CODE_PREFIX + book.getInviteCode(),
                book.getId().intValue(), INVITE_CODE_EXPIRE);

        auditLogService.log(book.getId().intValue(), userId, "BOOK_CREATE", "BOOK", book.getId(), null);
        return book;
    }

    @Override
    @Transactional
    public String regenerateInviteCode(Integer bookId, Integer userId) {
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || !userId.equals(book.getOwnerUserId())) {
            throw new BusinessException(CodeMsg.INVITE_CODE_REGENERATE_FORBIDDEN);
        }
        // 删除旧邀请码缓存
        if (book.getInviteCode() != null) {
            redisUtil.del(RedisKey.INVITE_CODE_PREFIX + book.getInviteCode());
        }
        String newCode = generateUniqueInviteCode();
        book.setInviteCode(newCode);
        recordBookService.updateById(book);
        redisUtil.set(RedisKey.INVITE_CODE_PREFIX + newCode, bookId, INVITE_CODE_EXPIRE);

        auditLogService.log(bookId, userId, "INVITE_CODE_REGENERATE", "BOOK", (long) bookId, null);
        return newCode;
    }

    @Override
    @Transactional
    public RecordBookDO joinByInviteCode(Integer userId, String inviteCode) {
        // 查缓存 → 查DB
        Object cachedBookId = redisUtil.get(RedisKey.INVITE_CODE_PREFIX + inviteCode);
        Integer bookId;
        if (cachedBookId != null) {
            bookId = ((Number) cachedBookId).intValue();
        } else {
            RecordBookDO book = recordBookService.getOne(
                    new QueryWrapper<RecordBookDO>().eq("invite_code", inviteCode)
                            .eq("book_type", RecordConstant.BOOK_TYPE_SHARED));
            if (book == null) {
                throw new BusinessException(CodeMsg.INVALID_INVITE_CODE);
            }
            bookId = book.getId().intValue();
            // 回填缓存
            redisUtil.set(RedisKey.INVITE_CODE_PREFIX + inviteCode, bookId, INVITE_CODE_EXPIRE);
        }

        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || book.getBookType() != RecordConstant.BOOK_TYPE_SHARED) {
            throw new BusinessException(CodeMsg.INVALID_INVITE_CODE);
        }

        // 检查是否已是成员
        SharedBookMemberDO existing = getMember(bookId, userId);
        if (existing != null && existing.getStatus() == 0) {
            throw new BusinessException(CodeMsg.ALREADY_MEMBER);
        }

        if (existing != null) {
            // 重新激活
            existing.setStatus(0);
            existing.setPermissions("entry,view");
            updateById(existing);
        } else {
            // 新成员
            SharedBookMemberDO member = SharedBookMemberDO.builder()
                    .bookId(bookId)
                    .userId(userId)
                    .permissions("entry,view")
                    .status(0)
                    .build();
            save(member);
        }

        // 清除权限缓存
        redisUtil.del(RedisKey.SHARED_BOOK_PERMS + bookId + ":" + userId);

        auditLogService.log(bookId, userId, "MEMBER_JOIN", "MEMBER", (long) userId, null);
        return book;
    }

    @Override
    @Transactional
    public void addMember(Integer bookId, Integer operatorId, Integer targetUserId, String permissions) {
        checkOwner(bookId, operatorId);
        SharedBookMemberDO existing = getMember(bookId, targetUserId);
        if (existing != null && existing.getStatus() == 0) {
            throw new BusinessException(CodeMsg.ALREADY_MEMBER);
        }
        if (existing != null) {
            existing.setStatus(0);
            existing.setPermissions(permissions);
            updateById(existing);
        } else {
            SharedBookMemberDO member = SharedBookMemberDO.builder()
                    .bookId(bookId)
                    .userId(targetUserId)
                    .permissions(permissions)
                    .status(0)
                    .build();
            save(member);
        }
        redisUtil.del(RedisKey.SHARED_BOOK_PERMS + bookId + ":" + targetUserId);
        auditLogService.log(bookId, operatorId, "MEMBER_ADD", "MEMBER", (long) targetUserId,
                "{\"permissions\":\"" + permissions + "\"}");
    }

    @Override
    @Transactional
    public void removeMember(Integer bookId, Integer operatorId, Integer targetUserId) {
        checkOwner(bookId, operatorId);
        RecordBookDO book = recordBookService.getById(bookId);
        if (book != null && targetUserId.equals(book.getOwnerUserId())) {
            throw new BusinessException(CodeMsg.NOT_BOOK_OWNER, "不能移除账本所有者");
        }
        SharedBookMemberDO member = getMember(bookId, targetUserId);
        if (member != null && member.getStatus() == 0) {
            member.setStatus(1);
            updateById(member);
        }
        redisUtil.del(RedisKey.SHARED_BOOK_PERMS + bookId + ":" + targetUserId);
        auditLogService.log(bookId, operatorId, "MEMBER_REMOVE", "MEMBER", (long) targetUserId, null);
    }

    @Override
    @Transactional
    public void updatePermissions(Integer bookId, Integer operatorId, Integer targetUserId, String permissions) {
        checkOwner(bookId, operatorId);
        SharedBookMemberDO member = getMember(bookId, targetUserId);
        if (member == null || member.getStatus() != 0) {
            throw new BusinessException(CodeMsg.NOT_BOOK_MEMBER);
        }
        member.setPermissions(permissions);
        updateById(member);
        redisUtil.del(RedisKey.SHARED_BOOK_PERMS + bookId + ":" + targetUserId);
        auditLogService.log(bookId, operatorId, "PERMISSION_CHANGE", "MEMBER", (long) targetUserId,
                "{\"permissions\":\"" + permissions + "\"}");
    }

    @Override
    public List<SharedBookMemberDO> listMembers(Integer bookId) {
        return list(new QueryWrapper<SharedBookMemberDO>()
                .eq("book_id", bookId)
                .eq("status", 0));
    }

    @Override
    public SharedBookMemberDO getMember(Integer bookId, Integer userId) {
        return getOne(new QueryWrapper<SharedBookMemberDO>()
                .eq("book_id", bookId)
                .eq("user_id", userId));
    }

    @Override
    public boolean hasPermission(Integer bookId, Integer userId, String permission) {
        // 先检查是否为owner
        RecordBookDO book = recordBookService.getById(bookId);
        if (book != null && userId.equals(book.getOwnerUserId())) {
            return true;
        }
        // 查缓存
        String cacheKey = RedisKey.SHARED_BOOK_PERMS + bookId + ":" + userId;
        Object cached = redisUtil.get(cacheKey);
        String perms;
        if (cached != null) {
            perms = cached.toString();
        } else {
            SharedBookMemberDO member = getMember(bookId, userId);
            if (member == null || member.getStatus() != 0) {
                return false;
            }
            perms = member.getPermissions();
            redisUtil.set(cacheKey, perms, PERM_CACHE_EXPIRE);
        }
        return Arrays.asList(perms.split(",")).contains(permission);
    }

    @Override
    public void checkPermission(Integer bookId, Integer userId, String permission) {
        if (!hasPermission(bookId, userId, permission)) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_PERMISSION_DENIED);
        }
    }

    @Override
    public List<RecordBookDO> listSharedBooks(Integer userId) {
        List<SharedBookMemberDO> memberships = list(new QueryWrapper<SharedBookMemberDO>()
                .eq("user_id", userId)
                .eq("status", 0));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Integer> bookIds = memberships.stream()
                .map(SharedBookMemberDO::getBookId)
                .collect(Collectors.toList());
        return recordBookService.list(new QueryWrapper<RecordBookDO>()
                .in("id", bookIds)
                .eq("book_type", RecordConstant.BOOK_TYPE_SHARED));
    }

    @Override
    public List<RecordBookDO> listAllBooks(Integer userId) {
        // 个人账本
        List<RecordBookDO> personalBooks = recordBookService.list(
                new QueryWrapper<RecordBookDO>().eq("user_id", userId));
        // 共享账本
        List<RecordBookDO> sharedBooks = listSharedBooks(userId);
        personalBooks.addAll(sharedBooks);
        return personalBooks;
    }

    private void checkOwner(Integer bookId, Integer userId) {
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || !userId.equals(book.getOwnerUserId())) {
            throw new BusinessException(CodeMsg.NOT_BOOK_OWNER);
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                sb.append(INVITE_CHARS.charAt(secureRandom.nextInt(INVITE_CHARS.length())));
            }
            code = sb.toString();
        } while (redisUtil.hasKey(RedisKey.INVITE_CODE_PREFIX + code));
        return code;
    }
}
