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
 * 月结重算记录表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_monthly_closing_recalculation")
public class MonthlyClosingRecalculationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer bookId;

    private String yearMonth;

    private Integer recalcVersion;

    private Integer triggeredBy;

    private String triggerReason;

    private Long previousTotalIncome;

    private Long previousTotalExpend;

    private Long newTotalIncome;

    private Long newTotalExpend;

    private Long deltaIncome;

    private Long deltaExpend;

    /**
     * JSON stored as text
     */
    private String relatedAdjustmentIds;

    @TableField(value = "created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdTime;
}
