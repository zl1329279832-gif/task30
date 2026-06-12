package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 月结调整单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_closing_adjustment")
public class ClosingAdjustmentDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 账本ID */
    private Integer bookId;

    /** 受影响的已结月份 yyyy-MM */
    private String yearMonth;

    /** 关联月结记录ID */
    private Long closingId;

    /** 调整类型: REVERSAL / SUPPLEMENTARY_AUDIT */
    private String adjustmentType;

    /** 触发调整的记录ID */
    private Long sourceRecordId;

    /** 冲正反向条目ID */
    private Long counterRecordId;

    /** 责任归属成员ID */
    private Integer userId;

    /** 分类 */
    private String recordCategory;

    /** 账户ID */
    private Integer recordAccountId;

    /** 收入调整量 */
    private BigDecimal adjustmentIncome;

    /** 支出调整量 */
    private BigDecimal adjustmentExpend;

    /** 预算影响量 */
    private BigDecimal budgetImpact;

    /** 幂等键 */
    private String idempotencyKey;

    /** 操作人ID */
    private Integer operatorId;

    /** 备注 */
    private String remark;

    private Integer status;
}
