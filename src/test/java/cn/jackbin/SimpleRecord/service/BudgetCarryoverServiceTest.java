package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryoverMapper;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryoverRuleMapper;
import cn.jackbin.SimpleRecord.service.impl.BudgetCarryoverServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 预算结转服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预算结转服务测试")
class BudgetCarryoverServiceTest {

    @Spy
    @InjectMocks
    private BudgetCarryoverServiceImpl budgetCarryoverService;

    @Mock
    private BudgetCarryoverMapper budgetCarryoverMapper;

    @Mock
    private BudgetCarryoverRuleMapper budgetCarryoverRuleMapper;

    @Mock
    private BudgetService budgetService;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 1;
    private static final Integer USER_ID = 100;
    private static final String YEAR_MONTH = "2026-06";
    private static final Long CLOSING_ID = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(budgetCarryoverService, "baseMapper", budgetCarryoverMapper);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("FULL规则: 预算5000已用3000 → 结转2000")
    void testFullCarryover() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_FULL, 100, null, 0, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("3000"));

        assertNotNull(result);
        assertEquals(new BigDecimal("2000"), result.getRawCarryover());
        assertEquals(new BigDecimal("2000"), result.getAppliedCarryover());
        assertEquals(0, result.getOverspendCarryover().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("PERCENTAGE规则: 50%结转, 预算5000已用3000 → 结转1000")
    void testPercentageCarryover() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_PERCENTAGE, 50, null, 0, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("3000"));

        assertEquals(0, new BigDecimal("1000.00").compareTo(result.getAppliedCarryover()));
    }

    @Test
    @DisplayName("CAPPED规则: 上限500, 预算5000已用3000 → 结转500(非2000)")
    void testCappedCarryover_capExceeded() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_CAPPED, 100, new BigDecimal("500"), 0, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("3000"));

        assertEquals(0, new BigDecimal("500").compareTo(result.getAppliedCarryover()));
    }

    @Test
    @DisplayName("CAPPED规则: 上限5000, 预算5000已用3000 → 结转2000(未超限)")
    void testCappedCarryover_underCap() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_CAPPED, 100, new BigDecimal("5000"), 0, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("3000"));

        assertEquals(new BigDecimal("2000"), result.getAppliedCarryover());
    }

    @Test
    @DisplayName("超支结转开启: 预算5000已用6000 → 超支-1000结转")
    void testOverspendCarryover_allowed() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_FULL, 100, null, 1, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("6000"));

        assertEquals(0, result.getAppliedCarryover().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("-1000").compareTo(result.getOverspendCarryover()));
    }

    @Test
    @DisplayName("超支结转关闭: 预算5000已用6000 → 不结转超支")
    void testOverspendCarryover_blocked() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_FULL, 100, null, 0, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("6000"));

        assertEquals(0, result.getAppliedCarryover().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getOverspendCarryover().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("RESERVE策略: 预算5000已用2000待审核1000 → 原始结余2000")
    void testPendingReserve_reservePolicy() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_FULL, 100, null, 0, RecordConstant.PENDING_POLICY_RESERVE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);

        RecordDetailDO pendingRecord = new RecordDetailDO();
        pendingRecord.setAmount(-1000.0);
        when(recordDetailService.list(any(QueryWrapper.class))).thenReturn(Collections.singletonList(pendingRecord));
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("2000"));

        assertEquals(0, new BigDecimal("1000").compareTo(result.getPendingReserve()));
        assertEquals(0, new BigDecimal("2000").compareTo(result.getRawCarryover()));
        assertEquals(0, new BigDecimal("2000").compareTo(result.getAppliedCarryover()));
    }

    @Test
    @DisplayName("IGNORE策略: 预算5000已用2000待审核1000 → 原始结余3000")
    void testPendingReserve_ignorePolicy() {
        doReturn(null).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);
        doReturn(buildRule(RecordConstant.CARRYOVER_TYPE_FULL, 100, null, 0, RecordConstant.PENDING_POLICY_IGNORE))
                .when(budgetCarryoverService).getActiveRule(BOOK_ID, YEAR_MONTH);
        when(budgetService.getBudget(eq(BOOK_ID), eq("2026-07"))).thenReturn(null);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("2000"));

        assertEquals(0, result.getPendingReserve().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("3000").compareTo(result.getAppliedCarryover()));
    }

    @Test
    @DisplayName("幂等: 重复结转 → 返回已有记录不重复计算")
    void testIdempotent_duplicateCarryover() {
        BudgetCarryoverDO existing = BudgetCarryoverDO.builder()
                .id(1L).bookId(BOOK_ID).sourceYearMonth(YEAR_MONTH).appliedCarryover(new BigDecimal("2000")).build();
        doReturn(existing).when(budgetCarryoverService).getCarryover(BOOK_ID, YEAR_MONTH);

        BudgetCarryoverDO result = budgetCarryoverService.computeAndApplyCarryover(
                BOOK_ID, YEAR_MONTH, CLOSING_ID, new BigDecimal("5000"), new BigDecimal("3000"));

        assertSame(existing, result);
        verify(budgetCarryoverMapper, never()).insert(any());
    }

    @Test
    @DisplayName("规则版本: 新规则覆盖旧规则")
    void testRuleVersioning() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));

        BudgetCarryoverRuleDO oldRule = buildRule(RecordConstant.CARRYOVER_TYPE_FULL, 100, null, 0, RecordConstant.PENDING_POLICY_IGNORE);
        oldRule.setVersion(1);
        oldRule.setStatus(0);
        when(budgetCarryoverRuleMapper.selectOne(any(QueryWrapper.class))).thenReturn(oldRule);
        when(budgetCarryoverRuleMapper.updateById(any())).thenReturn(1);
        when(budgetCarryoverRuleMapper.insert(any())).thenReturn(1);

        BudgetCarryoverRuleDO result = budgetCarryoverService.setCarryoverRule(
                BOOK_ID, USER_ID, RecordConstant.CARRYOVER_TYPE_PERCENTAGE, 50, null,
                false, RecordConstant.PENDING_POLICY_IGNORE, "2026-07");

        assertEquals(2, result.getVersion());
        verify(budgetCarryoverRuleMapper).updateById(argThat(r -> r.getStatus() == 1));
    }

    @Test
    @DisplayName("PERCENTAGE规则: 百分比超范围 → CARRYOVER_INVALID_PERCENT")
    void testInvalidPercent() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> budgetCarryoverService.setCarryoverRule(
                        BOOK_ID, USER_ID, RecordConstant.CARRYOVER_TYPE_PERCENTAGE, 150, null,
                        false, RecordConstant.PENDING_POLICY_IGNORE, "2026-07"));
        assertEquals(CodeMsg.CARRYOVER_INVALID_PERCENT.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("有效预算: 原始5000 + 上月结转2000 = 7000")
    void testEffectiveBudget_includesCarryover() {
        BookBudgetDO budget = BookBudgetDO.builder()
                .budgetAmount(new BigDecimal("5000")).build();
        when(budgetService.getBudget(BOOK_ID, "2026-07")).thenReturn(budget);

        BudgetCarryoverDO carryover = BudgetCarryoverDO.builder()
                .appliedCarryover(new BigDecimal("2000"))
                .overspendCarryover(BigDecimal.ZERO).build();
        doReturn(carryover).when(budgetCarryoverService).getCarryover(BOOK_ID, "2026-06");

        BigDecimal effective = budgetCarryoverService.getEffectiveBudget(BOOK_ID, "2026-07");

        assertEquals(0, new BigDecimal("7000").compareTo(effective));
    }

    private BudgetCarryoverRuleDO buildRule(String type, int percent, BigDecimal cap,
                                             int carryOverspend, String pendingPolicy) {
        return BudgetCarryoverRuleDO.builder()
                .id(1L).bookId(BOOK_ID).version(1).carryoverType(type)
                .carryoverPercent(percent).capAmount(cap).carryOverspend(carryOverspend)
                .pendingRecordPolicy(pendingPolicy).effectiveFrom("2026-01").createdBy(USER_ID)
                .status(0).build();
    }
}
