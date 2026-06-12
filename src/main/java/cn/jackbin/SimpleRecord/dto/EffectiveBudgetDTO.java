package cn.jackbin.SimpleRecord.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 有效预算DTO (原始预算 + 结转明细)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveBudgetDTO {

    /** 账本ID */
    private Integer bookId;

    /** 年月 */
    private String yearMonth;

    /** 原始预算金额 */
    private BigDecimal originalBudget;

    /** 上月结转入金额 (正数=结余结转, 负数=超支结转) */
    private BigDecimal carryoverAmount;

    /** 有效预算 = 原始 + 结转 */
    private BigDecimal effectiveBudget;

    /** 已使用金额 */
    private BigDecimal usedAmount;

    /** 剩余可用 */
    private BigDecimal remainingBudget;
}
