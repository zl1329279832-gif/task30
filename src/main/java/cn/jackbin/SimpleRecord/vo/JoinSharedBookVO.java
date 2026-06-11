package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("加入共享账本")
public class JoinSharedBookVO {
    @NotBlank(message = "邀请码不能为空")
    @ApiModelProperty("邀请码")
    private String inviteCode;
}
