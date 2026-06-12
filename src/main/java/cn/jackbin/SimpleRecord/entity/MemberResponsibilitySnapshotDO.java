package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 成员责任快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_member_responsibility_snapshot")
public class MemberResponsibilitySnapshotDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 账本ID */
    private Integer bookId;

    /** 年月 yyyy-MM */
    private String yearMonth;

    /** 关联月结记录ID */
    private Long closingId;

    /** 成员ID(月结时) */
    private Integer userId;

    /** 成员昵称快照 */
    private String userNickname;

    /** 成员权限快照 */
    private String userPermissions;

    /** 分类维度 */
    private String recordCategory;

    /** 账户维度 */
    private Integer recordAccountId;

    /** 收入汇总 */
    private BigDecimal totalIncome;

    /** 支出汇总 */
    private BigDecimal totalExpend;

    /** 记录数 */
    private Integer recordCount;

    private Integer status;
}
