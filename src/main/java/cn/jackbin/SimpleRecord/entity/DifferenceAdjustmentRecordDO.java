package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * 差额调整记录表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_difference_adjustment_record")
public class DifferenceAdjustmentRecordDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer bookId;

    private String sourceYearMonth;

    private String targetYearMonth;

    private Long originalRecordId;

    private Long reversalRequestId;

    private String adjustmentType;

    private Long adjustmentAmount;

    private Long categoryId;

    private Long accountId;

    private Integer memberUserId;

    private String reason;

    private Long relatedClosingId;

    private Integer reviewStatus;

    private Long adjustmentRecordId;

    private String idempotencyKey;

    private Integer createdBy;

    @TableField(value = "created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdTime;

    @TableField(value = "updated_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedTime;
}
