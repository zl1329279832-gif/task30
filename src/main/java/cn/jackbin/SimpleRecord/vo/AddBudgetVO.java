package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class AddBudgetVO {

    @NotNull(message = "账本ID不能为空")
    private Integer recordBookId;

    @NotNull(message = "预算类型不能为空")
    private Integer budgetType;

    private String categoryName;

    private Integer memberUserId;

    @NotNull(message = "预算月份不能为空")
    private String yearMonth;

    @NotNull(message = "预算金额不能为空")
    private Double budgetAmount;

    private Integer warnPercent;
}
