package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@NoArgsConstructor
public class BatchAuditVO {

    @NotEmpty(message = "记录ID列表不能为空")
    private List<Long> recordIds;

    private String remark;
}
