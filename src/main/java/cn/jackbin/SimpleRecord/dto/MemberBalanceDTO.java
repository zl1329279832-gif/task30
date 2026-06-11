package cn.jackbin.SimpleRecord.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成员余额DTO
 */
@Data
public class MemberBalanceDTO {
    private Integer userId;
    private String nickname;
    private BigDecimal netAmount;
    private BigDecimal totalPaid;
    private BigDecimal totalOwed;
}
