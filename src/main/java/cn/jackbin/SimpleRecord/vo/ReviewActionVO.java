package cn.jackbin.SimpleRecord.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("审核操作")
public class ReviewActionVO {
    @ApiModelProperty("备注/驳回原因")
    private String remark;
}
