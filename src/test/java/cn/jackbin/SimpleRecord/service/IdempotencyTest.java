package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.DifferenceAdjustmentRecordDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.DifferenceAdjustmentRecordMapper;
import cn.jackbin.SimpleRecord.service.impl.DifferenceAdjustmentServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 幂等机制测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("幂等机制测试")
class IdempotencyTest {

    @Spy
    @InjectMocks
    private DifferenceAdjustmentServiceImpl adjustmentService;

    @Mock
    private DifferenceAdjustmentRecordMapper adjustmentRecordMapper;

    @Mock
    private MonthlyClosingService monthlyClosingService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 100;
    private static final Integer USER_ID = 1;
    private static final String SOURCE_MONTH = "2026-05";
    private static final String TARGET_MONTH = "2026-06";

    @Test
    @DisplayName("Redis层命中-返回缓存结果")
    void redisLayerHit_returnsCachedResult() {
        String idempKey = "idemp-hit-001";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;

        DifferenceAdjustmentRecordDO existing = DifferenceAdjustmentRecordDO.builder()
                .id(50L).bookId(BOOK_ID).idempotencyKey(idempKey)
                .adjustmentAmount(5000L).build();
        when(redisUtil.get(redisIdempKey)).thenReturn(50L);
        when(adjustmentRecordMapper.selectById(50L)).thenReturn(existing);

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                USER_ID, "test", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(50L, result.getId());
        // No DB operations should happen
        verify(adjustmentRecordMapper, never()).insert(any());
        verify(adjustmentRecordMapper, never()).selectByIdempotencyKey(anyString());
    }

    @Test
    @DisplayName("数据库唯一键保护-返回已有记录")
    void dbUniqueKeyProtection_returnsExisting() {
        String idempKey = "idemp-db-002";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;

        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        DifferenceAdjustmentRecordDO dbExisting = DifferenceAdjustmentRecordDO.builder()
                .id(60L).bookId(BOOK_ID).idempotencyKey(idempKey)
                .adjustmentAmount(3000L).build();
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey)).thenReturn(dbExisting);

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 3000L, null, null,
                USER_ID, "test", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(60L, result.getId());
        verify(adjustmentRecordMapper, never()).insert(any());
        // Redis cache should be set
        verify(redisUtil).set(eq(redisIdempKey), eq(60L), eq(86400L));
    }

    @Test
    @DisplayName("DuplicateKeyException处理-返回已有记录")
    void duplicateKeyException_handled() {
        String idempKey = "idemp-dup-003";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;

        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        // First check: not found; after insert failure: found
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey))
                .thenReturn(null)
                .thenReturn(DifferenceAdjustmentRecordDO.builder().id(70L).build());
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(true);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, TARGET_MONTH)).thenReturn(false);
        when(adjustmentRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        DifferenceAdjustmentRecordDO result = adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 2000L, null, null,
                USER_ID, "test", null, idempKey, USER_ID);

        assertNotNull(result);
        assertEquals(70L, result.getId());
        verify(redisUtil).set(eq(redisIdempKey), eq(70L), eq(86400L));
    }

    @Test
    @DisplayName("首次创建-成功")
    void firstTimeCreate_succeeds() {
        String idempKey = "idemp-new-004";
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
                USER_ID, "test", null, idempKey, USER_ID);

        assertNotNull(result);
        verify(adjustmentRecordMapper).insert(any());
        verify(recordDetailService).save(any(RecordDetailDO.class));
    }

    @Test
    @DisplayName("创建后Redis缓存被设置")
    void redisSetCalledAfterCreate() {
        String idempKey = "idemp-cache-005";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;

        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(adjustmentRecordMapper.selectByIdempotencyKey(idempKey)).thenReturn(null);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, SOURCE_MONTH)).thenReturn(true);
        when(monthlyClosingService.isMonthClosed(BOOK_ID, TARGET_MONTH)).thenReturn(false);
        when(adjustmentRecordMapper.insert(any())).thenReturn(1);
        when(recordDetailService.save(any(RecordDetailDO.class))).thenReturn(true);
        when(adjustmentRecordMapper.updateById(any())).thenReturn(1);

        adjustmentService.createAdjustment(
                BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                USER_ID, "test", null, idempKey, USER_ID);

        // Verify Redis cache was set with the idempotency key
        verify(redisUtil).set(eq(redisIdempKey), any(), eq(86400L));
    }

    @Test
    @DisplayName("零金额-拒绝")
    void zeroAmount_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.createAdjustment(
                        BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                        RecordConstant.ADJUST_REVERSAL_DIFF, 0L, null, null,
                        USER_ID, "test", null, "idemp-zero-006", USER_ID));
        assertEquals(CodeMsg.ADJUSTMENT_AMOUNT_ZERO.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("源期间未关闭-拒绝")
    void sourceNotClosed_rejected() {
        String idempKey = "idemp-src-007";
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
    @DisplayName("锁获取失败-拒绝")
    void lockFailed_rejected() {
        String idempKey = "idemp-lock-008";
        String redisIdempKey = RedisKey.IDEMP_ADJUSTMENT_PREFIX + idempKey;

        when(redisUtil.get(redisIdempKey)).thenReturn(null);
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adjustmentService.createAdjustment(
                        BOOK_ID, SOURCE_MONTH, TARGET_MONTH, 1L, null,
                        RecordConstant.ADJUST_REVERSAL_DIFF, 5000L, null, null,
                        USER_ID, "test", null, idempKey, USER_ID));
        assertEquals(CodeMsg.ADJUSTMENT_LOCK_FAILED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }
}
