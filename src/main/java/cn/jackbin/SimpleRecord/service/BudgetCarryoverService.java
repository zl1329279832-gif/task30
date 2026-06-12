package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.BudgetCarryoverDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryoverRuleDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算结转服务
 */
public interface BudgetCarryoverService {

    /**
     * 设置结转规则 (新建版本, 旧版本自动置为已替代)
     */
    BudgetCarryoverRuleDO setCarryoverRule(Integer bookId, Integer userId,
                                            String carryoverType, Integer percent, BigDecimal cap,
                                            Boolean carryOverspend, String pendingRecordPolicy,
                                            String effectiveFrom);

    /**
     * 获取某月适用的生效规则 (最高版本且 effectiveFrom <= yearMonth)
     */
    BudgetCarryoverRuleDO getActiveRule(Integer bookId, String yearMonth);

    /**
     * 列出账本所有规则版本
     */
    List<BudgetCarryoverRuleDO> listRuleVersions(Integer bookId);

    /**
     * 月结时计算并执行预算结转
     */
    BudgetCarryoverDO computeAndApplyCarryover(Integer bookId, String yearMonth,
                                                Long closingId, BigDecimal budgetAmount,
                                                BigDecimal usedAmount);

    /**
     * 获取某月的结转记录
     */
    BudgetCarryoverDO getCarryover(Integer bookId, String yearMonth);

    /**
     * 获取某月有效预算 (原始预算 + 上月结转入)
     */
    BigDecimal getEffectiveBudget(Integer bookId, String yearMonth);
}
