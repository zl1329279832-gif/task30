package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.bo.RecordDetailBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ReversalRequestMapper;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 冲正服务实现
 */
@Service
public class ReversalServiceImpl extends ServiceImpl<ReversalRequestMapper, ReversalRequestDO>
        implements ReversalService {

    @Autowired
    private ReversalRequestMapper reversalRequestMapper;

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private RecordDetailFactory recordDetailFactory;

    @Autowired
    private DictItemService dictItemService;

    @Autowired
    private SharedBookService sharedBookService;

    @Autowired
    @Lazy
    private MonthlyClosingService monthlyClosingService;

    @Autowired
    @Lazy
    private BudgetService budgetService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Override
    @Transactional
    public ReversalRequestDO requestReversal(Integer bookId, Integer requesterId, Long recordId, String reason) {
        RecordDetailDO record = recordDetailService.getById(recordId);
        if (record == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }

        // 仅创建者可申请
        if (!requesterId.equals(record.getUserId())) {
            throw new BusinessException(CodeMsg.REVERSAL_NOT_BY_CREATOR);
        }

        // 必须已入账
        if (record.getReviewStatus() != RecordConstant.REVIEW_POSTED) {
            throw new BusinessException(CodeMsg.REVERSAL_RECORD_NOT_POSTED);
        }

        // 月必须已结
        String yearMonth = new SimpleDateFormat("yyyy-MM").format(record.getOccurTime());
        if (!monthlyClosingService.isMonthClosed(bookId, yearMonth)) {
            throw new BusinessException(CodeMsg.REVERSAL_MONTH_NOT_CLOSED);
        }

        // 不能有重复待审核申请
        long pendingCount = count(new QueryWrapper<ReversalRequestDO>()
                .eq("original_record_id", recordId)
                .eq("review_status", RecordConstant.REVERSAL_PENDING));
        if (pendingCount > 0) {
            throw new BusinessException(CodeMsg.REVERSAL_ALREADY_PENDING);
        }

        ReversalRequestDO request = ReversalRequestDO.builder()
                .bookId(bookId)
                .originalRecordId(recordId)
                .requesterId(requesterId)
                .requestReason(reason)
                .reviewStatus(RecordConstant.REVERSAL_PENDING)
                .status(0)
                .build();
        save(request);

        auditLogService.log(bookId, requesterId, "REVERSAL_REQUEST", "RECORD", recordId,
                "{\"reason\":\"" + reason + "\"}");
        return request;
    }

    @Override
    @Transactional
    public void approveReversal(Integer bookId, Integer reviewerId, Long requestId, String remark) {
        sharedBookService.checkPermission(bookId, reviewerId, RecordConstant.PERM_REVIEW);

        String lockKey = RedisKey.LOCK_PREFIX + "reversal:" + requestId;
        if (!redisLockUtil.tryLock(lockKey, 30)) {
            throw new BusinessException(CodeMsg.OPERATION_IN_PROGRESS);
        }
        try {
            ReversalRequestDO req = getById(requestId);
            if (req == null || req.getReviewStatus() != RecordConstant.REVERSAL_PENDING) {
                throw new BusinessException(CodeMsg.RECORD_NOT_PENDING);
            }

            // 标记申请为已通过
            req.setReviewStatus(RecordConstant.REVERSAL_APPROVED);
            req.setReviewerId(reviewerId);
            req.setReviewTime(new Date());
            req.setReviewRemark(remark);
            updateById(req);

            // 标记原记录为已冲正
            RecordDetailDO original = recordDetailService.getById(req.getOriginalRecordId());
            if (original == null) {
                throw new BusinessException(CodeMsg.NOT_FIND_DATA);
            }
            original.setReviewStatus(RecordConstant.REVIEW_REVERSED);
            recordDetailService.updateById(original);

            // 创建反向条目
            DictItemDO dictItemDO = dictItemService.getById(original.getRecordType());
            RecordDetailHandler handler = recordDetailFactory.getHandler(dictItemDO.getValue());
            if (handler == null) {
                throw new BusinessException(CodeMsg.BUSINESS_ERROR);
            }

            RecordDetailBO reversalBO = new RecordDetailBO();
            reversalBO.setTargetAccountId(original.getRecordAccountId());
            reversalBO.setSourceAccountId(original.getSourceAccountId());
            reversalBO.setRecordBookId(original.getRecordBookId());
            reversalBO.setRecordTypeId(original.getRecordType());
            reversalBO.setRecordCategory(original.getRecordCategory());
            reversalBO.setAmount(-original.getAmount()); // 反向金额
            reversalBO.setOccurTime(new Date());
            reversalBO.setRemark("冲正: " + req.getRequestReason());

            int reversalId = handler.handleAdd(original.getUserId(), reversalBO);

            // 关联并自动入账
            RecordDetailDO counterEntry = recordDetailService.getById(reversalId);
            if (counterEntry != null) {
                counterEntry.setOriginalRecordId(original.getId());
                counterEntry.setReviewStatus(RecordConstant.REVIEW_POSTED);
                counterEntry.setReviewerId(reviewerId);
                counterEntry.setReviewTime(new Date());
                counterEntry.setReviewRemark("冲正自动入账");
                recordDetailService.updateById(counterEntry);

                req.setReversalRecordId(counterEntry.getId());
                updateById(req);
            }

            // 递减预算缓存
            if (original.getAmount() != null && original.getAmount() < 0) {
                String ym = new SimpleDateFormat("yyyy-MM").format(original.getOccurTime());
                BigDecimal expense = BigDecimal.valueOf(Math.abs(original.getAmount()));
                budgetService.atomicDecrementUsed(bookId, ym, expense);
            }

            auditLogService.log(bookId, reviewerId, "REVERSAL_APPROVE", "RECORD", original.getId(), null);
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public void rejectReversal(Integer bookId, Integer reviewerId, Long requestId, String reason) {
        sharedBookService.checkPermission(bookId, reviewerId, RecordConstant.PERM_REVIEW);

        ReversalRequestDO req = getById(requestId);
        if (req == null || req.getReviewStatus() != RecordConstant.REVERSAL_PENDING) {
            throw new BusinessException(CodeMsg.RECORD_NOT_PENDING);
        }

        req.setReviewStatus(RecordConstant.REVERSAL_REJECTED);
        req.setReviewerId(reviewerId);
        req.setReviewTime(new Date());
        req.setReviewRemark(reason);
        updateById(req);

        auditLogService.log(bookId, reviewerId, "REVERSAL_REJECT", "RECORD", req.getOriginalRecordId(),
                "{\"reason\":\"" + reason + "\"}");
    }

    @Override
    public void getReversalRequests(Integer bookId, Integer reviewStatus, PageBO<ReversalRequestDO> pageBO) {
        QueryWrapper<ReversalRequestDO> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId);
        if (reviewStatus != null) {
            wrapper.eq("review_status", reviewStatus);
        }
        wrapper.orderByDesc("create_time");

        IPage<ReversalRequestDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        IPage<ReversalRequestDO> result = page(page, wrapper);
        pageBO.setList(result.getRecords());
        pageBO.setTotal((int) result.getTotal());
    }
}
