package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
public class AddSharedBookVO {

    @NotBlank(message = "账本名称不能为空")
    private String name;

    private String remark;

    private Integer orderNo;
}
