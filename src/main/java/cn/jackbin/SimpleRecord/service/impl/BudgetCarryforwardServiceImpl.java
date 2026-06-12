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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算结转服务实现
 */
@Service
public class BudgetCarryforwardServiceImpl implements BudgetCarryforwardService {

    @Autowired
    private BudgetCarryforwardRuleMapper carryforwardRuleMapper;

    @Autowired
    private BudgetCarryforwardLogMapper carryforwardLogMapper;

    @Autowired
    private BookBudgetMapper bookBudgetMapper;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private SharedBookService sharedBookService;

    @Autowired
    private RecordBookService recordBookService;

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

    @Override
    @Transactional
    public long[] executeCarryforward(Integer bookId, String yearMonth, Integer operatorUserId) {
        // 检查结转是否已执行
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + bookId + ":" + yearMonth;
        if (redisUtil.hasKey(cfExecutedKey)) {
            throw new BusinessException(CodeMsg.CARRYFORWARD_ALREADY_EXECUTED);
        }

        // 获取结转锁
        String lockKey = RedisKey.LOCK_PREFIX + "carryforward:" + bookId + ":" + yearMonth;
        if (!redisLockUtil.tryLock(lockKey, 60)) {
            throw new BusinessException(CodeMsg.CARRYFORWARD_LOCK_FAILED);
        }

        try {
            // 加载活跃规则 (最高版本, fallback to book default)
            BudgetCarryforwardRuleDO rule = carryforwardRuleMapper.selectActiveRule(bookId, yearMonth);
            String ruleType;
            BigDecimal carryforwardRate;
            Long maxCarryforwardAmount;
            Integer includePending;
            String overspentMode;

            if (rule != null) {
                ruleType = rule.getRuleType();
                carryforwardRate = rule.getCarryforwardRate();
                maxCarryforwardAmount = rule.getMaxCarryforwardAmount();
                includePending = rule.getIncludePending();
                overspentMode = rule.getOverspentMode();
            } else {
                // fallback: 使用账本默认规则
                RecordBookDO book = recordBookService.getById(bookId);
                ruleType = book != null && book.getCarryforwardDefaultRule() != null
                        ? book.getCarryforwardDefaultRule() : RecordConstant.CARRYFORWARD_RULE_FULL;
                carryforwardRate = BigDecimal.ONE;
                maxCarryforwardAmount = null;
                includePending = 0;
                overspentMode = RecordConstant.OVERSPENT_CARRY_DEBT;
            }

            int ruleVersion = rule != null ? rule.getRuleVersion() : 0;

            // NONE 规则 = 不结转
            if (RecordConstant.CARRYFORWARD_RULE_NONE.equals(ruleType)) {
                redisUtil.set(cfExecutedKey, 1);
                return new long[]{0, 0, 0, 0};
            }

            // 计算下期
            String targetYearMonth = computeNextMonth(yearMonth);

            // 查询当期预算
            BookBudgetDO currentBudget = budgetService.getBudget(bookId, yearMonth);
            if (currentBudget == null) {
                // 无预算设置, 标记已执行, 无结转
                redisUtil.set(cfExecutedKey, 1);
                return new long[]{0, 0, 0, 0};
            }

            // 计算 remaining = budgetAmount - usedAmount
            BigDecimal remaining = currentBudget.getBudgetAmount().subtract(currentBudget.getUsedAmount());

            // 按规则计算结转金额
            BigDecimal carryforwardDecimal;
            if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
                // 结余场景
                carryforwardDecimal = remaining.multiply(carryforwardRate).setScale(2, RoundingMode.HALF_UP);
            } else {
                // 超支场景
                switch (overspentMode) {
                    case RecordConstant.OVERSPENT_WRITE_OFF:
                        carryforwardDecimal = BigDecimal.ZERO;
                        break;
                    case RecordConstant.OVERSPENT_CAP_AT_ZERO:
                        carryforwardDecimal = BigDecimal.ZERO;
                        break;
                    case RecordConstant.OVERSPENT_CARRY_DEBT:
                    default:
                        carryforwardDecimal = remaining.multiply(carryforwardRate).setScale(2, RoundingMode.HALF_UP);
                        break;
                }
            }

            // 上限截断 (仅正数)
            if (maxCarryforwardAmount != null && carryforwardDecimal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal maxDecimal = BigDecimal.valueOf(maxCarryforwardAmount).divide(BigDecimal.valueOf(100));
                if (carryforwardDecimal.compareTo(maxDecimal) > 0) {
                    carryforwardDecimal = maxDecimal;
                }
            }

            long carryforwardCents = carryforwardDecimal.multiply(BigDecimal.valueOf(100)).longValue();
            long originalCents = currentBudget.getBudgetAmount().multiply(BigDecimal.valueOf(100)).longValue();
            long usedCents = currentBudget.getUsedAmount().multiply(BigDecimal.valueOf(100)).longValue();

            // 超支总额
            long overspentCents = remaining.compareTo(BigDecimal.ZERO) < 0
                    ? remaining.abs().multiply(BigDecimal.valueOf(100)).longValue() : 0;

            // 待审核影响
            long pendingImpactCents = 0;
            if (includePending != null && includePending == 1) {
                pendingImpactCents = computePendingImpact(bookId, yearMonth);
            }

            // Upsert 下期预算
            BookBudgetDO targetBudget = budgetService.getBudget(bookId, targetYearMonth);
            if (targetBudget == null) {
                // 创建下期预算, 初始 budget=0, 结转写入 carryforwardAmount
                BookBudgetDO newBudget = BookBudgetDO.builder()
                        .bookId(bookId)
                        .yearMonth(targetYearMonth)
                        .budgetAmount(carryforwardDecimal)
                        .usedAmount(BigDecimal.ZERO)
                        .warnThreshold(80)
                        .carryforwardAmount(carryforwardCents)
                        .sourceYearMonth(yearMonth)
                        .ruleVersion(ruleVersion)
                        .status(0)
                        .build();
                bookBudgetMapper.insert(newBudget);
            } else {
                // 更新下期预算的结转金额
                bookBudgetMapper.applyCarryforward(bookId, targetYearMonth,
                        carryforwardCents, carryforwardDecimal, yearMonth, ruleVersion);
            }

            // 生成结转日志 (汇总行)
            BudgetCarryforwardLogDO log = BudgetCarryforwardLogDO.builder()
                    .bookId(bookId)
                    .sourceYearMonth(yearMonth)
                    .targetYearMonth(targetYearMonth)
                    .sourceBudgetId(currentBudget.getId())
                    .originalAmount(originalCents)
                    .usedAmount(usedCents)
                    .carryforwardAmount(carryforwardCents)
                    .pendingImpactAmount(pendingImpactCents)
                    .ruleVersion(ruleVersion)
                    .ruleType(ruleType)
                    .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                    .build();
            carryforwardLogMapper.insert(log);

            // 按成员维度生成日志
            generatePerMemberLogs(bookId, yearMonth, targetYearMonth, ruleVersion, ruleType);

            // 标记结转已执行
            redisUtil.set(cfExecutedKey, 1);

            // 审计日志
            auditLogService.log(bookId, operatorUserId,
                    RecordConstant.ACTION_CARRYFORWARD_EXECUTED, "BUDGET", currentBudget.getId(),
                    "{\"sourceYearMonth\":\"" + yearMonth + "\",\"targetYearMonth\":\"" + targetYearMonth
                    + "\",\"totalAmount\":" + carryforwardCents + ",\"overspentAmount\":" + overspentCents
                    + ",\"pendingImpact\":" + pendingImpactCents + ",\"ruleVersion\":" + ruleVersion + "}");

            return new long[]{carryforwardCents, overspentCents, pendingImpactCents, 1};
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public void rollbackCarryforward(Integer bookId, String yearMonth, Integer operatorUserId) {
        String lockKey = RedisKey.LOCK_PREFIX + "carryforward:" + bookId + ":" + yearMonth;
        if (!redisLockUtil.tryLock(lockKey, 60)) {
            throw new BusinessException(CodeMsg.CARRYFORWARD_LOCK_FAILED);
        }

        try {
            // 查询生效日志
            List<BudgetCarryforwardLogDO> logs = carryforwardLogMapper.selectBySourcePeriod(bookId, yearMonth);
            if (logs.isEmpty()) {
                throw new BusinessException(CodeMsg.CARRYFORWARD_RULE_NOT_FOUND);
            }

            String targetYearMonth = logs.get(0).getTargetYearMonth();

            // 检查目标期间是否有非结转记录
            long recordCount = recordDetailService.count(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", targetYearMonth)
                    .isNull("adjustment_record_type")
                    .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED));
            if (recordCount > 0) {
                throw new BusinessException(CodeMsg.CARRYFORWARD_ROLLBACK_NOT_ALLOWED);
            }

            // 回滚日志状态
            for (BudgetCarryforwardLogDO log : logs) {
                log.setStatus(RecordConstant.CARRYFORWARD_LOG_ROLLED_BACK);
                carryforwardLogMapper.updateById(log);
            }

            // 回退下期预算的结转金额
            BookBudgetDO targetBudget = budgetService.getBudget(bookId, targetYearMonth);
            if (targetBudget != null && targetBudget.getCarryforwardAmount() != null) {
                long cfAmount = targetBudget.getCarryforwardAmount();
                BigDecimal cfDecimal = BigDecimal.valueOf(cfAmount).divide(BigDecimal.valueOf(100));
                targetBudget.setBudgetAmount(targetBudget.getBudgetAmount().subtract(cfDecimal));
                targetBudget.setCarryforwardAmount(0L);
                targetBudget.setSourceYearMonth(null);
                targetBudget.setRuleVersion(null);
                bookBudgetMapper.updateById(targetBudget);
            }

            // 删除Redis标记
            String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + bookId + ":" + yearMonth;
            redisUtil.del(cfExecutedKey);

            // 审计日志
            auditLogService.log(bookId, operatorUserId,
                    RecordConstant.ACTION_CARRYFORWARD_ROLLBACK, "BUDGET", null,
                    "{\"sourceYearMonth\":\"" + yearMonth + "\",\"targetYearMonth\":\"" + targetYearMonth
                    + "\",\"rolledBackAmount\":" + (targetBudget != null ? targetBudget.getCarryforwardAmount() : 0) + "}");
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public BudgetCarryforwardRuleDO saveRule(Integer bookId, String yearMonth, String ruleType,
                                              BigDecimal carryforwardRate, Long maxCarryforwardAmount,
                                              Integer expireMonths, Integer includePending,
                                              String overspentMode, String categoryFilter,
                                              Integer operatorUserId) {
        sharedBookService.checkPermission(bookId, operatorUserId, RecordConstant.PERM_SETTLEMENT);

        // 校验结转比例
        if (carryforwardRate != null && (carryforwardRate.compareTo(BigDecimal.ZERO) < 0
                || carryforwardRate.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException(CodeMsg.CARRYFORWARD_RATE_INVALID);
        }

        // 查询当前最高版本
        BudgetCarryforwardRuleDO currentRule = carryforwardRuleMapper.selectActiveRule(bookId, yearMonth);
        int newVersion = (currentRule != null ? currentRule.getRuleVersion() : 0) + 1;

        BudgetCarryforwardRuleDO rule = BudgetCarryforwardRuleDO.builder()
                .bookId(bookId)
                .yearMonth(yearMonth)
                .ruleVersion(newVersion)
                .ruleType(ruleType)
                .carryforwardRate(carryforwardRate != null ? carryforwardRate : BigDecimal.ONE)
                .maxCarryforwardAmount(maxCarryforwardAmount)
                .expireMonths(expireMonths)
                .includePending(includePending != null ? includePending : 0)
                .overspentMode(overspentMode != null ? overspentMode : RecordConstant.OVERSPENT_CARRY_DEBT)
                .categoryFilter(categoryFilter)
                .createdBy(operatorUserId)
                .build();
        carryforwardRuleMapper.insert(rule);

        // 审计日志
        String action = currentRule != null
                ? RecordConstant.ACTION_CARRYFORWARD_RULE_UPDATED
                : RecordConstant.ACTION_CARRYFORWARD_RULE_CREATED;
        auditLogService.log(bookId, operatorUserId, action, "BUDGET", rule.getId(),
                "{\"yearMonth\":\"" + yearMonth + "\",\"ruleType\":\"" + ruleType
                + "\",\"ruleVersion\":" + newVersion + ",\"carryforwardRate\":" + carryforwardRate + "}");

        return rule;
    }

    @Override
    public BudgetCarryforwardRuleDO getActiveRule(Integer bookId, String yearMonth) {
        return carryforwardRuleMapper.selectActiveRule(bookId, yearMonth);
    }

    @Override
    public List<BudgetCarryforwardRuleDO> getRuleHistory(Integer bookId, String yearMonth) {
        return carryforwardRuleMapper.selectRuleHistory(bookId, yearMonth);
    }

    @Override
    public List<BudgetCarryforwardLogDO> getCarryforwardLogs(Integer bookId, String sourceYearMonth) {
        return carryforwardLogMapper.selectBySourcePeriod(bookId, sourceYearMonth);
    }

    // ========== Private Helpers ==========

    /**
     * 计算下一期 yyyy-MM
     */
    private String computeNextMonth(String yearMonth) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
            Date date = sdf.parse(yearMonth);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, 1);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            throw new BusinessException(CodeMsg.PARAMETER_ILLEGAL);
        }
    }

    /**
     * 计算待审核记录的预算影响 (以分为单位)
     */
    private long computePendingImpact(Integer bookId, String yearMonth) {
        List<RecordDetailDO> pendingRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                .eq("record_book_id", bookId)
                .eq("review_status", RecordConstant.REVIEW_PENDING)
                .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                .isNull("target_account_id"));

        BigDecimal total = BigDecimal.ZERO;
        for (RecordDetailDO r : pendingRecords) {
            if (r.getAmount() != null && r.getAmount() < 0) {
                total = total.add(BigDecimal.valueOf(Math.abs(r.getAmount())));
            }
        }
        return total.multiply(BigDecimal.valueOf(100)).longValue();
    }

    /**
     * 按成员维度生成结转日志
     */
    private void generatePerMemberLogs(Integer bookId, String yearMonth, String targetYearMonth,
                                        int ruleVersion, String ruleType) {
        // 查询该账本所有成员 (含已移除, 历史归属)
        List<SharedBookMemberDO> members = sharedBookService.listMembers(bookId);

        for (SharedBookMemberDO member : members) {
            // 计算该成员在该期的支出
            List<RecordDetailDO> memberRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .eq("user_id", member.getUserId())
                    .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                    .isNull("target_account_id"));

            BigDecimal memberUsed = BigDecimal.ZERO;
            for (RecordDetailDO r : memberRecords) {
                if (r.getAmount() != null && r.getAmount() < 0) {
                    memberUsed = memberUsed.add(BigDecimal.valueOf(Math.abs(r.getAmount())));
                }
            }

            if (memberUsed.compareTo(BigDecimal.ZERO) > 0) {
                long memberUsedCents = memberUsed.multiply(BigDecimal.valueOf(100)).longValue();
                BudgetCarryforwardLogDO memberLog = BudgetCarryforwardLogDO.builder()
                        .bookId(bookId)
                        .sourceYearMonth(yearMonth)
                        .targetYearMonth(targetYearMonth)
                        .memberUserId(member.getUserId())
                        .originalAmount(0L)
                        .usedAmount(memberUsedCents)
                        .carryforwardAmount(0L)
                        .pendingImpactAmount(0L)
                        .ruleVersion(ruleVersion)
                        .ruleType(ruleType)
                        .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                        .build();
                carryforwardLogMapper.insert(memberLog);
            }
        }
    }
}
