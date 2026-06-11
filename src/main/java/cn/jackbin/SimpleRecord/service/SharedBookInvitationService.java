package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.SharedBookInvitationDO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SharedBookInvitationService extends IService<SharedBookInvitationDO> {

    SharedBookInvitationDO createInvitation(Integer recordBookId, Integer inviterUserId, String permission);

    void acceptInvitation(String inviteCode, Integer userId);

    void cancelInvitation(Long invitationId, Integer userId);
}
