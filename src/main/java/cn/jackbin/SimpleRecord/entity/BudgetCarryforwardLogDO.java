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
 * 预算结转日志表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_budget_carryforward_log")
public class BudgetCarryforwardLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer bookId;

    private String sourceYearMonth;

    private String targetYearMonth;

    private Long sourceBudgetId;

    private Long categoryId;

    private Long accountId;

    private Integer memberUserId;

    private Long originalAmount;

    private Long usedAmount;

    private Long carryforwardAmount;

    private Long pendingImpactAmount;

    private Integer ruleVersion;

    private String ruleType;

    private Integer status;

    @TableField(value = "created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdTime;
}
