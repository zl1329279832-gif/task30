package cn.jackbin.SimpleRecord.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成员结算服务
 */
public interface MemberSettlementService {

    /**
     * 计算成员净余额 (正=应收, 负=应付)
     */
    Map<Integer, BigDecimal> calculateNetBalances(Integer bookId, String yearMonth);

    /**
     * 执行结算, 返回生成的转账记录ID列表
     */
    List<Long> executeSettlement(Integer bookId, Integer operatorId, String yearMonth);
}
