package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * 冲正申请表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_reversal_request")
public class ReversalRequestDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer bookId;

    /**
     * 原始记录ID
     */
    private Long originalRecordId;

    /**
     * 申请人ID (必须是原始记录创建者)
     */
    private Integer requesterId;

    /**
     * 审核人ID
     */
    private Integer reviewerId;

    /**
     * 申请原因
     */
    private String requestReason;

    /**
     * 审核状态: 1=待审核, 2=已通过, 3=已驳回
     */
    private Integer reviewStatus;

    /**
     * 审核备注
     */
    private String reviewRemark;

    /**
     * 审核时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date reviewTime;

    /**
     * 冲正记录ID
     */
    private Long reversalRecordId;

    private Integer status;
}
