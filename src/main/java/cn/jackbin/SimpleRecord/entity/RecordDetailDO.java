package cn.jackbin.SimpleRecord.entity;

import cn.jackbin.SimpleRecord.common.anotations.DictValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: create by bin
 * @version: v1.0
 * @description: 记账详情表
 * @date: 2020/9/16 22:19
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("tb_record_detail")
public class RecordDetailDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 2546476382250156336L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记账人
     */
    private Integer userId;

    /**
     * 账户Id
     */
    private Integer recordAccountId;

    /**
     * 账本Id
     */
    private Integer recordBookId;

    /**
     * 记账类型
     */
    @DictValue(code = "recordType")
    private Integer recordType;

    /**
     * 记账类别
     */
    private String recordCategory;

    /**
     * 流入账户
     */
    private Integer sourceAccountId;

    /**
     * 流出账户
     */
    private Integer targetAccountId;

    /**
     * 关联记录Id
     */
    private Integer relationRecordId;

    /**
     * 金额
     */
    private Double amount;

    @JsonFormat(pattern ="yyyy-MM-dd",timezone ="GMT+8")
    private Date occurTime;

    private String tag;

    /**
     * 备注
     */
    private String remark;

    private Integer status;

    /**
     * 报销状态
     */
    private Integer recoverableStatus;

    /**
     * 审核状态: 0=无需审核(个人), 1=待审核, 2=已入账, 3=已驳回, 4=已冲正
     */
    private Integer reviewStatus;

    /**
     * 审核人ID
     */
    private Integer reviewerId;

    /**
     * 审核时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date reviewTime;

    /**
     * 审核备注/驳回原因
     */
    private String reviewRemark;

    /**
     * 冲正条目指向原记录ID
     */
    private Long originalRecordId;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 调整记录类型: NULL=normal/CARRYFORWARD_ADJUST/DIFF_ADJUST/REVERSAL_DIFF
     */
    private String adjustmentRecordType;

    /**
     * 跨期来源期间
     */
    private String sourceYearMonth;

    /**
     * 幂等键
     */
    private String idempotencyKey;
}
