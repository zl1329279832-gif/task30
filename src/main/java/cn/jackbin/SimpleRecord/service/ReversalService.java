package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.ReversalRequestDO;

/**
 * 冲正服务
 */
public interface ReversalService {

    /**
     * 申请冲正
     */
    ReversalRequestDO requestReversal(Integer bookId, Integer requesterId, Long recordId, String reason);

    /**
     * 审批通过冲正
     */
    void approveReversal(Integer bookId, Integer reviewerId, Long requestId, String remark);

    /**
     * 驳回冲正
     */
    void rejectReversal(Integer bookId, Integer reviewerId, Long requestId, String reason);

    /**
     * 分页获取冲正申请
     */
    void getReversalRequests(Integer bookId, Integer reviewStatus, PageBO<ReversalRequestDO> pageBO);
}
