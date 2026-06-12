package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
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
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 增强月结服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("增强月结服务测试")
class MonthlyClosingEnhancedTest {

    @Spy
    @InjectMocks
    private MonthlyClosingServiceImpl monthlyClosingService;

    @Mock
    private MonthlyClosingMapper monthlyClosingMapper;

    @Mock
    private BookBudgetMapper bookBudgetMapper;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private RecordBookService recordBookService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private BudgetCarryforwardService budgetCarryforwardService;

    @Mock
    private MemberSettlementService memberSettlementService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 100;
    private static final Integer USER_ID = 1;
    private static final String YEAR_MONTH = "2026-05";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(monthlyClosingService, "baseMapper", monthlyClosingMapper);
    }

    @Test
    @DisplayName("月结-启用结转-执行结转和快照")
    void closeMonth_carryforwardEnabled_executesCarryforwardAndSnapshots() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);

        RecordDetailDO r1 = new RecordDetailDO();
        r1.setAmount(1000.0);
        RecordDetailDO r2 = new RecordDetailDO();
        r2.setAmount(-600.0);
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(r1, r2));
        when(monthlyClosingService.save(any(MonthlyClosingDO.class))).thenReturn(true);

        // Book with carryforward enabled
        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setCarryforwardEnabled(1);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);
        when(budgetCarryforwardService.executeCarryforward(eq(BOOK_ID), eq(YEAR_MONTH), eq(USER_ID)))
                .thenReturn(new long[]{40000, 0, 0, 1});

        monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test");

        verify(budgetCarryforwardService).executeCarryforward(eq(BOOK_ID), eq(YEAR_MONTH), eq(USER_ID));
        verify(memberSettlementService).captureSnapshots(eq(BOOK_ID), eq(YEAR_MONTH));
    }

    @Test
    @DisplayName("月结-未启用结转-跳过结转")
    void closeMonth_carryforwardDisabled_skipsCarryforward() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(monthlyClosingService.save(any(MonthlyClosingDO.class))).thenReturn(true);

        // Book with carryforward disabled (null)
        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setCarryforwardEnabled(null);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test");

        verify(budgetCarryforwardService, never()).executeCarryforward(anyInt(), anyString(), anyInt());
        // Snapshots should still be captured
        verify(memberSettlementService).captureSnapshots(eq(BOOK_ID), eq(YEAR_MONTH));
    }

    @Test
    @DisplayName("月结-结转失败-不影响月结结果")
    void closeMonth_carryforwardFails_doesNotAffectClosing() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(monthlyClosingService.save(any(MonthlyClosingDO.class))).thenReturn(true);

        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setCarryforwardEnabled(1);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        // Carryforward throws exception
        when(budgetCarryforwardService.executeCarryforward(anyInt(), anyString(), anyInt()))
                .thenThrow(new BusinessException(CodeMsg.CARRYFORWARD_LOCK_FAILED));

        // Month closing should not throw
        assertDoesNotThrow(() -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));

        // Audit log should still be called (closing succeeded)
        verify(auditLogService).log(eq(BOOK_ID), eq(USER_ID), eq("MONTHLY_CLOSE"),
                eq("CLOSING"), any(), anyString());
    }

    @Test
    @DisplayName("月结-快照捕获失败-不影响月结结果")
    void closeMonth_snapshotCaptureFails_doesNotAffectClosing() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(monthlyClosingService.save(any(MonthlyClosingDO.class))).thenReturn(true);

        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setCarryforwardEnabled(0);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        // Snapshot capture throws exception
        doThrow(new RuntimeException("Snapshot failed"))
                .when(memberSettlementService).captureSnapshots(anyInt(), anyString());

        // Month closing should not throw
        assertDoesNotThrow(() -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));

        verify(auditLogService).log(eq(BOOK_ID), eq(USER_ID), eq("MONTHLY_CLOSE"),
                eq("CLOSING"), any(), anyString());
    }

    @Test
    @DisplayName("月结-向后兼容-结果结构不变")
    void closeMonth_backwardCompatibility_resultStructureUnchanged() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);

        RecordDetailDO r1 = new RecordDetailDO();
        r1.setAmount(2000.0);
        RecordDetailDO r2 = new RecordDetailDO();
        r2.setAmount(-1500.0);
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(r1, r2));
        when(monthlyClosingService.save(any(MonthlyClosingDO.class))).thenReturn(true);

        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setCarryforwardEnabled(0);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        // Should complete without error - backward compatible behavior
        assertDoesNotThrow(() -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));

        // Verify basic closing actions still happen
        verify(redisUtil).set(contains(RedisKey.MONTHLY_CLOSING_PREFIX), eq(1));
        verify(redisUtil).del(contains(RedisKey.BUDGET_USED_PREFIX));
    }

    @Test
    @DisplayName("月结-月份已结-抛出错误")
    void closeMonth_monthAlreadyClosed_throwsError() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(RedisKey.MONTHLY_CLOSING_PREFIX + BOOK_ID + ":" + YEAR_MONTH)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));
        assertEquals(CodeMsg.MONTH_ALREADY_CLOSED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("月结-存在待审核记录-抛出错误")
    void closeMonth_pendingRecordsExist_throwsError() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(3);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));
        assertEquals(CodeMsg.PENDING_RECORDS_EXIST.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }
}
