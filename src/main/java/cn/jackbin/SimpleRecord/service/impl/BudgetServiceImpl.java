package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
import cn.jackbin.SimpleRecord.service.BudgetCarryoverService;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.service.SharedBookAuditLogService;
import cn.jackbin.SimpleRecord.service.SharedBookService;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算服务实现
 */
@Service
public class BudgetServiceImpl extends ServiceImpl<BookBudgetMapper, BookBudgetDO> implements BudgetService {

    @Autowired
    private BookBudgetMapper bookBudgetMapper;

    @Autowired
    private SharedBookService sharedBookService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    @Lazy
    private BudgetCarryoverService budgetCarryoverService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void setBudget(Integer bookId, Integer userId, String yearMonth, BigDecimal amount, Integer warnThreshold) {
        sharedBookService.checkPermission(bookId, userId, RecordConstant.PERM_SETTLEMENT);

        BookBudgetDO existing = getBudget(bookId, yearMonth);
        if (existing != null) {
            existing.setBudgetAmount(amount);
            if (warnThreshold != null) {
                existing.setWarnThreshold(warnThreshold);
            }
            updateById(existing);
        } else {
            BookBudgetDO budget = BookBudgetDO.builder()
                    .bookId(bookId)
                    .yearMonth(yearMonth)
                    .budgetAmount(amount)
                    .usedAmount(BigDecimal.ZERO)
                    .warnThreshold(warnThreshold != null ? warnThreshold : 80)
                    .status(0)
                    .build();
            save(budget);
        }

        // 刷新Redis缓存: 从DB重新查询已使用金额
        BigDecimal usedAmount = bookBudgetMapper.queryUsedAmountByMonth(bookId, yearMonth);
        String redisKey = RedisKey.BUDGET_USED_PREFIX + bookId + ":" + yearMonth;
        long usedCents = usedAmount.multiply(BigDecimal.valueOf(100)).longValue();
        redisUtil.set(redisKey, usedCents, 3600L);

        auditLogService.log(bookId, userId, "BUDGET_SET", "BUDGET", null,
                "{\"yearMonth\":\"" + yearMonth + "\",\"amount\":" + amount + "}");
    }

    @Override
    public BookBudgetDO getBudget(Integer bookId, String yearMonth) {
        return getOne(new QueryWrapper<BookBudgetDO>()
                .eq("book_id", bookId)
                .eq("year_month", yearMonth));
    }

    @Override
    public boolean checkBudgetWarning(Integer bookId, BigDecimal newExpenseAmount, String yearMonth) {
        BookBudgetDO budget = getBudget(bookId, yearMonth);
        if (budget == null) {
            return false;
        }
        BigDecimal effectiveBudget = budgetCarryoverService.getEffectiveBudget(bookId, yearMonth);
        BigDecimal projectedUsed = getUsedAmountFromRedis(bookId, yearMonth).add(newExpenseAmount);
        BigDecimal warnAmount = effectiveBudget
                .multiply(BigDecimal.valueOf(budget.getWarnThreshold()))
                .divide(BigDecimal.valueOf(100));
        return projectedUsed.compareTo(warnAmount) >= 0;
    }

    @Override
    @Transactional
    public void atomicIncrementUsed(Integer bookId, String yearMonth, BigDecimal amount) {
        BookBudgetDO budget = getBudget(bookId, yearMonth);
        if (budget == null) {
            return; // 未设置预算, 不检查
        }

        String redisKey = RedisKey.BUDGET_USED_PREFIX + bookId + ":" + yearMonth;
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        // 确保Redis key存在
        if (!redisUtil.hasKey(redisKey)) {
            BigDecimal dbUsed = bookBudgetMapper.queryUsedAmountByMonth(bookId, yearMonth);
            long dbCents = dbUsed.multiply(BigDecimal.valueOf(100)).longValue();
            redisUtil.set(redisKey, dbCents, 3600L);
        }

        // 原子递增
        Long newTotal = redisTemplate.opsForValue().increment(redisKey, amountCents);
        if (newTotal == null) {
            return;
        }

        BigDecimal effectiveBudget = budgetCarryoverService.getEffectiveBudget(bookId, yearMonth);
        long budgetCents = effectiveBudget.multiply(BigDecimal.valueOf(100)).longValue();
        if (newTotal > budgetCents) {
            // 回滚
            redisTemplate.opsForValue().increment(redisKey, -amountCents);
            throw new BusinessException(CodeMsg.BUDGET_EXCEEDED);
        }

        // 更新DB
        BigDecimal newUsed = BigDecimal.valueOf(newTotal).divide(BigDecimal.valueOf(100));
        budget.setUsedAmount(newUsed);
        updateById(budget);

        // 预警检查
        long warnCents = effectiveBudget
                .multiply(BigDecimal.valueOf(budget.getWarnThreshold()))
                .divide(BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(100)).longValue();
        if (newTotal >= warnCents) {
            auditLogService.log(bookId, 0, "BUDGET_WARN", "BUDGET", null,
                    "{\"yearMonth\":\"" + yearMonth + "\",\"used\":" + newUsed + "}");
        }
    }

    @Override
    @Transactional
    public void atomicDecrementUsed(Integer bookId, String yearMonth, BigDecimal amount) {
        String redisKey = RedisKey.BUDGET_USED_PREFIX + bookId + ":" + yearMonth;
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        if (redisUtil.hasKey(redisKey)) {
            Long newTotal = redisTemplate.opsForValue().increment(redisKey, -amountCents);
            if (newTotal != null && newTotal < 0) {
                redisUtil.set(redisKey, 0L, 3600L);
            }
        }

        // 更新DB
        BookBudgetDO budget = getBudget(bookId, yearMonth);
        if (budget != null) {
            BigDecimal dbUsed = bookBudgetMapper.queryUsedAmountByMonth(bookId, yearMonth);
            budget.setUsedAmount(dbUsed);
            updateById(budget);
        }
    }

    @Override
    public List<BookBudgetDO> listBudgets(Integer bookId, String year) {
        return list(new QueryWrapper<BookBudgetDO>()
                .eq("book_id", bookId)
                .likeRight("year_month", year)
                .orderByAsc("year_month"));
    }

    private BigDecimal getUsedAmountFromRedis(Integer bookId, String yearMonth) {
        String redisKey = RedisKey.BUDGET_USED_PREFIX + bookId + ":" + yearMonth;
        Object cached = redisUtil.get(redisKey);
        if (cached != null) {
            long cents = ((Number) cached).longValue();
            return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));
        }
        BigDecimal dbUsed = bookBudgetMapper.queryUsedAmountByMonth(bookId, yearMonth);
        long cents = dbUsed.multiply(BigDecimal.valueOf(100)).longValue();
        redisUtil.set(redisKey, cents, 3600L);
        return dbUsed;
    }
}
