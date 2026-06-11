package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_shared_book_invitation")
public class SharedBookInvitationDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 共享账本ID
     */
    private Integer recordBookId;

    /**
     * 邀请人ID
     */
    private Integer inviterUserId;

    /**
     * 被邀请人ID
     */
    private Integer inviteeUserId;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 邀请附带的权限
     */
    private String permission;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 状态：0=待接受, 1=已接受, 2=已过期, 3=已取消
     */
    private Integer status;
}
