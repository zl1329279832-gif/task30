package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算结转记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_budget_carryover")
public class BudgetCarryoverDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 账本ID */
    private Integer bookId;

    /** 结转来源月份 yyyy-MM */
    private String sourceYearMonth;

    /** 结转目标月份 yyyy-MM */
    private String targetYearMonth;

    /** 使用的规则ID */
    private Long ruleId;

    /** 来源月原始预算 */
    private BigDecimal budgetAmount;

    /** 来源月已使用金额 */
    private BigDecimal usedAmount;

    /** 待审核预留金额 */
    private BigDecimal pendingReserve;

    /** 原始结余 = 预算 - 已用 - 预留 */
    private BigDecimal rawCarryover;

    /** 应用规则后的结转金额 */
    private BigDecimal appliedCarryover;

    /** 超支结转金额 */
    private BigDecimal overspendCarryover;

    /** 关联月结记录ID */
    private Long closingId;

    private Integer status;
}
