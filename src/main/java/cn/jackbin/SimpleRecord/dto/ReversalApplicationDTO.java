package cn.jackbin.SimpleRecord.dto;

import cn.jackbin.SimpleRecord.entity.ReversalApplicationDO;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReversalApplicationDTO extends ReversalApplicationDO {

    private static final long serialVersionUID = 1L;

    private String applicantName;

    private String auditorName;

    private Double originalAmount;

    private String originalCategory;
}
