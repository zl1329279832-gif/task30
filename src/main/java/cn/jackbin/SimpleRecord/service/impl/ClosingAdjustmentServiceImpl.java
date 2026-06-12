package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ClosingAdjustmentMapper;
import cn.jackbin.SimpleRecord.service.ClosingAdjustmentService;
import cn.jackbin.SimpleRecord.service.MonthlyClosingService;
import cn.jackbin.SimpleRecord.service.SharedBookAuditLogService;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月结调整单服务实现
 */
@Service
public class ClosingAdjustmentServiceImpl extends ServiceImpl<ClosingAdjustmentMapper, ClosingAdjustmentDO>
        implements ClosingAdjustmentService {

    @Autowired
    private ClosingAdjustmentMapper closingAdjustmentMapper;

    @Autowired
    @Lazy
    private MonthlyClosingService monthlyClosingService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Override
    @Transactional
    public ClosingAdjustmentDO createAdjustmentIdempotent(String idempotencyKey, Integer bookId,
                                                           String yearMonth, String adjustmentType,
                                                           Long sourceRecordId, Long counterRecordId,
                                                           Integer userId, String category,
                                                           Integer accountId, BigDecimal adjustmentIncome,
                                                           BigDecimal adjustmentExpend, BigDecimal budgetImpact,
                                                           Integer operatorId, String remark) {
        // 幂等检查: DB查找
        ClosingAdjustmentDO existing = getOne(new QueryWrapper<ClosingAdjustmentDO>()
                .eq("idempotency_key", idempotencyKey));
        if (existing != null) {
            return existing;
        }

        String lockKey = RedisKey.LOCK_PREFIX + "adjustment:" + idempotencyKey;
        if (!redisLockUtil.tryLock(lockKey, 15)) {
            throw new BusinessException(CodeMsg.OPERATION_IN_PROGRESS);
        }
        try {
            // 双重检查
            existing = getOne(new QueryWrapper<ClosingAdjustmentDO>()
                    .eq("idempotency_key", idempotencyKey));
            if (existing != null) {
                return existing;
            }

            // 校验月结记录存在
            MonthlyClosingDO closing = monthlyClosingService.getClosing(bookId, yearMonth);
            if (closing == null) {
                throw new BusinessException(CodeMsg.ADJUSTMENT_CLOSING_NOT_FOUND);
            }

            ClosingAdjustmentDO adjustment = ClosingAdjustmentDO.builder()
                    .bookId(bookId)
                    .yearMonth(yearMonth)
                    .closingId(closing.getId())
                    .adjustmentType(adjustmentType)
                    .sourceRecordId(sourceRecordId)
                    .counterRecordId(counterRecordId)
                    .userId(userId)
                    .recordCategory(category)
                    .recordAccountId(accountId)
                    .adjustmentIncome(adjustmentIncome)
                    .adjustmentExpend(adjustmentExpend)
                    .budgetImpact(budgetImpact)
                    .idempotencyKey(idempotencyKey)
                    .operatorId(operatorId)
                    .remark(remark)
                    .status(0)
                    .build();
            save(adjustment);

            auditLogService.log(bookId, operatorId, "CLOSING_ADJUSTMENT", "CLOSING", adjustment.getId(),
                    "{\"type\":\"" + adjustmentType + "\",\"sourceRecord\":" + sourceRecordId
                            + ",\"budgetImpact\":" + budgetImpact + "}");

            return adjustment;
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    public List<ClosingAdjustmentDO> getAdjustments(Integer bookId, String yearMonth) {
        return list(new QueryWrapper<ClosingAdjustmentDO>()
                .eq("book_id", bookId)
                .eq("year_month", yearMonth)
                .orderByDesc("create_time"));
    }

    @Override
    public void getAdjustmentsByPage(Integer bookId, String yearMonth,
                                      PageBO<ClosingAdjustmentDO> pageBO) {
        IPage<ClosingAdjustmentDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        QueryWrapper<ClosingAdjustmentDO> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId);
        if (yearMonth != null) {
            wrapper.eq("year_month", yearMonth);
        }
        wrapper.orderByDesc("create_time");

        IPage<ClosingAdjustmentDO> result = page(page, wrapper);
        pageBO.setList(result.getRecords());
        pageBO.setTotal((int) result.getTotal());
    }

    @Override
    public BigDecimal getNetBudgetImpact(Integer bookId, String yearMonth) {
        BigDecimal impact = closingAdjustmentMapper.queryNetBudgetImpact(bookId, yearMonth);
        return impact != null ? impact : BigDecimal.ZERO;
    }
}
