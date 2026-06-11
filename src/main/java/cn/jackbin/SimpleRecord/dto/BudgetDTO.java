package cn.jackbin.SimpleRecord.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算DTO
 */
@Data
public class BudgetDTO {
    private String yearMonth;
    private BigDecimal budgetAmount;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private boolean isOverBudget;
    private Integer warnThreshold;
    private boolean isWarning;
}
