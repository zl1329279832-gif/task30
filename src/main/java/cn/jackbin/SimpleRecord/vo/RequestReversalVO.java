package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("申请冲正")
public class RequestReversalVO {
    @NotNull(message = "记录ID不能为空")
    @ApiModelProperty("原始记录ID")
    private Long recordId;

    @NotBlank(message = "冲正原因不能为空")
    @ApiModelProperty("冲正原因")
    private String reason;

    @ApiModelProperty("账本ID")
    private Integer bookId;
}
