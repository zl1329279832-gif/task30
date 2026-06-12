package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryforwardLogMapper;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryforwardRuleMapper;
import cn.jackbin.SimpleRecord.service.impl.BudgetCarryforwardServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 预算结转服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预算结转服务测试")
class BudgetCarryforwardServiceTest {

    @Spy
    @InjectMocks
    private BudgetCarryforwardServiceImpl carryforwardService;

    @Mock
    private BudgetCarryforwardRuleMapper carryforwardRuleMapper;

    @Mock
    private BudgetCarryforwardLogMapper carryforwardLogMapper;

    @Mock
    private BookBudgetMapper bookBudgetMapper;

    @Mock
    private BudgetService budgetService;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private RecordBookService recordBookService;

    @Mock
    private MonthlyClosingService monthlyClosingService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 100;
    private static final Integer USER_ID = 1;
    private static final String YEAR_MONTH = "2026-05";
    private static final String TARGET_YEAR_MONTH = "2026-06";

    @Test
    @DisplayName("全额结转-结余场景-正确计算结转金额并写入下期")
    void executeCarryforward_fullRule_surplus_correctlyCarriesForward() {
        // budget=1000, used=600, remaining=400, FULL rule, rate=1.0 -> carryforward=400
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("600"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(40000, result[0]); // carryforwardCents = 400 * 100
        assertEquals(0, result[1]);     // overspentCents = 0 (surplus)
        verify(bookBudgetMapper).insert(argThat(budget ->
                budget.getCarryforwardAmount() == 40000L));
        verify(redisUtil).set(eq(cfExecutedKey), eq(1));
    }

    @Test
    @DisplayName("全额结转-超支场景-CARRY_DEBT模式下负结转")
    void executeCarryforward_fullRule_overspent_carryDebt() {
        // budget=1000, used=1200, remaining=-200, CARRY_DEBT -> carryforward=-20000 cents
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("1200"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(-20000, result[0]); // carryforwardCents = -200 * 100
        assertEquals(20000, result[1]);  // overspentCents = 200 * 100
    }

    @Test
    @DisplayName("部分结转-50%比例-正确截断")
    void executeCarryforward_partialRule_fiftyPercent() {
        // budget=1000, used=600, remaining=400, PARTIAL rate=0.5 -> carryforward=20000 cents
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_PARTIAL,
                new BigDecimal("0.5"), null, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("600"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(20000, result[0]); // 400 * 0.5 * 100 = 20000
    }

    @Test
    @DisplayName("结转上限-超出maxCarryforwardAmount时截断")
    void executeCarryforward_capAtMaxAmount() {
        // budget=1000, used=200, remaining=800, maxCarryforwardAmount=30000(300 yuan) -> carryforward=30000
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, 30000L, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("200"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(30000, result[0]); // capped at 30000 cents (300 yuan)
    }

    @Test
    @DisplayName("WRITE_OFF模式-超支不结转")
    void executeCarryforward_overspent_writeOff_zero() {
        // budget=1000, used=1200, WRITE_OFF -> carryforward=0
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_WRITE_OFF);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("1200"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(0, result[0]);     // WRITE_OFF -> 0 carryforward
        assertEquals(20000, result[1]); // overspent = 200 * 100
    }

    @Test
    @DisplayName("CAP_AT_ZERO模式-超支截断为0")
    void executeCarryforward_overspent_capAtZero() {
        // budget=1000, used=1200, CAP_AT_ZERO -> carryforward=0
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_CAP_AT_ZERO);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("1200"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(0, result[0]);     // CAP_AT_ZERO -> 0 carryforward
        assertEquals(20000, result[1]); // overspent = 200 * 100
    }

    @Test
    @DisplayName("含待审核记录-待审核影响纳入结转计算")
    void executeCarryforward_includePending_pendingImpactIncluded() {
        // includePending=1, has pending records -> pendingImpactCents > 0
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 1, RecordConstant.OVERSPENT_CARRY_DEBT);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("600"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        // Mock pending records: 2 pending expenditures totaling 150 yuan
        RecordDetailDO pending1 = new RecordDetailDO();
        pending1.setAmount(-100.0);
        RecordDetailDO pending2 = new RecordDetailDO();
        pending2.setAmount(-50.0);
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(pending1, pending2));

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(40000, result[0]);  // carryforwardCents = 400 * 100
        assertEquals(15000, result[2]);  // pendingImpactCents = 150 * 100
    }

    @Test
    @DisplayName("NONE规则-不执行结转")
    void executeCarryforward_noneRule_noCarryforward() {
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_NONE,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(0, result[0]); // no carryforward
        assertEquals(0, result[1]); // no overspent
        assertEquals(0, result[2]); // no pending
        assertEquals(0, result[3]); // log count = 0
        verify(redisUtil).set(eq(cfExecutedKey), eq(1));
        verify(bookBudgetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("规则版本-使用最高版本规则")
    void executeCarryforward_usesHighestRuleVersion() {
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        // Rule with version 3
        BudgetCarryforwardRuleDO rule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        rule.setRuleVersion(3);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(rule);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("500"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(50000, result[0]); // 500 * 100
        // Verify log contains version 3
        verify(carryforwardLogMapper).insert(argThat(log -> log.getRuleVersion() == 3));
    }

    @Test
    @DisplayName("回滚结转-日志状态更新+下期预算回退")
    void rollbackCarryforward_updatesLogStatusAndReversesBudget() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardLogDO log = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID).sourceYearMonth(YEAR_MONTH)
                .targetYearMonth(TARGET_YEAR_MONTH)
                .carryforwardAmount(40000L)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();
        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, YEAR_MONTH))
                .thenReturn(Collections.singletonList(log));
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(carryforwardLogMapper.updateById(any())).thenReturn(1);

        BookBudgetDO targetBudget = buildBudget(new BigDecimal("400"), BigDecimal.ZERO);
        targetBudget.setCarryforwardAmount(40000L);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(targetBudget);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);

        carryforwardService.rollbackCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        // Verify log status changed to ROLLED_BACK
        verify(carryforwardLogMapper).updateById(argThat(l ->
                l.getStatus() == RecordConstant.CARRYFORWARD_LOG_ROLLED_BACK));
        // Verify budget carryforwardAmount = 0
        verify(bookBudgetMapper).updateById(argThat(b ->
                b.getCarryforwardAmount() == 0L));
        // Verify Redis key deleted
        verify(redisUtil).del(contains(RedisKey.CARRYFORWARD_EXECUTED_PREFIX));
    }

    @Test
    @DisplayName("回滚结转-目标期间已有记录时拒绝")
    void rollbackCarryforward_rejectedWhenTargetHasRecords() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardLogDO log = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID).sourceYearMonth(YEAR_MONTH)
                .targetYearMonth(TARGET_YEAR_MONTH)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();
        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, YEAR_MONTH))
                .thenReturn(Collections.singletonList(log));
        // Target period has records
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.rollbackCarryforward(BOOK_ID, YEAR_MONTH, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_ROLLBACK_NOT_ALLOWED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("重复执行结转-幂等拒绝")
    void executeCarryforward_idempotentRejectWhenAlreadyExecuted() {
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_ALREADY_EXECUTED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("结转锁获取失败-抛出CARRYFORWARD_LOCK_FAILED")
    void executeCarryforward_lockFailed_throwsError() {
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_LOCK_FAILED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("无规则时使用账本默认规则FULL")
    void executeCarryforward_fallbackToBookDefaultRule() {
        String cfExecutedKey = RedisKey.CARRYFORWARD_EXECUTED_PREFIX + BOOK_ID + ":" + YEAR_MONTH;
        when(redisUtil.hasKey(cfExecutedKey)).thenReturn(false);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        // No rule found
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(null);

        // Book with default rule FULL
        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setCarryforwardDefaultRule(RecordConstant.CARRYFORWARD_RULE_FULL);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        BookBudgetDO currentBudget = buildBudget(new BigDecimal("1000"), new BigDecimal("600"));
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(currentBudget);
        when(budgetService.getBudget(BOOK_ID, TARGET_YEAR_MONTH)).thenReturn(null);
        when(bookBudgetMapper.insert(any(BookBudgetDO.class))).thenReturn(1);
        when(carryforwardLogMapper.insert(any(BudgetCarryforwardLogDO.class))).thenReturn(1);
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        long[] result = carryforwardService.executeCarryforward(BOOK_ID, YEAR_MONTH, USER_ID);

        assertEquals(40000, result[0]); // FULL: remaining=400, rate=1.0 -> 40000 cents
    }

    @Test
    @DisplayName("保存规则-版本号自动递增")
    void saveRule_versionAutoIncrement() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));

        // Current rule with version 2
        BudgetCarryforwardRuleDO currentRule = buildRule(RecordConstant.CARRYFORWARD_RULE_FULL,
                BigDecimal.ONE, null, 0, RecordConstant.OVERSPENT_CARRY_DEBT);
        currentRule.setRuleVersion(2);
        when(carryforwardRuleMapper.selectActiveRule(BOOK_ID, YEAR_MONTH)).thenReturn(currentRule);
        when(carryforwardRuleMapper.insert(any(BudgetCarryforwardRuleDO.class))).thenReturn(1);

        BudgetCarryforwardRuleDO result = carryforwardService.saveRule(BOOK_ID, YEAR_MONTH,
                RecordConstant.CARRYFORWARD_RULE_PARTIAL, new BigDecimal("0.5"),
                null, null, 0, RecordConstant.OVERSPENT_CARRY_DEBT, null, USER_ID);

        // Verify version incremented to 3
        verify(carryforwardRuleMapper).insert(argThat(rule -> rule.getRuleVersion() == 3));
        assertNotNull(result);
    }

    @Test
    @DisplayName("保存规则-无效结转比例拒绝")
    void saveRule_invalidRate_rejected() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));

        // rate=1.5 > 1 -> invalid
        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.saveRule(BOOK_ID, YEAR_MONTH,
                        RecordConstant.CARRYFORWARD_RULE_PARTIAL, new BigDecimal("1.5"),
                        null, null, null, null, null, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_RATE_INVALID.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    // ========== Helper Methods ==========

    private BudgetCarryforwardRuleDO buildRule(String ruleType, BigDecimal rate,
                                                 Long maxAmount, Integer includePending,
                                                 String overspentMode) {
        return BudgetCarryforwardRuleDO.builder()
                .id(1L)
                .bookId(BOOK_ID)
                .yearMonth(YEAR_MONTH)
                .ruleVersion(1)
                .ruleType(ruleType)
                .carryforwardRate(rate)
                .maxCarryforwardAmount(maxAmount)
                .includePending(includePending)
                .overspentMode(overspentMode)
                .build();
    }

    private BookBudgetDO buildBudget(BigDecimal budgetAmount, BigDecimal usedAmount) {
        return BookBudgetDO.builder()
                .id(1L)
                .bookId(BOOK_ID)
                .yearMonth(YEAR_MONTH)
                .budgetAmount(budgetAmount)
                .usedAmount(usedAmount)
                .warnThreshold(80)
                .status(0)
                .build();
    }
}
