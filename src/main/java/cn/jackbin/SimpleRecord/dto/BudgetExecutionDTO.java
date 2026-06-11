package cn.jackbin.SimpleRecord.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BudgetExecutionDTO {

    private Long budgetId;

    private Integer budgetType;

    private String categoryName;

    private Integer memberUserId;

    private String memberName;

    private String yearMonth;

    private Double budgetAmount;

    private Double spentAmount;

    private Double remainingAmount;

    private Integer usedPercent;

    private Integer warnPercent;

    private Boolean overLimit;

    public Double getRemainingAmount() {
        if (budgetAmount != null && spentAmount != null) {
            return budgetAmount - spentAmount;
        }
        return budgetAmount;
    }

    public Integer getUsedPercent() {
        if (budgetAmount != null && budgetAmount > 0 && spentAmount != null) {
            return (int) (spentAmount / budgetAmount * 100);
        }
        return 0;
    }

    public Boolean getOverLimit() {
        if (warnPercent != null) {
            return getUsedPercent() >= warnPercent;
        }
        return false;
    }
}
