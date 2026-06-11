package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class EditBudgetVO {

    @NotNull(message = "预算金额不能为空")
    private Double budgetAmount;

    private Integer warnPercent;
}
