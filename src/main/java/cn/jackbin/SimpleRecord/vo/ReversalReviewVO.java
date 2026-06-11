package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("冲正审核操作")
public class ReversalReviewVO {
    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("账本ID")
    private Integer bookId;
}
