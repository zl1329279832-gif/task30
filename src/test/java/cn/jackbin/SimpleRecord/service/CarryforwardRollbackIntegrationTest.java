package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardLogDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 结转回滚集成测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("结转回滚集成测试")
class CarryforwardRollbackIntegrationTest {

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
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 100;
    private static final Integer USER_ID = 1;
    private static final String SOURCE_MONTH = "2026-05";
    private static final String TARGET_MONTH = "2026-06";

    @Test
    @DisplayName("结转后回滚-预算恢复+日志标记已回滚")
    void rollbackAfterCarryforward_budgetRestored_logsMarked() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        // Two logs: summary + member
        BudgetCarryforwardLogDO summaryLog = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .carryforwardAmount(40000L)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();
        BudgetCarryforwardLogDO memberLog = BudgetCarryforwardLogDO.builder()
                .id(2L).bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .memberUserId(200)
                .carryforwardAmount(0L)
                .usedAmount(30000L)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();
        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH))
                .thenReturn(Arrays.asList(summaryLog, memberLog));
        // No records in target period
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(carryforwardLogMapper.updateById(any())).thenReturn(1);

        BookBudgetDO targetBudget = BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(TARGET_MONTH)
                .budgetAmount(new BigDecimal("400"))
                .usedAmount(BigDecimal.ZERO)
                .carryforwardAmount(40000L)
                .sourceYearMonth(SOURCE_MONTH)
                .ruleVersion(1)
                .build();
        when(budgetService.getBudget(BOOK_ID, TARGET_MONTH)).thenReturn(targetBudget);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);

        carryforwardService.rollbackCarryforward(BOOK_ID, SOURCE_MONTH, USER_ID);

        // Verify both logs marked as ROLLED_BACK
        verify(carryforwardLogMapper, times(2)).updateById(argThat(log ->
                log.getStatus() == RecordConstant.CARRYFORWARD_LOG_ROLLED_BACK));
        // Verify budget carryforward amount = 0
        verify(bookBudgetMapper).updateById(argThat(b ->
                b.getCarryforwardAmount() == 0L));
        // Verify Redis key deleted
        verify(redisUtil).del(contains(RedisKey.CARRYFORWARD_EXECUTED_PREFIX));
        // Verify audit log
        verify(auditLogService).log(eq(BOOK_ID), eq(USER_ID),
                eq(RecordConstant.ACTION_CARRYFORWARD_ROLLBACK), eq("BUDGET"), isNull(), anyString());
    }

    @Test
    @DisplayName("回滚-目标期间已有记录-拒绝")
    void rollbackWithTargetRecords_rejected() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        BudgetCarryforwardLogDO log = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();
        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH))
                .thenReturn(Collections.singletonList(log));
        // Target period has records
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(3);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.rollbackCarryforward(BOOK_ID, SOURCE_MONTH, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_ROLLBACK_NOT_ALLOWED.getRetCode(),
                ex.getCodeMsg().getRetCode());

        // Verify no logs were updated
        verify(carryforwardLogMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("回滚-无日志-抛出RULE_NOT_FOUND")
    void rollbackNoLogs_throwsRuleNotFound() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH))
                .thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.rollbackCarryforward(BOOK_ID, SOURCE_MONTH, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_RULE_NOT_FOUND.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("回滚-锁获取失败-抛出错误")
    void rollbackLockFailed_throwsError() {
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carryforwardService.rollbackCarryforward(BOOK_ID, SOURCE_MONTH, USER_ID));
        assertEquals(CodeMsg.CARRYFORWARD_LOCK_FAILED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }
}
