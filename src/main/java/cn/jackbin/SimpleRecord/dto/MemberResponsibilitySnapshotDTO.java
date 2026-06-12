package cn.jackbin.SimpleRecord.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 成员责任快照DTO (原始快照 + 调整合并后)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponsibilitySnapshotDTO {

    private Integer userId;
    private String userNickname;
    private String userPermissions;
    private String recordCategory;
    private Integer recordAccountId;

    /** 原始收入 */
    private BigDecimal originalIncome;
    /** 原始支出 */
    private BigDecimal originalExpend;
    /** 原始记录数 */
    private Integer originalRecordCount;

    /** 调整后收入 (原始 + 调整量) */
    private BigDecimal adjustedIncome;
    /** 调整后支出 (原始 + 调整量) */
    private BigDecimal adjustedExpend;

    /** 收入调整量 */
    private BigDecimal adjustmentIncome;
    /** 支出调整量 */
    private BigDecimal adjustmentExpend;
}
