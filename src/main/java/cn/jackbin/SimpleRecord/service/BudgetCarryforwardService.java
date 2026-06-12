package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardLogDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardRuleDO;

import java.util.List;

/**
 * 预算结转服务
 */
public interface BudgetCarryforwardService {

    /**
     * 月结时执行预算结转
     * @return 结转结果摘要 (carryforwardTotal, overspentTotal, pendingImpactTotal, logCount)
     */
    long[] executeCarryforward(Integer bookId, String yearMonth, Integer operatorUserId);

    /**
     * 回滚结转
     */
    void rollbackCarryforward(Integer bookId, String yearMonth, Integer operatorUserId);

    /**
     * 创建/更新结转规则 (自动 version++)
     */
    BudgetCarryforwardRuleDO saveRule(Integer bookId, String yearMonth, String ruleType,
                                       java.math.BigDecimal carryforwardRate, Long maxCarryforwardAmount,
                                       Integer expireMonths, Integer includePending,
                                       String overspentMode, String categoryFilter,
                                       Integer operatorUserId);

    /**
     * 查询活跃规则
     */
    BudgetCarryforwardRuleDO getActiveRule(Integer bookId, String yearMonth);

    /**
     * 查询规则版本历史
     */
    List<BudgetCarryforwardRuleDO> getRuleHistory(Integer bookId, String yearMonth);

    /**
     * 查询结转日志
     */
    List<BudgetCarryforwardLogDO> getCarryforwardLogs(Integer bookId, String sourceYearMonth);
}
