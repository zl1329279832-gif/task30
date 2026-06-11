package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.SharedBookInvitationDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.entity.UserDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.SharedBookInvitationMapper;
import cn.jackbin.SimpleRecord.service.SharedBookInvitationService;
import cn.jackbin.SimpleRecord.service.SharedBookLogService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

@Service
public class SharedBookInvitationServiceImpl extends ServiceImpl<SharedBookInvitationMapper, SharedBookInvitationDO>
        implements SharedBookInvitationService {

    @Autowired
    private SharedBookMemberService sharedBookMemberService;

    @Autowired
    private SharedBookLogService sharedBookLogService;

    @Autowired
    private UserService userService;

    @Override
    public SharedBookInvitationDO createInvitation(Integer recordBookId, Integer inviterUserId, String permission) {
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);

        SharedBookInvitationDO invitation = SharedBookInvitationDO.builder()
                .recordBookId(recordBookId)
                .inviterUserId(inviterUserId)
                .inviteCode(inviteCode)
                .permission(permission)
                .expireTime(cal.getTime())
                .status(RecordConstant.INVITE_PENDING)
                .build();
        save(invitation);

        sharedBookLogService.log(recordBookId, inviterUserId, "INVITE", invitation.getId(),
                "创建邀请码: " + inviteCode);

        return invitation;
    }

    @Transactional
    @Override
    public void acceptInvitation(String inviteCode, Integer userId) {
        LambdaQueryWrapper<SharedBookInvitationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SharedBookInvitationDO::getInviteCode, inviteCode)
                .eq(SharedBookInvitationDO::getStatus, RecordConstant.INVITE_PENDING);
        SharedBookInvitationDO invitation = getOne(wrapper);

        if (invitation == null) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_INVITE_INVALID);
        }

        if (invitation.getExpireTime().before(new Date())) {
            invitation.setStatus(RecordConstant.INVITE_EXPIRED);
            updateById(invitation);
            throw new BusinessException(CodeMsg.SHARED_BOOK_INVITE_EXPIRED);
        }

        UserDO user = userService.getById(userId);
        String nickname = user != null ? user.getNickname() : null;

        sharedBookMemberService.addMember(invitation.getRecordBookId(), userId,
                invitation.getPermission(), nickname);

        invitation.setInviteeUserId(userId);
        invitation.setStatus(RecordConstant.INVITE_ACCEPTED);
        updateById(invitation);

        sharedBookLogService.log(invitation.getRecordBookId(), userId, "JOIN",
                invitation.getId(), "通过邀请码加入账本");
    }

    @Override
    public void cancelInvitation(Long invitationId, Integer userId) {
        SharedBookInvitationDO invitation = getById(invitationId);
        if (invitation == null) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_INVITE_INVALID);
        }
        if (!invitation.getInviterUserId().equals(userId)) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
        }
        invitation.setStatus(RecordConstant.INVITE_CANCELLED);
        updateById(invitation);
    }
}
