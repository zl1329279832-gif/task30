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
@TableName("tb_month_lock")
public class MonthLockDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer recordBookId;

    /**
     * 锁定月份 yyyy-MM
     */
    private String yearMonth;

    /**
     * 锁定操作人ID
     */
    private Integer lockedBy;

    /**
     * 锁定时间
     */
    private Date lockTime;

    /**
     * 状态
     */
    private Integer status;
}
