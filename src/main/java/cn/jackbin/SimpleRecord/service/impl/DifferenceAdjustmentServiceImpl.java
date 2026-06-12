package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.*;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 差额调整服务实现
 */
@Service
public class DifferenceAdjustmentServiceImpl implements DifferenceAdjustmentService {

    @Autowired
    private DifferenceAdjustmentRecordMapper adjustmentRecordMapper;

    @Autowired
    private MonthlyClosingRecalculationMapper recalculationMapper;

    @Autowired
    private MonthlyClosingMapper monthlyClosingMapper;

    @Autowired
    private BookBudgetMapper bookBudgetMapper;

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    @Lazy
    private MonthlyClosingService monthlyClosingService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public DifferenceAdjustmentRecordDO createAdjustment(Integer bookId, String sourceYearMonth,
                                                           String targetYearMonth, Long originalRecordId,
                                                           Long reversalRequestId, String adjustmentType,
                                                           Long adjustmentAmount, Long categoryId,
                                                           Long accountId, Integer memberUserId,
                                                           String reason, Long relatedClosingId,
                                                           String idempotencyKey, Integer operatorUserId) {
        // 金额不能为零
        if (adjustmentAmount == null || adjustmentAmount == 0) {
            throw new BusinessException(CodeMsg.ADJUSTMENT_AMOUNT_ZERO);
        }

        // 幂等检查 Layer 1: Redis
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempotencyKey;
        Object cached = redisUtil.get(redisIdempKey);
        if (cached != null) {
            Long existingId = ((Number) cached).longValue();
            DifferenceAdjustmentRecordDO existing = adjustmentRecordMapper.selectById(existingId);
            if (existing != null) {
                return existing;
            }
        }

        // 获取锁
        String lockKey = RedisKey.LOCK_PREFIX + "adjustment:" + bookId + ":" + sourceYearMonth;
        if (!redisLockUtil.tryLock(lockKey, 30)) {
            throw new BusinessException(CodeMsg.ADJUSTMENT_LOCK_FAILED);
        }

        try {
            // 幂等检查 Layer 2: DB unique key
            DifferenceAdjustmentRecordDO dbExisting = adjustmentRecordMapper.selectByIdempotencyKey(idempotencyKey);
            if (dbExisting != null) {
                redisUtil.set(redisIdempKey, dbExisting.getId(), 86400L);
                return dbExisting;
            }

            // 源期间必须已关闭
            if (!monthlyClosingService.isMonthClosed(bookId, sourceYearMonth)) {
                throw new BusinessException(CodeMsg.ADJUSTMENT_SOURCE_NOT_CLOSED);
            }

            // 目标期间不能已关闭
            if (monthlyClosingService.isMonthClosed(bookId, targetYearMonth)) {
                throw new BusinessException(CodeMsg.ADJUSTMENT_TARGET_CLOSED);
            }

            // 创建调整记录
            DifferenceAdjustmentRecordDO adjustment = DifferenceAdjustmentRecordDO.builder()
                    .bookId(bookId)
                    .sourceYearMonth(sourceYearMonth)
                    .targetYearMonth(targetYearMonth)
                    .originalRecordId(originalRecordId)
                    .reversalRequestId(reversalRequestId)
                    .adjustmentType(adjustmentType)
                    .adjustmentAmount(adjustmentAmount)
                    .categoryId(categoryId)
                    .accountId(accountId)
                    .memberUserId(memberUserId)
                    .reason(reason)
                    .relatedClosingId(relatedClosingId)
                    .reviewStatus(RecordConstant.REVIEW_POSTED)
                    .idempotencyKey(idempotencyKey)
                    .createdBy(operatorUserId)
                    .build();

            try {
                adjustmentRecordMapper.insert(adjustment);
            } catch (DuplicateKeyException e) {
                // Layer 2 fallback
                DifferenceAdjustmentRecordDO dup = adjustmentRecordMapper.selectByIdempotencyKey(idempotencyKey);
                if (dup != null) {
                    redisUtil.set(redisIdempKey, dup.getId(), 86400L);
                    return dup;
                }
                throw new BusinessException(CodeMsg.ADJUSTMENT_DUPLICATE);
            }

            // 在 record_detail 中生成调整分录
            RecordDetailDO adjustRecord = RecordDetailDO.builder()
                    .userId(operatorUserId)
                    .recordBookId(bookId)
                    .amount(BigDecimal.valueOf(adjustmentAmount).divide(BigDecimal.valueOf(100)).doubleValue())
                    .occurTime(new Date())
                    .remark("差额调整: " + reason)
                    .reviewStatus(RecordConstant.REVIEW_POSTED)
                    .adjustmentRecordType(adjustmentType)
                    .sourceYearMonth(sourceYearMonth)
                    .idempotencyKey(idempotencyKey)
                    .status(0)
                    .build();
            recordDetailService.save(adjustRecord);

            adjustment.setAdjustmentRecordId(adjustRecord.getId());
            adjustmentRecordMapper.updateById(adjustment);

            // 设置幂等缓存 (afterCommit would be ideal, but simplified here)
            redisUtil.set(redisIdempKey, adjustment.getId(), 86400L);

            // 审计日志
            auditLogService.log(bookId, operatorUserId,
                    RecordConstant.ACTION_ADJUSTMENT_CREATED, "RECORD", adjustment.getId(),
                    "{\"sourceYearMonth\":\"" + sourceYearMonth + "\",\"targetYearMonth\":\"" + targetYearMonth
                    + "\",\"adjustmentType\":\"" + adjustmentType + "\",\"amount\":" + adjustmentAmount
                    + ",\"originalRecordId\":" + originalRecordId + "}");

            return adjustment;
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public void applyAdjustment(Long adjustmentId) {
        DifferenceAdjustmentRecordDO adjustment = adjustmentRecordMapper.selectById(adjustmentId);
        if (adjustment == null) {
            throw new BusinessException(CodeMsg.ADJUSTMENT_NOT_FOUND);
        }
        if (adjustment.getReviewStatus() != RecordConstant.REVIEW_POSTED) {
            throw new BusinessException(CodeMsg.ADJUSTMENT_ALREADY_REVERSED);
        }

        String targetYearMonth = adjustment.getTargetYearMonth();
        Integer bookId = adjustment.getBookId();

        // 获取预算写入锁
        String lockKey = RedisKey.LOCK_PREFIX + "budget:adjust:" + bookId + ":" + targetYearMonth;
        if (!redisLockUtil.tryLock(lockKey, 20)) {
            throw new BusinessException(CodeMsg.ADJUSTMENT_LOCK_FAILED);
        }

        try {
            // 更新DB预算
            BookBudgetDO budget = budgetService.getBudget(bookId, targetYearMonth);
            if (budget != null) {
                BigDecimal adjustDecimal = BigDecimal.valueOf(adjustment.getAdjustmentAmount())
                        .divide(BigDecimal.valueOf(100));
                budget.setUsedAmount(budget.getUsedAmount().add(adjustDecimal));
                bookBudgetMapper.updateById(budget);

                // Redis同步 (仅在key存在时)
                String redisKey = RedisKey.BUDGET_USED_PREFIX + bookId + ":" + targetYearMonth;
                if (redisUtil.hasKey(redisKey)) {
                    long adjustCents = adjustment.getAdjustmentAmount();
                    redisTemplate.opsForValue().increment(redisKey, adjustCents);
                }
            }

            // 审计日志
            auditLogService.log(bookId, adjustment.getCreatedBy(),
                    RecordConstant.ACTION_ADJUSTMENT_APPLIED, "RECORD", adjustmentId,
                    "{\"targetYearMonth\":\"" + targetYearMonth + "\",\"amountApplied\":" + adjustment.getAdjustmentAmount() + "}");
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public MonthlyClosingRecalculationDO triggerRecalculation(Integer bookId, String closedYearMonth,
                                                                Integer operatorUserId, String reason) {
        String lockKey = RedisKey.LOCK_PREFIX + "recalc:" + bookId + ":" + closedYearMonth;
        if (!redisLockUtil.tryLock(lockKey, 60)) {
            throw new BusinessException(CodeMsg.RECALC_LOCK_FAILED);
        }

        try {
            // 加载当前月结
            MonthlyClosingDO closing = monthlyClosingService.getClosing(bookId, closedYearMonth);
            if (closing == null) {
                throw new BusinessException(CodeMsg.MONTH_ALREADY_CLOSED);
            }

            int currentVersion = closing.getSnapshotVersion() != null ? closing.getSnapshotVersion() : 1;

            // 重新聚合 RecordDetail
            List<RecordDetailDO> records = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", closedYearMonth)
                    .isNull("target_account_id"));

            BigDecimal newIncome = BigDecimal.ZERO;
            BigDecimal newExpend = BigDecimal.ZERO;
            for (RecordDetailDO r : records) {
                if (r.getAmount() != null) {
                    if (r.getAmount() > 0) {
                        newIncome = newIncome.add(BigDecimal.valueOf(r.getAmount()));
                    } else {
                        newExpend = newExpend.add(BigDecimal.valueOf(Math.abs(r.getAmount())));
                    }
                }
            }

            // 加上差额调整
            Long adjTotal = adjustmentRecordMapper.sumAdjustmentByTargetPeriod(bookId, closedYearMonth);
            if (adjTotal != null && adjTotal != 0) {
                BigDecimal adjDecimal = BigDecimal.valueOf(adjTotal).divide(BigDecimal.valueOf(100));
                if (adjTotal > 0) {
                    newExpend = newExpend.add(adjDecimal);
                } else {
                    newExpend = newExpend.subtract(adjDecimal.abs());
                }
            }

            long prevIncomeCents = closing.getTotalIncome().multiply(BigDecimal.valueOf(100)).longValue();
            long prevExpendCents = closing.getTotalExpend().multiply(BigDecimal.valueOf(100)).longValue();
            long newIncomeCents = newIncome.multiply(BigDecimal.valueOf(100)).longValue();
            long newExpendCents = newExpend.multiply(BigDecimal.valueOf(100)).longValue();
            long deltaIncome = newIncomeCents - prevIncomeCents;
            long deltaExpend = newExpendCents - prevExpendCents;

            // 无变化则无需重算
            if (deltaIncome == 0 && deltaExpend == 0) {
                throw new BusinessException(CodeMsg.RECALC_NO_ADJUSTMENT);
            }

            int newVersion = currentVersion + 1;

            // CAS 更新月结
            int updated = monthlyClosingMapper.updateWithRecalcVersion(bookId, closedYearMonth,
                    currentVersion, newVersion, newIncome, newExpend,
                    closing.getCarryforwardTotal() != null ? closing.getCarryforwardTotal() : 0L,
                    closing.getOverspentTotal() != null ? closing.getOverspentTotal() : 0L,
                    closing.getPendingImpactTotal() != null ? closing.getPendingImpactTotal() : 0L);
            if (updated == 0) {
                throw new BusinessException(CodeMsg.RECALC_VERSION_CONFLICT);
            }

            // 插入重算记录
            MonthlyClosingRecalculationDO recalc = MonthlyClosingRecalculationDO.builder()
                    .bookId(bookId)
                    .yearMonth(closedYearMonth)
                    .recalcVersion(newVersion)
                    .triggeredBy(operatorUserId)
                    .triggerReason(reason)
                    .previousTotalIncome(prevIncomeCents)
                    .previousTotalExpend(prevExpendCents)
                    .newTotalIncome(newIncomeCents)
                    .newTotalExpend(newExpendCents)
                    .deltaIncome(deltaIncome)
                    .deltaExpend(deltaExpend)
                    .build();
            recalculationMapper.insert(recalc);

            // 审计日志
            auditLogService.log(bookId, operatorUserId,
                    RecordConstant.ACTION_CLOSING_RECALCULATED, "CLOSING", closing.getId(),
                    "{\"yearMonth\":\"" + closedYearMonth + "\",\"recalcVersion\":" + newVersion
                    + ",\"previousIncome\":" + prevIncomeCents + ",\"previousExpend\":" + prevExpendCents
                    + ",\"newIncome\":" + newIncomeCents + ",\"newExpend\":" + newExpendCents
                    + ",\"deltaIncome\":" + deltaIncome + ",\"deltaExpend\":" + deltaExpend + "}");

            return recalc;
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    public List<DifferenceAdjustmentRecordDO> getBySourcePeriod(Integer bookId, String sourceYearMonth) {
        return adjustmentRecordMapper.selectBySourcePeriod(bookId, sourceYearMonth);
    }

    @Override
    public List<DifferenceAdjustmentRecordDO> getByTargetPeriod(Integer bookId, String targetYearMonth) {
        return adjustmentRecordMapper.selectByTargetPeriod(bookId, targetYearMonth);
    }

    @Override
    public DifferenceAdjustmentRecordDO getById(Long adjustmentId) {
        return adjustmentRecordMapper.selectById(adjustmentId);
    }

    @Override
    public List<MonthlyClosingRecalculationDO> getRecalcHistory(Integer bookId, String yearMonth) {
        return recalculationMapper.selectRecalcHistory(bookId, yearMonth);
    }
}
