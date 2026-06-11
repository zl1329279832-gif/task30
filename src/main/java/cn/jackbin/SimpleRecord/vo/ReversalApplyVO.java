package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class ReversalApplyVO {

    @NotNull(message = "原始记录ID不能为空")
    private Long originalRecordId;

    @NotBlank(message = "冲正原因不能为空")
    private String reason;
}
