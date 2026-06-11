package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 共享账本服务
 */
public interface SharedBookService extends IService<SharedBookMemberDO> {

    /**
     * 创建共享账本
     */
    RecordBookDO createSharedBook(Integer userId, String name, String remark);

    /**
     * 生成/刷新邀请码
     */
    String regenerateInviteCode(Integer bookId, Integer userId);

    /**
     * 通过邀请码加入账本
     */
    RecordBookDO joinByInviteCode(Integer userId, String inviteCode);

    /**
     * 直接添加成员
     */
    void addMember(Integer bookId, Integer operatorId, Integer targetUserId, String permissions);

    /**
     * 移除成员
     */
    void removeMember(Integer bookId, Integer operatorId, Integer targetUserId);

    /**
     * 更新成员权限
     */
    void updatePermissions(Integer bookId, Integer operatorId, Integer targetUserId, String permissions);

    /**
     * 列出账本所有成员
     */
    List<SharedBookMemberDO> listMembers(Integer bookId);

    /**
     * 获取某用户在某账本的成员信息
     */
    SharedBookMemberDO getMember(Integer bookId, Integer userId);

    /**
     * 检查是否有某权限
     */
    boolean hasPermission(Integer bookId, Integer userId, String permission);

    /**
     * 校验权限 (无权限抛异常)
     */
    void checkPermission(Integer bookId, Integer userId, String permission);

    /**
     * 列出用户参与的所有共享账本
     */
    List<RecordBookDO> listSharedBooks(Integer userId);

    /**
     * 列出用户所有账本(个人+共享)
     */
    List<RecordBookDO> listAllBooks(Integer userId);
}
