package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.MonthlyClosingMapper;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 月结服务实现
 */
@Service
public class MonthlyClosingServiceImpl extends ServiceImpl<MonthlyClosingMapper, MonthlyClosingDO>
        implements MonthlyClosingService {

    @Autowired
    private MonthlyClosingMapper monthlyClosingMapper;

    @Autowired
    private BookBudgetMapper bookBudgetMapper;

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private SharedBookService sharedBookService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired(required = false)
    @Lazy
    private BudgetCarryforwardService budgetCarryforwardService;

    @Autowired(required = false)
    @Lazy
    private MemberSettlementService memberSettlementService;

    @Autowired
    private RecordBookService recordBookService;

    @Override
    @Transactional
    public void closeMonth(Integer bookId, Integer userId, String yearMonth, String remark) {
        sharedBookService.checkPermission(bookId, userId, RecordConstant.PERM_SETTLEMENT);

        String lockKey = RedisKey.LOCK_PREFIX + "closing:" + bookId + ":" + yearMonth;
        if (!redisLockUtil.tryLock(lockKey, 60)) {
            throw new BusinessException(CodeMsg.OPERATION_IN_PROGRESS);
        }
        try {
            // 检查是否已结
            if (isMonthClosed(bookId, yearMonth)) {
                throw new BusinessException(CodeMsg.MONTH_ALREADY_CLOSED);
            }

            // 检查无待审核记录
            long pendingCount = recordDetailService.count(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .eq("review_status", RecordConstant.REVIEW_PENDING)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth));
            if (pendingCount > 0) {
                throw new BusinessException(CodeMsg.PENDING_RECORDS_EXIST);
            }

            // 快照收支
            List<RecordDetailDO> monthRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                    .isNull("target_account_id")); // 排除转账目标

            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpend = BigDecimal.ZERO;
            for (RecordDetailDO r : monthRecords) {
                if (r.getAmount() != null) {
                    if (r.getAmount() > 0) {
                        totalIncome = totalIncome.add(BigDecimal.valueOf(r.getAmount()));
                    } else {
                        totalExpend = totalExpend.add(BigDecimal.valueOf(Math.abs(r.getAmount())));
                    }
                }
            }

            // 插入月结记录
            MonthlyClosingDO closing = MonthlyClosingDO.builder()
                    .bookId(bookId)
                    .yearMonth(yearMonth)
                    .closedBy(userId)
                    .closedTime(new Date())
                    .totalIncome(totalIncome)
                    .totalExpend(totalExpend)
                    .remark(remark)
                    .status(0)
                    .build();
            save(closing);

            // 设置Redis标记 (无过期)
            String redisKey = RedisKey.MONTHLY_CLOSING_PREFIX + bookId + ":" + yearMonth;
            redisUtil.set(redisKey, 1);

            // 删除预算缓存
            redisUtil.del(RedisKey.BUDGET_USED_PREFIX + bookId + ":" + yearMonth);

            // 执行预算结转 (如果账本启用了结转)
            long carryforwardTotal = 0, overspentTotal = 0, pendingImpactTotal = 0;
            if (budgetCarryforwardService != null) {
                try {
                    RecordBookDO book = recordBookService.getById(bookId);
                    if (book != null && book.getCarryforwardEnabled() != null && book.getCarryforwardEnabled() == 1) {
                        long[] cfResult = budgetCarryforwardService.executeCarryforward(bookId, yearMonth, userId);
                        carryforwardTotal = cfResult[0];
                        overspentTotal = cfResult[1];
                        pendingImpactTotal = cfResult[2];
                    }
                } catch (Exception e) {
                    // 结转失败不影响月结结果, 仅记录日志
                    // 结转可以稍后手动执行
                }
            }

            // 捕获成员责任快照
            if (memberSettlementService != null) {
                try {
                    memberSettlementService.captureSnapshots(bookId, yearMonth);
                } catch (Exception e) {
                    // 快照失败不影响月结结果
                }
            }

            auditLogService.log(bookId, userId, "MONTHLY_CLOSE", "CLOSING", closing.getId(),
                    "{\"yearMonth\":\"" + yearMonth + "\",\"income\":" + totalIncome + ",\"expend\":" + totalExpend + "}");
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    public boolean isMonthClosed(Integer bookId, String yearMonth) {
        String redisKey = RedisKey.MONTHLY_CLOSING_PREFIX + bookId + ":" + yearMonth;
        if (redisUtil.hasKey(redisKey)) {
            return true;
        }
        // DB fallback
        long count = count(new QueryWrapper<MonthlyClosingDO>()
                .eq("book_id", bookId)
                .eq("year_month", yearMonth));
        if (count > 0) {
            redisUtil.set(redisKey, 1);
            return true;
        }
        return false;
    }

    @Override
    public void checkNotClosed(Integer bookId, Date occurTime) {
        if (occurTime == null) return;
        String yearMonth = new SimpleDateFormat("yyyy-MM").format(occurTime);
        if (isMonthClosed(bookId, yearMonth)) {
            throw new BusinessException(CodeMsg.MONTH_CLOSED_CANNOT_MODIFY);
        }
    }

    @Override
    public MonthlyClosingDO getClosing(Integer bookId, String yearMonth) {
        return getOne(new QueryWrapper<MonthlyClosingDO>()
                .eq("book_id", bookId)
                .eq("year_month", yearMonth));
    }

    @Override
    public void getClosingsByPage(Integer bookId, PageBO<MonthlyClosingDO> pageBO) {
        IPage<MonthlyClosingDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        IPage<MonthlyClosingDO> result = page(page, new QueryWrapper<MonthlyClosingDO>()
                .eq("book_id", bookId)
                .orderByDesc("year_month"));
        pageBO.setList(result.getRecords());
        pageBO.setTotal((int) result.getTotal());
    }

    /**
     * 获取月结详情 (含结转和重算信息)
     */
    public MonthlyClosingDO getClosingDetail(Integer bookId, String yearMonth) {
        return getClosing(bookId, yearMonth);
    }
}
