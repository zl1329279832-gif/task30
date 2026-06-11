package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@ApiModel("设置预算")
public class SetBudgetVO {
    @NotBlank(message = "年月不能为空")
    @ApiModelProperty("年月 yyyy-MM")
    private String yearMonth;

    @NotNull(message = "预算金额不能为空")
    @ApiModelProperty("预算金额")
    private BigDecimal budgetAmount;

    @ApiModelProperty("预警阈值百分比 (默认80)")
    private Integer warnThreshold;
}
