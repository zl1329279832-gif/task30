package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.dto.RecordDetailDTO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;

/**
 * 记账审核服务
 */
public interface RecordReviewService {

    /**
     * 提交记录进入待审核
     */
    void submitForReview(RecordDetailDO record);

    /**
     * 审核通过
     */
    void approveRecord(Integer bookId, Integer reviewerId, Long recordId, String remark);

    /**
     * 审核驳回
     */
    void rejectRecord(Integer bookId, Integer reviewerId, Long recordId, String reason);

    /**
     * 获取待审核记录列表
     */
    void getPendingReviews(Integer bookId, Integer userId, PageBO<RecordDetailDTO> pageBO);
}
