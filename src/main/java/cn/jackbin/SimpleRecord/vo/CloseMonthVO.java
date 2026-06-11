package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("月结操作")
public class CloseMonthVO {
    @NotBlank(message = "年月不能为空")
    @ApiModelProperty("年月 yyyy-MM")
    private String yearMonth;

    @ApiModelProperty("备注")
    private String remark;
}
