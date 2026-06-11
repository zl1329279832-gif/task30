package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("更新成员权限")
public class UpdatePermissionVO {
    @NotBlank(message = "权限不能为空")
    @ApiModelProperty("权限: entry,review,view,settlement (逗号分隔)")
    private String permissions;
}
