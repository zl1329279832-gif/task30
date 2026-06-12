package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月结调整单服务
 */
public interface ClosingAdjustmentService {

    /**
     * 幂等创建调整单
     */
    ClosingAdjustmentDO createAdjustmentIdempotent(String idempotencyKey, Integer bookId,
                                                    String yearMonth, String adjustmentType,
                                                    Long sourceRecordId, Long counterRecordId,
                                                    Integer userId, String category,
                                                    Integer accountId, BigDecimal adjustmentIncome,
                                                    BigDecimal adjustmentExpend, BigDecimal budgetImpact,
                                                    Integer operatorId, String remark);

    /**
     * 查询某月所有调整单
     */
    List<ClosingAdjustmentDO> getAdjustments(Integer bookId, String yearMonth);

    /**
     * 分页查询调整单
     */
    void getAdjustmentsByPage(Integer bookId, String yearMonth, PageBO<ClosingAdjustmentDO> pageBO);

    /**
     * 查询某月调整单的预算影响净额
     */
    BigDecimal getNetBudgetImpact(Integer bookId, String yearMonth);
}
