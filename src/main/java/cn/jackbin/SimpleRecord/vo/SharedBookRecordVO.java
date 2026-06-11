package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SharedBookRecordVO extends RecordDetailVO {

    private Integer payerUserId;
}
