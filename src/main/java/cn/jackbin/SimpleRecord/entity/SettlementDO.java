package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_settlement")
public class SettlementDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer recordBookId;

    /**
     * 结算月份 yyyy-MM
     */
    private String yearMonth;

    /**
     * 应付款人ID
     */
    private Integer fromUserId;

    /**
     * 应收款人ID
     */
    private Integer toUserId;

    /**
     * 结算金额
     */
    private Double amount;

    /**
     * 结算状态：1=待结算, 2=已结算
     */
    private Integer settleStatus;

    /**
     * 确认结算操作人
     */
    private Integer settledBy;

    /**
     * 结算确认时间
     */
    private Date settleTime;

    /**
     * 状态
     */
    private Integer status;
}
