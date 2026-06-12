package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.MemberSettlementSnapshotDO;

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

    /**
     * 月结时捕获所有成员责任快照
     */
    void captureSnapshots(Integer bookId, String yearMonth);

    /**
     * 查询历史责任 (成员被移除后仍可查)
     */
    MemberSettlementSnapshotDO getHistoricalResponsibility(Integer bookId, String yearMonth, Integer memberUserId);

    /**
     * 成员历史时间线 (跨期)
     */
    List<MemberSettlementSnapshotDO> getMemberResponsibilityTimeline(Integer bookId, Integer memberUserId);

    /**
     * 重算成员快照
     */
    void recalculateSnapshot(Integer bookId, String yearMonth, Integer memberUserId, String reason);

    /**
     * 查询某期所有成员快照
     */
    List<MemberSettlementSnapshotDO> getPeriodSnapshots(Integer bookId, String yearMonth);
}
