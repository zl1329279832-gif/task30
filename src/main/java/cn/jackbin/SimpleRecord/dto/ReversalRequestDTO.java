package cn.jackbin.SimpleRecord.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 冲正申请DTO
 */
@Data
public class ReversalRequestDTO {
    private Long requestId;
    private Long originalRecordId;
    private String recordCategory;
    private Double recordAmount;
    private Integer requesterId;
    private String requestReason;
    private Integer reviewStatus;
    private String reviewStatusText;
    private Integer reviewerId;
    private String reviewRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date reviewTime;
    private Long reversalRecordId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
