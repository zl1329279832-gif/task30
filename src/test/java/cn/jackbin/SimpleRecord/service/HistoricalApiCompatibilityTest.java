package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.mapper.MonthlyClosingMapper;
import cn.jackbin.SimpleRecord.service.impl.MonthlyClosingServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
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
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 历史API兼容性测试
 * 验证新增字段为nullable且不影响已有行为
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("历史API兼容性测试")
class HistoricalApiCompatibilityTest {

    @Spy
    @InjectMocks
    private MonthlyClosingServiceImpl monthlyClosingService;

    @Mock
    private MonthlyClosingMapper monthlyClosingMapper;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(monthlyClosingService, "baseMapper", monthlyClosingMapper);
    }

    @Test
    @DisplayName("MonthlyClosingDO-新字段可为null")
    void monthlyClosingDO_newFieldsNullable() {
        MonthlyClosingDO closing = MonthlyClosingDO.builder()
                .id(1L)
                .bookId(100)
                .yearMonth("2026-05")
                .closedBy(1)
                .closedTime(new Date())
                .totalIncome(new BigDecimal("5000"))
                .totalExpend(new BigDecimal("3000"))
                .remark("test")
                .status(0)
                .build();
        // Don't set new fields: snapshotVersion, carryforwardExecuted, carryforwardTotal, etc.

        assertNull(closing.getSnapshotVersion());
        assertNull(closing.getCarryforwardExecuted());
        assertNull(closing.getCarryforwardTotal());
        assertNull(closing.getOverspentTotal());
        assertNull(closing.getPendingImpactTotal());
        assertNull(closing.getLastRecalcTime());

        // Existing fields should work fine
        assertEquals(1L, closing.getId());
        assertEquals(100, closing.getBookId());
        assertEquals("2026-05", closing.getYearMonth());
        assertEquals(0, closing.getTotalIncome().compareTo(new BigDecimal("5000")));
    }

    @Test
    @DisplayName("RecordBookDO-新字段默认值为null/0")
    void recordBookDO_newFieldsDefaultValues() {
        RecordBookDO book = new RecordBookDO();
        book.setId(1L);
        book.setUserId(100);
        book.setName("Test Book");
        book.setBookType(RecordConstant.BOOK_TYPE_SHARED);
        book.setOwnerUserId(100);
        // Don't set new fields: carryforwardEnabled, carryforwardDefaultRule, carryforwardExpireMonths

        assertNull(book.getCarryforwardEnabled());
        assertNull(book.getCarryforwardDefaultRule());
        assertNull(book.getCarryforwardExpireMonths());

        // Existing fields work
        assertEquals("Test Book", book.getName());
        assertEquals(RecordConstant.BOOK_TYPE_SHARED, book.getBookType());

        // Simulate code that checks carryforwardEnabled
        boolean carryforwardEnabled = book.getCarryforwardEnabled() != null && book.getCarryforwardEnabled() == 1;
        assertFalse(carryforwardEnabled, "When carryforwardEnabled is null, should be treated as disabled");
    }

    @Test
    @DisplayName("RecordDetailDO-新字段可为null")
    void recordDetailDO_newFieldsNullable() {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setUserId(100);
        record.setRecordBookId(1);
        record.setAmount(-500.0);
        record.setReviewStatus(RecordConstant.REVIEW_POSTED);
        record.setRecordType(1);
        record.setRecordAccountId(1);
        record.setOccurTime(new Date());
        // Don't set new fields: adjustmentRecordType, sourceYearMonth, idempotencyKey

        assertNull(record.getAdjustmentRecordType());
        assertNull(record.getSourceYearMonth());
        assertNull(record.getIdempotencyKey());

        // Existing fields work
        assertEquals(1L, record.getId());
        assertEquals(-500.0, record.getAmount());
        assertEquals(RecordConstant.REVIEW_POSTED, record.getReviewStatus());
    }

    @Test
    @DisplayName("BookBudgetDO-新字段默认值为null/0")
    void bookBudgetDO_newFieldsDefaultValues() {
        BookBudgetDO budget = BookBudgetDO.builder()
                .id(1L)
                .bookId(100)
                .yearMonth("2026-05")
                .budgetAmount(new BigDecimal("5000"))
                .usedAmount(new BigDecimal("3000"))
                .warnThreshold(80)
                .status(0)
                .build();
        // Don't set new fields: carryforwardAmount, sourceYearMonth, ruleVersion

        assertNull(budget.getCarryforwardAmount());
        assertNull(budget.getSourceYearMonth());
        assertNull(budget.getRuleVersion());

        // Existing fields work
        assertEquals(0, budget.getBudgetAmount().compareTo(new BigDecimal("5000")));
        assertEquals(0, budget.getUsedAmount().compareTo(new BigDecimal("3000")));

        // Simulate code that reads carryforwardAmount
        long cfAmount = budget.getCarryforwardAmount() != null ? budget.getCarryforwardAmount() : 0L;
        assertEquals(0L, cfAmount, "When carryforwardAmount is null, should default to 0");
    }

    @Test
    @DisplayName("ReversalRequestDO-新字段可为null")
    void reversalRequestDO_newFieldsNullable() {
        ReversalRequestDO request = ReversalRequestDO.builder()
                .id(1L)
                .bookId(100)
                .originalRecordId(42L)
                .requesterId(200)
                .requestReason("test")
                .reviewStatus(RecordConstant.REVERSAL_PENDING)
                .status(0)
                .build();
        // Don't set new fields: crossPeriod, sourceYearMonth

        assertNull(request.getCrossPeriod());
        assertNull(request.getSourceYearMonth());
        assertNull(request.getAdjustmentRecordId());

        // Existing fields work
        assertEquals(42L, request.getOriginalRecordId());
        assertEquals(RecordConstant.REVERSAL_PENDING, request.getReviewStatus());
    }

    @Test
    @DisplayName("MonthlyClosingService-新依赖为null时仍正常工作")
    void monthlyClosingService_worksWithoutNewDependencies() {
        // Simulate scenario where budgetCarryforwardService and memberSettlementService are null
        ReflectionTestUtils.setField(monthlyClosingService, "budgetCarryforwardService", null);
        ReflectionTestUtils.setField(monthlyClosingService, "memberSettlementService", null);

        lenient().doNothing().when(sharedBookService).checkPermission(anyInt(), anyInt(), anyString());
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        lenient().when(redisUtil.hasKey(anyString())).thenReturn(false);
        lenient().when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        lenient().when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        lenient().when(recordDetailService.list(any(QueryWrapper.class))).thenReturn(java.util.Collections.emptyList());
        lenient().when(monthlyClosingService.save(any(MonthlyClosingDO.class))).thenReturn(true);

        // Should not throw even with null new dependencies
        assertDoesNotThrow(() ->
                monthlyClosingService.closeMonth(100, 1, "2026-05", "compatibility test"));

        // Basic closing operations should still happen
        verify(redisUtil).set(contains("monthly_closing:"), eq(1));
    }
}
