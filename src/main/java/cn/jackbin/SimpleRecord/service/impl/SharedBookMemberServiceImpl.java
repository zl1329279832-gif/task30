package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.SharedBookMemberMapper;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
public class SharedBookMemberServiceImpl extends ServiceImpl<SharedBookMemberMapper, SharedBookMemberDO>
        implements SharedBookMemberService {

    @Autowired
    private SharedBookMemberMapper sharedBookMemberMapper;

    @Autowired
    private RedisUtil redisUtil;

    private static final long MEMBER_CACHE_TTL = 1800; // 30分钟

    @Override
    public List<SharedBookMemberDO> getMembersByBookId(Integer recordBookId) {
        return sharedBookMemberMapper.queryMembersByBookId(recordBookId);
    }

    @Override
    public SharedBookMemberDO getByBookAndUser(Integer recordBookId, Integer userId) {
        return sharedBookMemberMapper.queryByBookAndUser(recordBookId, userId);
    }

    @Override
    public boolean hasPermission(Integer recordBookId, Integer userId, String permission) {
        String cacheKey = RedisKey.SHARED_BOOK_MEMBER_PREFIX + recordBookId + ":" + userId;
        // 先查Redis缓存
        Object cached = redisUtil.get(cacheKey);
        String permissions;
        if (cached != null) {
            permissions = cached.toString();
        } else {
            SharedBookMemberDO member = getByBookAndUser(recordBookId, userId);
            if (member == null) {
                return false;
            }
            permissions = member.getPermission();
            redisUtil.set(cacheKey, permissions, MEMBER_CACHE_TTL);
        }
        List<String> permList = Arrays.asList(permissions.split(","));
        return permList.contains(permission);
    }

    @Override
    public void checkPermission(Integer recordBookId, Integer userId, String permission) {
        if (!hasPermission(recordBookId, userId, permission)) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
        }
    }

    @Override
    public void addMember(Integer recordBookId, Integer userId, String permission, String nickname) {
        SharedBookMemberDO existing = getByBookAndUser(recordBookId, userId);
        if (existing != null) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_MEMBER_EXISTS);
        }
        SharedBookMemberDO member = SharedBookMemberDO.builder()
                .recordBookId(recordBookId)
                .userId(userId)
                .permission(permission)
                .nickname(nickname)
                .joinTime(new Date())
                .status(0)
                .build();
        save(member);
        invalidateCache(recordBookId, userId);
    }

    @Override
    public void updatePermission(Long memberId, String permission) {
        SharedBookMemberDO member = getById(memberId);
        if (member == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
        member.setPermission(permission);
        updateById(member);
        invalidateCache(member.getRecordBookId(), member.getUserId());
    }

    @Override
    public void removeMember(Integer recordBookId, Integer userId) {
        SharedBookMemberDO member = getByBookAndUser(recordBookId, userId);
        if (member == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
        removeById(member.getId());
        invalidateCache(recordBookId, userId);
    }

    private void invalidateCache(Integer recordBookId, Integer userId) {
        String cacheKey = RedisKey.SHARED_BOOK_MEMBER_PREFIX + recordBookId + ":" + userId;
        redisUtil.del(cacheKey);
    }
}
