package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_budget")
public class BudgetDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer recordBookId;

    /**
     * 预算类型：1=总预算, 2=分类预算, 3=成员预算
     */
    private Integer budgetType;

    /**
     * 预算类别名称（budgetType=2时使用）
     */
    private String categoryName;

    /**
     * 成员用户ID（budgetType=3时使用）
     */
    private Integer memberUserId;

    /**
     * 预算月份 yyyy-MM
     */
    private String yearMonth;

    /**
     * 预算金额
     */
    private Double budgetAmount;

    /**
     * 预警百分比阈值
     */
    private Integer warnPercent;

    /**
     * 状态
     */
    private Integer status;
}
