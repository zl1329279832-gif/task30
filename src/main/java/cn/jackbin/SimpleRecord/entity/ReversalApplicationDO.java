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
@TableName("tb_reversal_application")
public class ReversalApplicationDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer recordBookId;

    /**
     * 原始记录ID
     */
    private Long originalRecordId;

    /**
     * 冲正记录ID（审批通过后生成）
     */
    private Long reversalRecordId;

    /**
     * 申请人ID
     */
    private Integer applicantUserId;

    /**
     * 冲正原因
     */
    private String reason;

    /**
     * 审核状态：1=待审核, 2=已通过, 3=已驳回
     */
    private Integer auditStatus;

    /**
     * 审核人ID
     */
    private Integer auditorId;

    /**
     * 审核时间
     */
    private Date auditTime;

    /**
     * 审核备注
     */
    private String auditRemark;

    /**
     * 状态
     */
    private Integer status;
}
