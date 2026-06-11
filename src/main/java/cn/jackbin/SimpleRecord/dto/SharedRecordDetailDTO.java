package cn.jackbin.SimpleRecord.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SharedRecordDetailDTO extends RecordDetailDTO {

    private static final long serialVersionUID = 1L;

    private String recorderName;

    private String payerName;

    private String auditStatusName;
}
