package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.dto.RecordDetailDTO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.DateUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记账审核服务实现
 */
@Service
public class RecordReviewServiceImpl implements RecordReviewService {

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private SharedBookService sharedBookService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    @Lazy
    private BudgetService budgetService;

    @Autowired
    @Lazy
    private MonthlyClosingService monthlyClosingService;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void submitForReview(RecordDetailDO record) {
        record.setReviewStatus(RecordConstant.REVIEW_PENDING);
        recordDetailService.updateById(record);
        auditLogService.log(record.getRecordBookId(), record.getUserId(),
                "RECORD_SUBMIT", "RECORD", record.getId(), null);
    }

    @Override
    @Transactional
    public void approveRecord(Integer bookId, Integer reviewerId, Long recordId, String remark) {
        sharedBookService.checkPermission(bookId, reviewerId, RecordConstant.PERM_REVIEW);

        // 先读取记录，检查月结锁定: 月结后禁止审核入账，须走冲正流程
        RecordDetailDO record = recordDetailService.getById(recordId);
        if (record == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
        String yearMonth = formatYearMonth(record.getOccurTime());
        if (monthlyClosingService.isMonthClosed(bookId, yearMonth)) {
            throw new BusinessException(CodeMsg.MONTH_CLOSED_CANNOT_APPROVE);
        }

        // 原子更新状态: 仅当 review_status=1(待审核) 时才更新
        boolean updated = updateReviewStatus(recordId, RecordConstant.REVIEW_PENDING, RecordConstant.REVIEW_POSTED,
                reviewerId, remark);
        if (!updated) {
            throw new BusinessException(CodeMsg.RECORD_NOT_PENDING);
        }

        // 预算原子递增 (仅支出)
        if (record.getAmount() != null && record.getAmount() < 0) {
            BigDecimal expense = BigDecimal.valueOf(Math.abs(record.getAmount()));
            budgetService.atomicIncrementUsed(bookId, yearMonth, expense);
        }

        auditLogService.log(bookId, reviewerId, "RECORD_REVIEW", "RECORD", recordId,
                "{\"action\":\"approve\"}");
    }

    @Override
    @Transactional
    public void rejectRecord(Integer bookId, Integer reviewerId, Long recordId, String reason) {
        sharedBookService.checkPermission(bookId, reviewerId, RecordConstant.PERM_REVIEW);

        boolean updated = updateReviewStatus(recordId, RecordConstant.REVIEW_PENDING, RecordConstant.REVIEW_REJECTED,
                reviewerId, reason);
        if (!updated) {
            throw new BusinessException(CodeMsg.RECORD_NOT_PENDING);
        }

        auditLogService.log(bookId, reviewerId, "RECORD_REJECT", "RECORD", recordId,
                "{\"reason\":\"" + reason + "\"}");
    }

    @Override
    public void getPendingReviews(Integer bookId, Integer userId, PageBO<RecordDetailDTO> pageBO) {
        sharedBookService.checkPermission(bookId, userId, RecordConstant.PERM_REVIEW);

        QueryWrapper<RecordDetailDO> wrapper = new QueryWrapper<>();
        wrapper.eq("record_book_id", bookId)
                .eq("review_status", RecordConstant.REVIEW_PENDING)
                .orderByDesc("create_time");

        IPage<RecordDetailDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        IPage<RecordDetailDO> result = recordDetailService.page(page, wrapper);

        List<RecordDetailDTO> dtos = result.getRecords().stream().map(r -> {
            RecordDetailDTO dto = new RecordDetailDTO();
            BeanUtils.copyProperties(r, dto);
            return dto;
        }).collect(Collectors.toList());

        pageBO.setList(dtos);
        pageBO.setTotal((int) result.getTotal());
    }

    /**
     * 原子更新审核状态
     */
    private boolean updateReviewStatus(Long recordId, int expectedFrom, int toStatus,
                                       Integer reviewerId, String remark) {
        UpdateWrapper<RecordDetailDO> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", recordId)
                .eq("review_status", expectedFrom)
                .set("review_status", toStatus)
                .set("reviewer_id", reviewerId)
                .set("review_time", new Date())
                .set("review_remark", remark);
        return recordDetailService.update(wrapper);
    }

    private String formatYearMonth(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM");
        return sdf.format(date);
    }
}
