package cn.jackbin.SimpleRecord.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberSpendSummaryDTO {

    private Integer userId;

    private String nickname;

    private Double totalSpend;

    private Double advancedAmount;
}
