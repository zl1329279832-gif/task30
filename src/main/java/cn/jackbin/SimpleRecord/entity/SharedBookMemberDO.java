package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;

/**
 * 共享账本成员表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_shared_book_member")
public class SharedBookMemberDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer bookId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 权限列表，逗号分隔: entry,review,view,settlement
     */
    private String permissions;

    /**
     * 在该账本中的显示名称
     */
    private String nickname;

    /**
     * 状态: 0=正常, 1=已移除
     */
    private Integer status;
}
