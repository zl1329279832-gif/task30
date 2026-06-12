package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryoverDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryoverRuleDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryoverMapper;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryoverRuleMapper;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 预算结转服务实现
 */
@Service
public class BudgetCarryoverServiceImpl extends ServiceImpl<BudgetCarryoverMapper, BudgetCarryoverDO>
        implements BudgetCarryoverService {

    @Autowired
    private BudgetCarryoverMapper budgetCarryoverMapper;

    @Autowired
    private BudgetCarryoverRuleMapper budgetCarryoverRuleMapper;

    @Autowired
    @Lazy
    private BudgetService budgetService;

    @Autowired
    @Lazy
    private RecordDetailService recordDetailService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    @Lazy
    private SharedBookService sharedBookService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    @Transactional
    public BudgetCarryoverRuleDO setCarryoverRule(Integer bookId, Integer userId,
                                                   String carryoverType, Integer percent, BigDecimal cap,
                                                   Boolean carryOverspend, String pendingRecordPolicy,
                                                   String effectiveFrom) {
        sharedBookService.checkPermission(bookId, userId, RecordConstant.PERM_SETTLEMENT);

        // 参数校验
        if (RecordConstant.CARRYOVER_TYPE_PERCENTAGE.equals(carryoverType)) {
            if (percent == null || percent < 0 || percent > 100) {
                throw new BusinessException(CodeMsg.CARRYOVER_INVALID_PERCENT);
            }
        }
        if (RecordConstant.CARRYOVER_TYPE_CAPPED.equals(carryoverType)) {
            if (cap == null || cap.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(CodeMsg.CARRYOVER_INVALID_CAP);
            }
        }

        // 查询当前最大版本号
        QueryWrapper<BudgetCarryoverRuleDO> maxVersionQuery = new QueryWrapper<>();
        maxVersionQuery.eq("book_id", bookId)
                .orderByDesc("version")
                .last("LIMIT 1");
        BudgetCarryoverRuleDO latest = budgetCarryoverRuleMapper.selectOne(maxVersionQuery);
        int newVersion = (latest != null) ? latest.getVersion() + 1 : 1;

        // 将旧版本标记为已替代
        if (latest != null && latest.getStatus() == 0) {
            latest.setStatus(1);
            budgetCarryoverRuleMapper.updateById(latest);
        }

        BudgetCarryoverRuleDO rule = BudgetCarryoverRuleDO.builder()
                .bookId(bookId)
                .version(newVersion)
                .carryoverType(carryoverType)
                .carryoverPercent(percent != null ? percent : 100)
                .capAmount(cap)
                .carryOverspend(Boolean.TRUE.equals(carryOverspend) ? 1 : 0)
                .pendingRecordPolicy(pendingRecordPolicy != null ? pendingRecordPolicy : RecordConstant.PENDING_POLICY_IGNORE)
                .effectiveFrom(effectiveFrom)
                .createdBy(userId)
                .status(0)
                .build();
        budgetCarryoverRuleMapper.insert(rule);

        auditLogService.log(bookId, userId, "CARRYOVER_RULE_SET", "BUDGET", rule.getId(),
                "{\"version\":" + newVersion + ",\"type\":\"" + carryoverType + "\"}");
        return rule;
    }

    @Override
    public BudgetCarryoverRuleDO getActiveRule(Integer bookId, String yearMonth) {
        QueryWrapper<BudgetCarryoverRuleDO> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId)
                .eq("status", 0)
                .le("effective_from", yearMonth)
                .orderByDesc("version")
                .last("LIMIT 1");
        return budgetCarryoverRuleMapper.selectOne(wrapper);
    }

    @Override
    public List<BudgetCarryoverRuleDO> listRuleVersions(Integer bookId) {
        return budgetCarryoverRuleMapper.selectList(new QueryWrapper<BudgetCarryoverRuleDO>()
                .eq("book_id", bookId)
                .orderByDesc("version"));
    }

    @Override
    @Transactional
    public BudgetCarryoverDO computeAndApplyCarryover(Integer bookId, String yearMonth,
                                                       Long closingId, BigDecimal budgetAmount,
                                                       BigDecimal usedAmount) {
        String lockKey = RedisKey.LOCK_PREFIX + "carryover:" + bookId + ":" + yearMonth;
        if (!redisLockUtil.tryLock(lockKey, 30)) {
            throw new BusinessException(CodeMsg.OPERATION_IN_PROGRESS);
        }
        try {
            // 幂等: 已存在则跳过
            BudgetCarryoverDO existing = getCarryover(bookId, yearMonth);
            if (existing != null) {
                return existing;
            }

            // 获取适用规则
            BudgetCarryoverRuleDO rule = getActiveRule(bookId, yearMonth);

            // 计算待审核预留
            BigDecimal pendingReserve = BigDecimal.ZERO;
            if (rule != null && RecordConstant.PENDING_POLICY_RESERVE.equals(rule.getPendingRecordPolicy())) {
                // 查询待审核记录金额合计
                List<RecordDetailDO> pendingRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                        .eq("record_book_id", bookId)
                        .eq("review_status", RecordConstant.REVIEW_PENDING)
                        .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                        .isNull("target_account_id"));
                for (RecordDetailDO r : pendingRecords) {
                    if (r.getAmount() != null && r.getAmount() < 0) {
                        pendingReserve = pendingReserve.add(BigDecimal.valueOf(Math.abs(r.getAmount())));
                    }
                }
            }

            // 计算原始结余
            BigDecimal rawCarryover = budgetAmount.subtract(usedAmount).subtract(pendingReserve);

            // 应用规则
            BigDecimal appliedCarryover;
            BigDecimal overspendCarryover = BigDecimal.ZERO;

            if (rawCarryover.compareTo(BigDecimal.ZERO) >= 0) {
                // 有结余
                appliedCarryover = applyRule(rule, rawCarryover);
            } else {
                // 超支
                appliedCarryover = BigDecimal.ZERO;
                if (rule != null && rule.getCarryOverspend() == 1) {
                    overspendCarryover = rawCarryover; // 负值
                }
            }

            // 计算目标月份
            YearMonth sourceYm = YearMonth.parse(yearMonth, YM_FMT);
            String targetYearMonth = sourceYm.plusMonths(1).format(YM_FMT);

            // 持久化结转记录
            BudgetCarryoverDO carryover = BudgetCarryoverDO.builder()
                    .bookId(bookId)
                    .sourceYearMonth(yearMonth)
                    .targetYearMonth(targetYearMonth)
                    .ruleId(rule != null ? rule.getId() : null)
                    .budgetAmount(budgetAmount)
                    .usedAmount(usedAmount)
                    .pendingReserve(pendingReserve)
                    .rawCarryover(rawCarryover)
                    .appliedCarryover(appliedCarryover)
                    .overspendCarryover(overspendCarryover)
                    .closingId(closingId)
                    .status(0)
                    .build();
            save(carryover);

            // 更新目标月预算: 获取或创建下月预算, 将结转金额加入
            BigDecimal netCarryover = appliedCarryover.add(overspendCarryover);
            if (netCarryover.compareTo(BigDecimal.ZERO) != 0) {
                BookBudgetDO targetBudget = budgetService.getBudget(bookId, targetYearMonth);
                if (targetBudget != null) {
                    targetBudget.setBudgetAmount(targetBudget.getBudgetAmount().add(netCarryover));
                    budgetService.updateById(targetBudget);
                }
                // 若目标月尚无预算, 结转金额在 getEffectiveBudget 中体现
            }

            auditLogService.log(bookId, 0, "BUDGET_CARRYOVER", "BUDGET", carryover.getId(),
                    "{\"source\":\"" + yearMonth + "\",\"target\":\"" + targetYearMonth
                            + "\",\"applied\":" + appliedCarryover + ",\"overspend\":" + overspendCarryover + "}");

            return carryover;
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    public BudgetCarryoverDO getCarryover(Integer bookId, String yearMonth) {
        return getOne(new QueryWrapper<BudgetCarryoverDO>()
                .eq("book_id", bookId)
                .eq("source_year_month", yearMonth));
    }

    @Override
    public BigDecimal getEffectiveBudget(Integer bookId, String yearMonth) {
        BookBudgetDO budget = budgetService.getBudget(bookId, yearMonth);
        BigDecimal originalBudget = (budget != null) ? budget.getBudgetAmount() : BigDecimal.ZERO;

        // 查找上月结转入本月的记录
        YearMonth currentYm = YearMonth.parse(yearMonth, YM_FMT);
        String previousMonth = currentYm.minusMonths(1).format(YM_FMT);
        BudgetCarryoverDO incomingCarryover = getCarryover(bookId, previousMonth);

        if (incomingCarryover != null) {
            BigDecimal netCarryover = incomingCarryover.getAppliedCarryover()
                    .add(incomingCarryover.getOverspendCarryover());
            return originalBudget.add(netCarryover);
        }

        return originalBudget;
    }

    /**
     * 根据规则计算实际结转金额
     */
    private BigDecimal applyRule(BudgetCarryoverRuleDO rule, BigDecimal rawCarryover) {
        if (rule == null || RecordConstant.CARRYOVER_TYPE_FULL.equals(rule.getCarryoverType())) {
            return rawCarryover;
        }

        if (RecordConstant.CARRYOVER_TYPE_PERCENTAGE.equals(rule.getCarryoverType())) {
            return rawCarryover.multiply(BigDecimal.valueOf(rule.getCarryoverPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        if (RecordConstant.CARRYOVER_TYPE_CAPPED.equals(rule.getCarryoverType())) {
            return rawCarryover.min(rule.getCapAmount());
        }

        return rawCarryover;
    }
}
