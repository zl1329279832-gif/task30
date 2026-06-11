package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 账本月度预算表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_book_budget")
public class BookBudgetDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer bookId;

    /**
     * 年月 yyyy-MM
     */
    private String yearMonth;

    /**
     * 预算金额
     */
    private BigDecimal budgetAmount;

    /**
     * 已使用金额
     */
    private BigDecimal usedAmount;

    /**
     * 预警阈值百分比
     */
    private Integer warnThreshold;

    private Integer status;
}
