package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface SharedBookMemberService extends IService<SharedBookMemberDO> {

    List<SharedBookMemberDO> getMembersByBookId(Integer recordBookId);

    SharedBookMemberDO getByBookAndUser(Integer recordBookId, Integer userId);

    boolean hasPermission(Integer recordBookId, Integer userId, String permission);

    void checkPermission(Integer recordBookId, Integer userId, String permission);

    void addMember(Integer recordBookId, Integer userId, String permission, String nickname);

    void updatePermission(Long memberId, String permission);

    void removeMember(Integer recordBookId, Integer userId);
}
