package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 月结-结转-快照集成测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("月结结转集成测试")
class MonthlyClosingCarryoverIntegrationTest {

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
    private SharedBookAuditLogService auditLogService;

    @Mock
    private BudgetCarryoverService budgetCarryoverService;

    @Mock
    private MemberResponsibilitySnapshotService memberResponsibilitySnapshotService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 1;
    private static final Integer USER_ID = 100;
    private static final String YEAR_MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(monthlyClosingService, "baseMapper", monthlyClosingMapper);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("月结 → 触发结转和快照生成")
    void testCloseMonth_triggersCarryoverAndSnapshot() {
        setupSuccessfulClose();
        BookBudgetDO budget = BookBudgetDO.builder()
                .budgetAmount(new BigDecimal("5000")).usedAmount(new BigDecimal("3000")).build();
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(budget);

        monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test");

        verify(memberResponsibilitySnapshotService).generateSnapshots(eq(BOOK_ID), eq(YEAR_MONTH), any());
        verify(budgetCarryoverService).computeAndApplyCarryover(
                eq(BOOK_ID), eq(YEAR_MONTH), any(),
                eq(new BigDecimal("5000")), eq(new BigDecimal("3000")));
    }

    @Test
    @DisplayName("月结无预算 → 跳过结转, 但快照仍生成")
    void testCloseMonth_noBudget_skipsCarryover() {
        setupSuccessfulClose();
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(null);

        monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test");

        verify(memberResponsibilitySnapshotService).generateSnapshots(eq(BOOK_ID), eq(YEAR_MONTH), any());
        verify(budgetCarryoverService, never()).computeAndApplyCarryover(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("结转异常 → 事务回滚")
    void testCloseMonth_carryoverFailure() {
        setupSuccessfulClose();
        BookBudgetDO budget = BookBudgetDO.builder()
                .budgetAmount(new BigDecimal("5000")).usedAmount(new BigDecimal("3000")).build();
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(budget);
        doThrow(new RuntimeException("carryover error"))
                .when(budgetCarryoverService).computeAndApplyCarryover(any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));
    }

    @Test
    @DisplayName("快照生成异常 → 事务回滚")
    void testCloseMonth_snapshotFailure() {
        setupSuccessfulClose();
        doThrow(new RuntimeException("snapshot error"))
                .when(memberResponsibilitySnapshotService).generateSnapshots(any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test"));
    }

    @Test
    @DisplayName("端到端: 月结成功 → Redis标记+审计日志+快照+结转")
    void testCloseMonth_endToEnd() {
        setupSuccessfulClose();
        when(budgetService.getBudget(BOOK_ID, YEAR_MONTH)).thenReturn(null);

        monthlyClosingService.closeMonth(BOOK_ID, USER_ID, YEAR_MONTH, "test close");

        // Redis标记设置
        verify(redisUtil).set(contains("monthly_closing:"), eq(1));
        // 审计日志
        verify(auditLogService).log(eq(BOOK_ID), eq(USER_ID), eq("MONTHLY_CLOSE"),
                eq("CLOSING"), any(), anyString());
        // 快照生成
        verify(memberResponsibilitySnapshotService).generateSnapshots(eq(BOOK_ID), eq(YEAR_MONTH), any());
    }

    private void setupSuccessfulClose() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
    }
}
