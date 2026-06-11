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
@TableName("tb_shared_book_member")
public class SharedBookMemberDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 共享账本ID
     */
    private Integer recordBookId;

    /**
     * 成员用户ID
     */
    private Integer userId;

    /**
     * 权限：record,audit,view,settle 逗号分隔
     */
    private String permission;

    /**
     * 在该账本中的昵称
     */
    private String nickname;

    /**
     * 加入时间
     */
    private Date joinTime;

    /**
     * 状态：0=正常, 1=已退出
     */
    private Integer status;
}
