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
@TableName("tb_shared_book_log")
public class SharedBookLogDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 共享账本ID
     */
    private Integer recordBookId;

    /**
     * 操作人ID
     */
    private Integer operUserId;

    /**
     * 操作类型：INVITE/JOIN/RECORD/AUDIT/REJECT/LOCK/REVERSAL_APPLY/REVERSAL_APPROVE/SETTLE
     */
    private String operType;

    /**
     * 目标对象ID
     */
    private Long targetId;

    /**
     * 操作描述
     */
    private String content;

    /**
     * 扩展JSON数据
     */
    private String extraData;

    /**
     * 状态
     */
    private Integer status;
}
