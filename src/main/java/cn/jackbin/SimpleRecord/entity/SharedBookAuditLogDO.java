package cn.jackbin.SimpleRecord.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * 共享账本审计日志 (append-only, 不继承BaseDO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_shared_book_audit_log")
public class SharedBookAuditLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账本ID
     */
    private Integer bookId;

    /**
     * 操作人ID
     */
    private Integer operatorId;

    /**
     * 操作类型
     */
    private String actionType;

    /**
     * 目标类型: MEMBER, RECORD, BUDGET, CLOSING
     */
    private String targetType;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 详情JSON
     */
    private String detail;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
