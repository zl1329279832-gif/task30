package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("添加账本成员")
public class AddMemberVO {
    @NotBlank(message = "用户ID或用户名不能为空")
    @ApiModelProperty("用户ID或用户名")
    private String userIdOrUsername;

    @NotBlank(message = "权限不能为空")
    @ApiModelProperty("权限: entry,review,view,settlement (逗号分隔)")
    private String permissions;
}
