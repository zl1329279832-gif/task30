package cn.jackbin.SimpleRecord.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BudgetWarnDTO {

    private String label;

    private Double budgetAmount;

    private Double spentAmount;

    private Integer usedPercent;
}
