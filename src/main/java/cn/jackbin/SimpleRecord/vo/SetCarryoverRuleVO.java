package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@ApiModel("设置结转规则")
public class SetCarryoverRuleVO {

    @NotBlank(message = "结转类型不能为空")
    @ApiModelProperty("结转类型: FULL/PERCENTAGE/CAPPED")
    private String carryoverType;

    @ApiModelProperty("结转百分比 (0-100, PERCENTAGE类型必填)")
    private Integer carryoverPercent;

    @ApiModelProperty("结转上限金额 (CAPPED类型必填)")
    private BigDecimal capAmount;

    @NotNull(message = "超支结转标识不能为空")
    @ApiModelProperty("是否结转超支 (true=结转, false=不结转)")
    private Boolean carryOverspend;

    @NotBlank(message = "待审核记录策略不能为空")
    @ApiModelProperty("待审核记录策略: IGNORE/RESERVE")
    private String pendingRecordPolicy;

    @NotBlank(message = "生效月份不能为空")
    @ApiModelProperty("生效起始月份 yyyy-MM")
    private String effectiveFrom;
}
