package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.*;
import cn.jackbin.SimpleRecord.service.impl.DifferenceAdjustmentServiceImpl;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 差额调整记录服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("差额调整记录服务测试")
class DifferenceAdjustmentServiceTest {

    @Spy
    @InjectMocks
    private DifferenceAdjustmentServiceImpl adjustmentService;

    @Mock
    private DifferenceAdjustmentRecordMapper adjustmentRecordMapper;

    @Mock
    private MonthlyClosingRecalculationMapper recalculationMapper;

    @Mock
    private MonthlyClosingMapper monthlyClosingMapper;

    @Mock
    private BookBudgetMapper bookBudgetMapper;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private MonthlyClosingService monthlyClosingService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private static final Integer BOOK_ID = 100;
    private static final Integer USER_ID = 1;
    private static final String SOURCE_MONTH = "2026-05";
    private static final String TARGET_MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("创建调整-已关闭期间冲销差额-成功创建调整记录")
    void createAdjustment_closedPeriodReversal_createsAdjustmentRecord() {
        String idempKey = "test-idemp-key-001";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;
        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey)).thenReturn(null);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(true);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, TARGET_MONTH)).thenReturn(false);
        when(adjustmentRecordMapper.insert(any())).thenReturn(1);
        when(recordDetailService.save(any(RecordDetailDO.class))).thenReturn(true);
        when(adjustmentRecordMapper.updateById(any())).thenReturn(1);

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                USER_ID, "冲销差额", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(RecordConstant.ADJUST_REVERSAL_DIFF, result.getAdjustmentType());
        assertEquals(5000L, result.getAdjustmentAmount());
        verify(adjustmentRecordMapper).insert(any());
    }

    @Test
    @DisplayName("创建调整-补充审核-关联原始记录")
    void createAdjustment_supplementAudit_linksOriginalRecord() {
        String idempKey = "test-idemp-key-002";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;
        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey)).thenReturn(null);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(true);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, TARGET_MONTH)).thenReturn(false);
        when(adjustmentRecordMapper.insert(any())).thenReturn(1);
        when(recordDetailService.save(any(RecordDetailDO.class))).thenReturn(true);
        when(adjustmentRecordMapper.updateById(any())).thenReturn(1);

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 42L, 10L,
                RecordConstant.ADJUST_SUPPLEMENT, 3000L, 1L, 2L,
                USER_ID, "补充审核记录", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(42L, result.getOriginalRecordId());
        assertEquals(10L, result.getReversalRequestId());
    }

    @Test
    @DisplayName("创建调整-重复幂等键返回已有记录(Redis命中)")
    void createAdjustment_duplicateIdempotencyKey_returnsExisting() {
        String idempKey = "test-idemp-key-003";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;

        DifferenceAdjustmentRecordDO existing = DifferenceAdjustmentRecordDO.builder()
                .id(99L).bookId(BOOK_ID).idempotencyKey(idempKey).build();
        when(redisUtil.get(redisIdempKey)).thenReturn(99L);
        when(adjustmentRecordMapper.selectById(99L)).thenReturn(existing);

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                USER_ID, "test", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(99L, result.getId());
        verify(adjustmentRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建调整-数据库唯一键冲突保护")
    void createAdjustment_dbUniqueKeyProtection() {
        String idempKey = "test-idemp-key-004";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;
        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey))
                .thenReturn(null) // First check: not found
                .thenReturn(DifferenceAdjustmentRecordDO.builder().id(88L).build()); // After DuplicateKeyException
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(true);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, TARGET_MONTH)).thenReturn(false);
        when(adjustmentRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                USER_ID, "test", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(88L, result.getId());
        verify(redisUtil).set(eq(redisIdempKey), eq(88L), eq(86400L));
    }

    @Test
    @DisplayName("创建调整-源期间未关闭-拒绝")
    void createAdjustment_sourceNotClosed_rejects() {
        String idempKey = "test-idemp-key-005";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;
        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey)).thenReturn(null);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.createAdjustment(
                        BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                        RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                        USER_ID, "test", null, idempKey, USER_ID));
        assertEquals(CodeMsg.ADJUSTMENT_SOURCE_NOT_CLOSED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("创建调整-目标期间已关闭-拒绝")
    void createAdjustment_targetClosed_rejects() {
        String idempKey = "test-idemp-key-006";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;
        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey)).thenReturn(null);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(true);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, TARGET_MONTH)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.createAdjustment(
                        BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                        RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                        USER_ID, "test", null, idempKey, USER_ID));
        assertEquals(CodeMsg.ADJUSTMENT_TARGET_CLOSED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("创建调整-零金额-拒绝")
    void createAdjustment_zeroAmount_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.createAdjustment(
                        BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                        RecordConstant.ADJUST_REVERSAL_DIFF, 0L, null, null,
                        USER_ID, "test", null, "idemp-zero", USER_ID));
        assertEquals(CodeMsg.ADJUSTMENT_AMOUNT_ZERO.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("应用调整-更新目标期间预算")
    void applyAdjustment_updatesTargetPeriodBudget() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        DifferenceAdjustmentRecordDO adjustment = DifferenceAdjustmentRecordDO.builder()
                .id(1L).bookId(BOOK_ID).targetYearMonth(TARGET_MONTH)
                .adjustmentAmount(5000L).reviewStatus(RecordConstant.REVIEW_POSTED)
                .createdBy(USER_ID).build();
        when(adjustmentRecordMapper.selectById(1L)).thenReturn(adjustment);

        BookBudgetDO budget = BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(TARGET_MONTH)
                .budgetAmount(new BigDecimal("5000"))
                .usedAmount(new BigDecimal("3000"))
                .build();
        when(budgetService.getBudget(BOOK_ID, TARGET_MONTH)).thenReturn(budget);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);
        when(redisUtil.hasKey(anyString())).thenReturn(false);

        adjustmentService.applyAdjustment(1L);

        // Verify budget usedAmount updated: 3000 + 50 = 3050
        verify(bookBudgetMapper).updateById(argThat(b ->
                b.getUsedAmount().compareTo(new BigDecimal("3050")) == 0));
    }

    @Test
    @DisplayName("应用调整-Redis缓存存在时同步incrBy")
    void applyAdjustment_redisCacheExists_incrBy() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        DifferenceAdjustmentRecordDO adjustment = DifferenceAdjustmentRecordDO.builder()
                .id(1L).bookId(BOOK_ID).targetYearMonth(TARGET_MONTH)
                .adjustmentAmount(5000L).reviewStatus(RecordConstant.REVIEW_POSTED)
                .createdBy(USER_ID).build();
        when(adjustmentRecordMapper.selectById(1L)).thenReturn(adjustment);

        BookBudgetDO budget = BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(TARGET_MONTH)
                .budgetAmount(new BigDecimal("5000"))
                .usedAmount(new BigDecimal("3000"))
                .build();
        when(budgetService.getBudget(BOOK_ID, TARGET_MONTH)).thenReturn(budget);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);
        // Redis key exists
        when(redisUtil.hasKey(anyString())).thenReturn(true);

        adjustmentService.applyAdjustment(1L);

        // Verify Redis increment called with 5000 cents
        verify(valueOperations).increment(anyString(), eq(5000L));
    }

    @Test
    @DisplayName("触发重算-快照版本递增CAS保护")
    void triggerRecalculation_snapshotVersionIncrement_casProtected() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        MonthlyClosingDO closing = MonthlyClosingDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(SOURCE_MONTH)
                .snapshotVersion(1)
                .totalIncome(new BigDecimal("10000"))
                .totalExpend(new BigDecimal("5000"))
                .carryforwardTotal(0L).overspentTotal(0L).pendingImpactTotal(0L)
                .build();
        // monthlyClosingService.getClosing returns the closing
        when(monthlyClosingService.getClosing(BOOK_ID, SOURCE_MONTH)).thenReturn(closing);

        // Return some records with different totals
        RecordDetailDO r1 = new RecordDetailDO();
        r1.setAmount(-6000.0);
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(r1));
        when(adjustmentRecordMapper.sumAdjustmentByTargetPeriod(BOOK_ID, SOURCE_MONTH)).thenReturn(0L);

        // CAS update succeeds
        when(monthlyClosingMapper.updateWithRecalcVersion(
                eq(BOOK_ID), eq(SOURCE_MONTH), eq(1), eq(2),
                any(), any(), anyLong(), anyLong(), anyLong()
        )).thenReturn(1);
        when(recalculationMapper.insert(any())).thenReturn(1);

        MonthlyClosingRecalculationDO result = adjustmentService.triggerRecalculation(
                BOOK_ID, SOURCE_MONTH, USER_ID, "调整记录导致差异");

        assertNotNull(result);
        assertEquals(2, result.getRecalcVersion());
    }

    @Test
    @DisplayName("触发重算-版本冲突-失败")
    void triggerRecalculation_versionConflict_fails() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        MonthlyClosingDO closing = MonthlyClosingDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(SOURCE_MONTH)
                .snapshotVersion(1)
                .totalIncome(new BigDecimal("10000"))
                .totalExpend(new BigDecimal("5000"))
                .carryforwardTotal(0L).overspentTotal(0L).pendingImpactTotal(0L)
                .build();
        when(monthlyClosingService.getClosing(BOOK_ID, SOURCE_MONTH)).thenReturn(closing);

        RecordDetailDO r1 = new RecordDetailDO();
        r1.setAmount(-6000.0);
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(r1));
        when(adjustmentRecordMapper.sumAdjustmentByTargetPeriod(BOOK_ID, SOURCE_MONTH)).thenReturn(0L);

        // CAS update fails (concurrent modification)
        when(monthlyClosingMapper.updateWithRecalcVersion(
                anyInt(), anyString(), anyInt(), anyInt(),
                any(), any(), anyLong(), anyLong(), anyLong()
        )).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.triggerRecalculation(
                        BOOK_ID, SOURCE_MONTH, USER_ID, "test"));
        assertEquals(CodeMsg.RECALC_VERSION_CONFLICT.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("应用调整-记录不存在-抛出错误")
    void applyAdjustment_notFound_throwsError() {
        when(adjustmentRecordMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.applyAdjustment(999L));
        assertEquals(CodeMsg.ADJUSTMENT_NOT_FOUND.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    // ========== Helper Methods ==========

    private DifferenceAdjustmentRecordDO buildAdjustment(Long id, String idempotencyKey) {
        return DifferenceAdjustmentRecordDO.builder()
                .id(id)
                .bookId(BOOK_ID)
                .sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .adjustmentType(RecordConstant.ADJUST_REVERSAL_DIFF)
                .adjustmentAmount(5000L)
                .reviewStatus(RecordConstant.REVIEW_POSTED)
                .idempotencyKey(idempotencyKey)
                .createdBy(USER_ID)
                .build();
    }
}
