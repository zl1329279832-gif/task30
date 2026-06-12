package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 月结锁定表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_monthly_closing")
public class MonthlyClosingDO extends BaseDO implements Serializable {

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
     * 月结操作人
     */
    private Integer closedBy;

    /**
     * 月结时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date closedTime;

    /**
     * 收入快照
     */
    private BigDecimal totalIncome;

    /**
     * 支出快照
     */
    private BigDecimal totalExpend;

    /**
     * 备注
     */
    private String remark;

    private Integer status;

    /**
     * 快照版本号
     */
    private Integer snapshotVersion;

    /**
     * 结转是否已执行
     */
    private Integer carryforwardExecuted;

    /**
     * 结转总额(分)
     */
    private Long carryforwardTotal;

    /**
     * 超支总额(分)
     */
    private Long overspentTotal;

    /**
     * 待审核影响总额(分)
     */
    private Long pendingImpactTotal;

    /**
     * 最后重算时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastRecalcTime;
}
