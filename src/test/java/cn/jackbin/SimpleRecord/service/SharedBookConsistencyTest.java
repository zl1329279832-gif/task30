package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.RecordDetailBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ReversalRequestMapper;
import cn.jackbin.SimpleRecord.service.impl.RecordReviewServiceImpl;
import cn.jackbin.SimpleRecord.service.impl.ReversalServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 共享账本统计守恒一致性测试
 *
 * 覆盖场景:
 * 1. 待审核记录遇到月结
 * 2. 权限变更后审核旧记录
 * 3. 重复冲正申请
 * 4. 冲正恢复预算统计
 * 5. 多人并发记账和月结
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("共享账本统计守恒一致性测试")
class SharedBookConsistencyTest {

    private static final Integer BOOK_ID = 1;
    private static final Integer REVIEWER_ID = 100;
    private static final Integer CREATOR_ID = 200;
    private static final String YEAR_MONTH = "2026-06";

    private RecordDetailDO buildRecord(Integer userId, int reviewStatus, double amount) {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setUserId(userId);
        record.setRecordBookId(BOOK_ID);
        record.setReviewStatus(reviewStatus);
        record.setAmount(amount);
        record.setRecordType(1);
        record.setRecordAccountId(1);
        try {
            record.setOccurTime(new SimpleDateFormat("yyyy-MM-dd").parse("2026-06-15"));
        } catch (Exception e) {
            record.setOccurTime(new Date());
        }
        return record;
    }

    // ===================== 场景1: 待审核记录遇到月结 =====================

    @Nested
    @DisplayName("待审核记录遇到月结")
    class PendingRecordMeetsMonthlyClosing {

        @InjectMocks
        private RecordReviewServiceImpl recordReviewService;

        @Mock
        private RecordDetailService recordDetailService;

        @Mock
        private SharedBookService sharedBookService;

        @Mock
        private SharedBookAuditLogService auditLogService;

        @Mock
        private BudgetService budgetService;

        @Mock
        private MonthlyClosingService monthlyClosingService;

        @Mock
        private RedisUtil redisUtil;

        @Test
        @DisplayName("月结后审核该月待审核记录 → RECORD_MONTH_CLOSED")
        void testApproveAfterMonthClosed() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -500.0);
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
            assertEquals(CodeMsg.RECORD_MONTH_CLOSED.getRetCode(), ex.getCodeMsg().getRetCode());

            // 验证未执行任何状态更新和预算变更
            verify(recordDetailService, never()).update(any(UpdateWrapper.class));
            verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("月未结时正常审核 → 成功入账并更新预算")
        void testApproveBeforeMonthClosed() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -800.0);
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(false);
            when(sharedBookService.hasPermission(BOOK_ID, CREATOR_ID, RecordConstant.PERM_ENTRY)).thenReturn(true);
            when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

            assertDoesNotThrow(() -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));

            verify(budgetService).atomicIncrementUsed(eq(BOOK_ID), eq(YEAR_MONTH), eq(BigDecimal.valueOf(800.0)));
            verify(auditLogService).log(eq(BOOK_ID), eq(REVIEWER_ID), eq("RECORD_REVIEW"), eq("RECORD"), eq(1L), anyString());
        }

        @Test
        @DisplayName("月结后驳回待审核记录 → 无影响，正常驳回")
        void testRejectAfterMonthClosed() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

            assertDoesNotThrow(() -> recordReviewService.rejectRecord(BOOK_ID, REVIEWER_ID, 1L, "不合规"));

            // 驳回不影响预算
            verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
        }
    }

    // ===================== 场景2: 权限变更后审核旧记录 =====================

    @Nested
    @DisplayName("权限变更后审核旧记录")
    class PermissionChangeAndOldRecordReview {

        @InjectMocks
        private RecordReviewServiceImpl recordReviewService;

        @Mock
        private RecordDetailService recordDetailService;

        @Mock
        private SharedBookService sharedBookService;

        @Mock
        private SharedBookAuditLogService auditLogService;

        @Mock
        private BudgetService budgetService;

        @Mock
        private MonthlyClosingService monthlyClosingService;

        @Mock
        private RedisUtil redisUtil;

        @Test
        @DisplayName("创建者权限被降为view后审核其旧记录 → RECORD_CREATOR_PERMISSION_REVOKED")
        void testApproveAfterCreatorDemotedToView() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -300.0);
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(false);
            // 创建者已无entry权限
            when(sharedBookService.hasPermission(BOOK_ID, CREATOR_ID, RecordConstant.PERM_ENTRY)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
            assertEquals(CodeMsg.RECORD_CREATOR_PERMISSION_REVOKED.getRetCode(), ex.getCodeMsg().getRetCode());

            // 确保不会产生入账操作
            verify(recordDetailService, never()).update(any(UpdateWrapper.class));
            verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("创建者权限保留entry时审核旧记录 → 成功")
        void testApproveWithCreatorPermissionValid() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -300.0);
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(false);
            when(sharedBookService.hasPermission(BOOK_ID, CREATOR_ID, RecordConstant.PERM_ENTRY)).thenReturn(true);
            when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

            assertDoesNotThrow(() -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));

            verify(budgetService).atomicIncrementUsed(eq(BOOK_ID), eq(YEAR_MONTH), eq(BigDecimal.valueOf(300.0)));
        }

        @Test
        @DisplayName("被移除成员的待审核记录 → RECORD_CREATOR_PERMISSION_REVOKED")
        void testApproveAfterCreatorRemoved() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -200.0);
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(false);
            // 创建者已被移除，hasPermission返回false
            when(sharedBookService.hasPermission(BOOK_ID, CREATOR_ID, RecordConstant.PERM_ENTRY)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
            assertEquals(CodeMsg.RECORD_CREATOR_PERMISSION_REVOKED.getRetCode(), ex.getCodeMsg().getRetCode());
        }
    }

    // ===================== 场景3: 重复冲正申请 =====================

    @Nested
    @DisplayName("重复冲正申请")
    class DuplicateReversalRequest {

        @InjectMocks
        private ReversalServiceImpl reversalService;

        @Mock
        private ReversalRequestMapper reversalRequestMapper;

        @Mock
        private RecordDetailService recordDetailService;

        @Mock
        private RecordDetailFactory recordDetailFactory;

        @Mock
        private DictItemService dictItemService;

        @Mock
        private SharedBookService sharedBookService;

        @Mock
        private MonthlyClosingService monthlyClosingService;

        @Mock
        private BudgetService budgetService;

        @Mock
        private SharedBookAuditLogService auditLogService;

        @Mock
        private RedisLockUtil redisLockUtil;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(reversalService, "baseMapper", reversalRequestMapper);
        }

        @Test
        @DisplayName("对已冲正记录申请冲正 → REVERSAL_ALREADY_REVERSED")
        void testReversalOnAlreadyReversedRecord() {
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_REVERSED, -1000.0);
            when(recordDetailService.getById(1L)).thenReturn(record);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "再次冲正"));
            assertEquals(CodeMsg.REVERSAL_ALREADY_REVERSED.getRetCode(), ex.getCodeMsg().getRetCode());
        }

        @Test
        @DisplayName("已有待审核冲正再次申请 → REVERSAL_ALREADY_PENDING")
        void testDuplicatePendingReversal() {
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED, -1000.0);
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(true);
            when(reversalService.count(any(QueryWrapper.class))).thenReturn(1); // 已有1条待审核

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
            assertEquals(CodeMsg.REVERSAL_ALREADY_PENDING.getRetCode(), ex.getCodeMsg().getRetCode());
        }

        @Test
        @DisplayName("冲正审批时原记录已被另一冲正审批通过 → REVERSAL_ALREADY_REVERSED")
        void testApproveReversalWhenAlreadyReversed() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

            ReversalRequestDO req = ReversalRequestDO.builder()
                    .id(10L).bookId(BOOK_ID).originalRecordId(1L)
                    .requesterId(CREATOR_ID).reviewStatus(RecordConstant.REVERSAL_PENDING)
                    .build();
            when(reversalRequestMapper.selectById(10L)).thenReturn(req);

            // 原记录已经被冲正
            RecordDetailDO original = buildRecord(CREATOR_ID, RecordConstant.REVIEW_REVERSED, -1000.0);
            when(recordDetailService.getById(1L)).thenReturn(original);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reversalService.approveReversal(BOOK_ID, REVIEWER_ID, 10L, "通过"));
            assertEquals(CodeMsg.REVERSAL_ALREADY_REVERSED.getRetCode(), ex.getCodeMsg().getRetCode());

            verify(redisLockUtil).releaseLock(anyString());
        }
    }

    // ===================== 场景4: 冲正恢复预算统计 =====================

    @Nested
    @DisplayName("冲正恢复预算统计")
    class ReversalBudgetRecovery {

        @InjectMocks
        private ReversalServiceImpl reversalService;

        @Mock
        private ReversalRequestMapper reversalRequestMapper;

        @Mock
        private RecordDetailService recordDetailService;

        @Mock
        private RecordDetailFactory recordDetailFactory;

        @Mock
        private RecordDetailHandler recordDetailHandler;

        @Mock
        private DictItemService dictItemService;

        @Mock
        private SharedBookService sharedBookService;

        @Mock
        private MonthlyClosingService monthlyClosingService;

        @Mock
        private BudgetService budgetService;

        @Mock
        private SharedBookAuditLogService auditLogService;

        @Mock
        private RedisLockUtil redisLockUtil;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(reversalService, "baseMapper", reversalRequestMapper);
        }

        @Test
        @DisplayName("冲正支出记录 → 预算递减恰好等于原金额")
        void testReversalDecrementsBudgetCorrectly() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

            ReversalRequestDO req = ReversalRequestDO.builder()
                    .id(10L).bookId(BOOK_ID).originalRecordId(1L)
                    .requesterId(CREATOR_ID).reviewStatus(RecordConstant.REVERSAL_PENDING)
                    .build();
            when(reversalRequestMapper.selectById(10L)).thenReturn(req);

            RecordDetailDO original = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED, -2500.0);
            when(recordDetailService.getById(1L)).thenReturn(original);

            DictItemDO dictItem = new DictItemDO();
            dictItem.setValue("expendType");
            when(dictItemService.getById(original.getRecordType())).thenReturn(dictItem);
            when(recordDetailFactory.getHandler("expendType")).thenReturn(recordDetailHandler);
            when(recordDetailHandler.handleAdd(eq(CREATOR_ID), any(RecordDetailBO.class))).thenReturn(2);

            RecordDetailDO counterEntry = new RecordDetailDO();
            counterEntry.setId(2L);
            counterEntry.setAmount(2500.0);
            when(recordDetailService.getById(2)).thenReturn(counterEntry);
            when(recordDetailService.getById(2L)).thenReturn(counterEntry);
            lenient().when(reversalRequestMapper.updateById(any(ReversalRequestDO.class))).thenReturn(1);
            when(recordDetailService.updateById(any(RecordDetailDO.class))).thenReturn(true);

            reversalService.approveReversal(BOOK_ID, REVIEWER_ID, 10L, "批准冲正");

            // 验证: 原记录被标记为 REVIEW_REVERSED
            verify(recordDetailService).updateById(argThat(r ->
                    r.getId().equals(1L) && r.getReviewStatus() == RecordConstant.REVIEW_REVERSED));

            // 验证: 反向条目设置了 originalRecordId 并自动入账
            verify(recordDetailService).updateById(argThat(r ->
                    r.getId().equals(2L) && r.getOriginalRecordId() != null
                            && r.getReviewStatus() == RecordConstant.REVIEW_POSTED));

            // 验证: 预算递减恰好原始金额 2500
            verify(budgetService).atomicDecrementUsed(eq(BOOK_ID), eq(YEAR_MONTH), eq(BigDecimal.valueOf(2500.0)));

            // 验证: 审计日志记录了冲正操作
            verify(auditLogService).log(eq(BOOK_ID), eq(REVIEWER_ID), eq("REVERSAL_APPROVE"),
                    eq("RECORD"), eq(1L), anyString());

            verify(redisLockUtil).releaseLock(anyString());
        }

        @Test
        @DisplayName("冲正收入记录 → 不触发预算递减")
        void testReversalIncomeRecordNoBudgetChange() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

            ReversalRequestDO req = ReversalRequestDO.builder()
                    .id(11L).bookId(BOOK_ID).originalRecordId(1L)
                    .requesterId(CREATOR_ID).reviewStatus(RecordConstant.REVERSAL_PENDING)
                    .build();
            when(reversalRequestMapper.selectById(11L)).thenReturn(req);

            // 收入记录 amount > 0
            RecordDetailDO original = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED, 3000.0);
            when(recordDetailService.getById(1L)).thenReturn(original);

            DictItemDO dictItem = new DictItemDO();
            dictItem.setValue("incomeType");
            when(dictItemService.getById(original.getRecordType())).thenReturn(dictItem);
            when(recordDetailFactory.getHandler("incomeType")).thenReturn(recordDetailHandler);
            when(recordDetailHandler.handleAdd(eq(CREATOR_ID), any(RecordDetailBO.class))).thenReturn(3);

            RecordDetailDO counterEntry = new RecordDetailDO();
            counterEntry.setId(3L);
            counterEntry.setAmount(-3000.0);
            when(recordDetailService.getById(3)).thenReturn(counterEntry);
            when(recordDetailService.getById(3L)).thenReturn(counterEntry);
            lenient().when(reversalRequestMapper.updateById(any(ReversalRequestDO.class))).thenReturn(1);
            when(recordDetailService.updateById(any(RecordDetailDO.class))).thenReturn(true);

            reversalService.approveReversal(BOOK_ID, REVIEWER_ID, 11L, "批准冲正");

            // 收入不影响预算
            verify(budgetService, never()).atomicDecrementUsed(anyInt(), anyString(), any(BigDecimal.class));
            verify(redisLockUtil).releaseLock(anyString());
        }
    }

    // ===================== 场景5: 多人并发记账和月结 =====================

    @Nested
    @DisplayName("多人并发记账和月结")
    class ConcurrentBookingAndClosing {

        @InjectMocks
        private RecordReviewServiceImpl recordReviewService;

        @Mock
        private RecordDetailService recordDetailService;

        @Mock
        private SharedBookService sharedBookService;

        @Mock
        private SharedBookAuditLogService auditLogService;

        @Mock
        private BudgetService budgetService;

        @Mock
        private MonthlyClosingService monthlyClosingService;

        @Mock
        private RedisUtil redisUtil;

        @Test
        @DisplayName("并发审核同一记录 → 仅1次成功，其余RECORD_NOT_PENDING")
        void testConcurrentApprovalSameRecord() throws Exception {
            doNothing().when(sharedBookService).checkPermission(anyInt(), anyInt(), eq(RecordConstant.PERM_REVIEW));
            when(monthlyClosingService.isMonthClosed(anyInt(), anyString())).thenReturn(false);
            when(sharedBookService.hasPermission(anyInt(), anyInt(), eq(RecordConstant.PERM_ENTRY))).thenReturn(true);

            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -500.0);
            when(recordDetailService.getById(1L)).thenReturn(record);

            // 模拟CAS: 仅第一次update返回true, 后续返回false
            AtomicBoolean firstCall = new AtomicBoolean(true);
            when(recordDetailService.update(any(UpdateWrapper.class))).thenAnswer(inv -> {
                return firstCall.compareAndSet(true, false);
            });

            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch latch = new CountDownLatch(3);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < 3; i++) {
                final int reviewerId = REVIEWER_ID + i;
                executor.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();
                        recordReviewService.approveRecord(BOOK_ID, reviewerId, 1L, "通过");
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getCodeMsg().getRetCode() == CodeMsg.RECORD_NOT_PENDING.getRetCode()) {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertEquals(1, successCount.get(), "仅1个审核者应成功");
            assertEquals(2, failCount.get(), "其余2个应收到RECORD_NOT_PENDING");
        }

        @Test
        @DisplayName("审核和月结并发 → 月结已执行时审核应被拒绝")
        void testApproveRacesWithMonthClose() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);

            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, -500.0);
            when(recordDetailService.getById(1L)).thenReturn(record);

            // 模拟月结已生效
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
            assertEquals(CodeMsg.RECORD_MONTH_CLOSED.getRetCode(), ex.getCodeMsg().getRetCode());

            // 确保月结后不会入账
            verify(recordDetailService, never()).update(any(UpdateWrapper.class));
            verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("收入记录审核通过 → 不触发预算递增")
        void testApproveIncomeRecordNoBudget() {
            doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
            RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING, 1000.0); // 收入
            when(recordDetailService.getById(1L)).thenReturn(record);
            when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq(YEAR_MONTH))).thenReturn(false);
            when(sharedBookService.hasPermission(BOOK_ID, CREATOR_ID, RecordConstant.PERM_ENTRY)).thenReturn(true);
            when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

            assertDoesNotThrow(() -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));

            // 收入不影响预算
            verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
        }
    }
}
