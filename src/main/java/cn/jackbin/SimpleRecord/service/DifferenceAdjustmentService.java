package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.DifferenceAdjustmentRecordDO;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingRecalculationDO;

import java.util.List;

/**
 * 差额调整服务
 */
public interface DifferenceAdjustmentService {

    /**
     * 创建差额调整记录 (幂等)
     */
    DifferenceAdjustmentRecordDO createAdjustment(Integer bookId, String sourceYearMonth,
                                                    String targetYearMonth, Long originalRecordId,
                                                    Long reversalRequestId, String adjustmentType,
                                                    Long adjustmentAmount, Long categoryId,
                                                    Long accountId, Integer memberUserId,
                                                    String reason, Long relatedClosingId,
                                                    String idempotencyKey, Integer operatorUserId);

    /**
     * 将调整计入当期预算
     */
    void applyAdjustment(Long adjustmentId);

    /**
     * 触发月结快照重算
     */
    MonthlyClosingRecalculationDO triggerRecalculation(Integer bookId, String closedYearMonth,
                                                        Integer operatorUserId, String reason);

    /**
     * 查询调整记录
     */
    List<DifferenceAdjustmentRecordDO> getBySourcePeriod(Integer bookId, String sourceYearMonth);

    /**
     * 查询调整记录
     */
    List<DifferenceAdjustmentRecordDO> getByTargetPeriod(Integer bookId, String targetYearMonth);

    /**
     * 根据ID获取
     */
    DifferenceAdjustmentRecordDO getById(Long adjustmentId);

    /**
     * 查询重算历史
     */
    List<MonthlyClosingRecalculationDO> getRecalcHistory(Integer bookId, String yearMonth);
}
