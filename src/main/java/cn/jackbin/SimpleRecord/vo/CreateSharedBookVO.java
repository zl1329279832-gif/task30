package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("创建共享账本")
public class CreateSharedBookVO {
    @NotBlank(message = "账本名称不能为空")
    @ApiModelProperty("账本名称")
    private String name;

    @ApiModelProperty("备注")
    private String remark;
}
