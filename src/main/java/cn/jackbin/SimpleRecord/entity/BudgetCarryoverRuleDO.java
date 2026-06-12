package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算结转规则(版本化)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_budget_carryover_rule")
public class BudgetCarryoverRuleDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 账本ID */
    private Integer bookId;

    /** 规则版本号 */
    private Integer version;

    /** 结转类型: FULL / PERCENTAGE / CAPPED */
    private String carryoverType;

    /** 结转百分比 (0-100) */
    private Integer carryoverPercent;

    /** 结转上限金额 */
    private BigDecimal capAmount;

    /** 是否结转超支 (1=是, 0=否) */
    private Integer carryOverspend;

    /** 待审核记录策略: IGNORE / RESERVE */
    private String pendingRecordPolicy;

    /** 生效起始月份 yyyy-MM */
    private String effectiveFrom;

    /** 创建人 */
    private Integer createdBy;

    private Integer status;
}
