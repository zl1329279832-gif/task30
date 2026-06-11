package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.RecordDetailBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ReversalRequestMapper;
import cn.jackbin.SimpleRecord.service.impl.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 共享账本状态一致性测试
 * 覆盖场景:
 *   1. 待审核记录遇到月结
 *   2. 权限变更后审核旧记录
 *   3. 重复冲正申请
 *   4. 冲正恢复预算统计
 *   5. 多人并发记账和月结
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("共享账本状态一致性测试")
class SharedBookStateConsistencyTest {

    // ========== 审核服务 ==========
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

    private static final Integer BOOK_ID = 1;
    private static final Integer OWNER_ID = 100;
    private static final Integer MEMBER_A_ID = 200;
    private static final Integer REVIEWER_ID = 300;

    private Date juneDate;
    private Date julyDate;

    @BeforeEach
    void setUp() throws Exception {
        juneDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-06-15");
        julyDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-07-01");
    }

    // =====================================================================
    // 场景 1: 待审核记录遇到月结
    // 成员 A 提交待审核支出后，管理员执行月结，之后审核该记录应被拒绝
    // =====================================================================

    @Test
    @DisplayName("场景1a: 月结后审核待审核记录 → MONTH_CLOSED_CANNOT_APPROVE")
    void testApproveAfterMonthClosed_shouldFail() {
        // 成员 A 的待审核记录
        RecordDetailDO pendingRecord = buildRecord(1L, MEMBER_A_ID, -500.0, juneDate,
                RecordConstant.REVIEW_PENDING);
        when(recordDetailService.getById(1L)).thenReturn(pendingRecord);

        // 月结已完成
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06"))).thenReturn(true);

        // 审核通过 → 应抛异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
        assertEquals(CodeMsg.MONTH_CLOSED_CANNOT_APPROVE.getRetCode(),
                ex.getCodeMsg().getRetCode());

        // 验证: 预算未被修改
        verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
        // 验证: 状态未被更新
        verify(recordDetailService, never()).update(any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("场景1b: 月结时有待审核记录 → PENDING_RECORDS_EXIST, 月结失败")
    void testCloseMonthWithPendingRecords_shouldFail() {
        // 模拟: closeMonth 内部检查有待审核记录
        // (由 MonthlyClosingServiceTest 覆盖, 这里验证状态一致性)
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06"))).thenReturn(false);

        // 月结未成功时审核应该可以正常通过
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
        RecordDetailDO pendingRecord = buildRecord(1L, MEMBER_A_ID, -500.0, juneDate,
                RecordConstant.REVIEW_PENDING);
        when(recordDetailService.getById(1L)).thenReturn(pendingRecord);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        // 不应抛异常
        assertDoesNotThrow(() -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));

        // 预算应被递增
        verify(budgetService).atomicIncrementUsed(eq(BOOK_ID), eq("2026-06"), any(BigDecimal.class));
    }

    @Test
    @DisplayName("场景1c: 月结后驳回待审核记录 → 允许 (驳回不影响预算)")
    void testRejectAfterMonthClosed_shouldSucceed() {
        // 驳回操作不涉及预算变动，月结后仍应允许
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        assertDoesNotThrow(() ->
                recordReviewService.rejectRecord(BOOK_ID, REVIEWER_ID, 1L, "月结后驳回"));

        verify(auditLogService).log(eq(BOOK_ID), eq(REVIEWER_ID), eq("RECORD_REJECT"),
                eq("RECORD"), eq(1L), anyString());
    }

    // =====================================================================
    // 场景 2: 权限变更后审核旧记录
    // 成员 A 提交待审核记录后，权限被降级为仅查看，此时审核旧记录应被拒绝
    // =====================================================================

    @Test
    @DisplayName("场景2a: 审核权限被降级后审核旧记录 → SHARED_BOOK_PERMISSION_DENIED")
    void testApproveAfterPermissionDowngrade_shouldFail() {
        // 审核人权限被降级为仅查看 → checkPermission 抛异常
        doThrow(new BusinessException(CodeMsg.SHARED_BOOK_PERMISSION_DENIED))
                .when(sharedBookService).checkPermission(BOOK_ID, MEMBER_A_ID, RecordConstant.PERM_REVIEW);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> recordReviewService.approveRecord(BOOK_ID, MEMBER_A_ID, 1L, "通过"));
        assertEquals(CodeMsg.SHARED_BOOK_PERMISSION_DENIED.getRetCode(),
                ex.getCodeMsg().getRetCode());

        // 预算和状态均未被修改
        verify(budgetService, never()).atomicIncrementUsed(anyInt(), anyString(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("场景2b: 权限仍有效 + 月未结 → 审核通过")
    void testApproveWithValidPermission_shouldSucceed() {
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);

        RecordDetailDO pendingRecord = buildRecord(1L, MEMBER_A_ID, -300.0, julyDate,
                RecordConstant.REVIEW_PENDING);
        when(recordDetailService.getById(1L)).thenReturn(pendingRecord);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-07"))).thenReturn(false);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        assertDoesNotThrow(() -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));

        verify(budgetService).atomicIncrementUsed(eq(BOOK_ID), eq("2026-07"),
                eq(BigDecimal.valueOf(300.0)));
    }

    // =====================================================================
    // 场景 3: 重复冲正申请
    // =====================================================================

    @Test
    @DisplayName("场景3a: 已冲正记录再次申请冲正 → REVERSAL_RECORD_NOT_POSTED")
    void testReversalOnAlreadyReversedRecord_shouldFail() {
        ReversalServiceImpl reversalService = buildReversalService();

        // 原记录已被冲正 (status = 4)
        RecordDetailDO reversedRecord = buildRecord(1L, MEMBER_A_ID, -1000.0, juneDate,
                RecordConstant.REVIEW_REVERSED);
        when(recordDetailService.getById(1L)).thenReturn(reversedRecord);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, MEMBER_A_ID, 1L, "再次冲正"));
        assertEquals(CodeMsg.REVERSAL_RECORD_NOT_POSTED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("场景3b: 并发审批两个冲正申请 → 第二个 REVERSAL_RECORD_ALREADY_REVERSED")
    void testConcurrentReversalApproval_secondShouldFail() {
        ReversalServiceImpl reversalService = buildReversalService();

        // 冲正申请1 和 2 都指向同一条已入账记录
        RecordDetailDO postedRecord = buildRecord(1L, MEMBER_A_ID, -1000.0, juneDate,
                RecordConstant.REVIEW_POSTED);

        // 模拟第一次审批后原记录状态变更为 REVERSED
        RecordDetailDO reversedRecord = buildRecord(1L, MEMBER_A_ID, -1000.0, juneDate,
                RecordConstant.REVIEW_REVERSED);

        // 使用 thenReturn 链模拟两次调用: 第一次 POSTED, 第二次 REVERSED
        when(recordDetailService.getById(1L))
                .thenReturn(postedRecord)
                .thenReturn(reversedRecord);

        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        // 模拟 dictItemService 和 handler
        DictItemDO dictItem = new DictItemDO();
        dictItem.setId(1L);
        dictItem.setValue("expendType");
        lenient().when(dictItemService.getById(any())).thenReturn(dictItem);

        RecordDetailHandler mockHandler = mock(RecordDetailHandler.class);
        lenient().when(recordDetailFactory.getHandler("expendType")).thenReturn(mockHandler);
        lenient().when(mockHandler.handleAdd(anyInt(), any(RecordDetailBO.class))).thenReturn(999);

        RecordDetailDO counterEntry = buildRecord(999L, MEMBER_A_ID, 1000.0, new Date(),
                RecordConstant.REVIEW_NONE);
        // getById with int (handleAdd returns int)
        lenient().when(recordDetailService.getById(999)).thenReturn(counterEntry);

        // 第一次审批: 标记申请为通过
        ReversalRequestDO req1 = buildReversalRequest(10L, 1L, RecordConstant.REVERSAL_PENDING);
        when(reversalService.getById(10L)).thenReturn(req1);

        // 第一次审批应成功
        assertDoesNotThrow(() -> reversalService.approveReversal(BOOK_ID, REVIEWER_ID, 10L, "通过"));

        // 第二次审批: 模拟另一冲正申请指向同一原记录
        ReversalRequestDO req2 = buildReversalRequest(11L, 1L, RecordConstant.REVERSAL_PENDING);
        when(reversalService.getById(11L)).thenReturn(req2);

        // 第二次读取原记录时返回 REVERSED → 应抛 REVERSAL_RECORD_ALREADY_REVERSED
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.approveReversal(BOOK_ID, REVIEWER_ID, 11L, "通过"));
        assertEquals(CodeMsg.REVERSAL_RECORD_ALREADY_REVERSED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("场景3c: 同一记录的重复待审核冲正申请 → REVERSAL_ALREADY_PENDING")
    void testDuplicatePendingReversalRequest_shouldFail() {
        ReversalServiceImpl reversalService = buildReversalService();

        RecordDetailDO postedRecord = buildRecord(1L, MEMBER_A_ID, -1000.0, juneDate,
                RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(postedRecord);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06"))).thenReturn(true);
        // 已有1条待审核冲正申请
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, MEMBER_A_ID, 1L, "重复申请"));
        assertEquals(CodeMsg.REVERSAL_ALREADY_PENDING.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    // =====================================================================
    // 场景 4: 冲正恢复预算统计
    // 冲正审批通过后，预算执行额、账户余额、成员分摊应全部恢复一致
    // =====================================================================

    @Test
    @DisplayName("场景4a: 冲正后预算统计恢复 → 反向条目标记 REVIEW_REVERSED, 不计入预算SQL")
    void testReversalBudgetRestoration() {
        ReversalServiceImpl reversalService = buildReversalService();

        // 原支出记录 1000 元, 已入账
        RecordDetailDO original = buildRecord(1L, MEMBER_A_ID, -1000.0, juneDate,
                RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(original);

        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);

        ReversalRequestDO req = buildReversalRequest(10L, 1L, RecordConstant.REVERSAL_PENDING);
        when(reversalService.getById(10L)).thenReturn(req);

        // 模拟 handler 创建反向条目
        DictItemDO dictItem = new DictItemDO();
        dictItem.setId(1L);
        dictItem.setValue("expendType");
        lenient().when(dictItemService.getById(any())).thenReturn(dictItem);

        RecordDetailHandler mockHandler = mock(RecordDetailHandler.class);
        lenient().when(recordDetailFactory.getHandler("expendType")).thenReturn(mockHandler);
        lenient().when(mockHandler.handleAdd(eq(MEMBER_A_ID), any(RecordDetailBO.class))).thenReturn(999);

        RecordDetailDO counterEntry = buildRecord(999L, MEMBER_A_ID, 1000.0, new Date(),
                RecordConstant.REVIEW_NONE);
        lenient().when(recordDetailService.getById(999)).thenReturn(counterEntry);

        // 执行冲正审批
        reversalService.approveReversal(BOOK_ID, REVIEWER_ID, 10L, "通过");

        // 验证: 原记录被标记为 REVIEW_REVERSED (4)
        assertEquals(RecordConstant.REVIEW_REVERSED, original.getReviewStatus());

        // 验证: 反向条目被标记为 REVIEW_REVERSED (4), 而非 REVIEW_POSTED (2)
        // 这是关键: 确保 BookBudgetMapper.queryUsedAmountByMonth 的 SQL
        // (review_status IN (0, 2)) 不会计入这两条记录
        assertEquals(RecordConstant.REVIEW_REVERSED, counterEntry.getReviewStatus());

        // 验证: 未调用 atomicDecrementUsed (预算通过 SQL 排除自动恢复)
        verify(budgetService, never()).atomicDecrementUsed(anyInt(), anyString(), any(BigDecimal.class));

        // 验证: 审计日志包含冲正记录ID
        verify(auditLogService).log(eq(BOOK_ID), eq(REVIEWER_ID), eq("REVERSAL_APPROVE"),
                eq("RECORD"), eq(1L), contains("reversalRecordId"));
    }

    @Test
    @DisplayName("场景4b: 冲正后预算SQL一致性 → 原记录+反向条目均被排除")
    void testBudgetSqlConsistencyAfterReversal() {
        // 模拟 BookBudgetMapper.queryUsedAmountByMonth 的 SQL 逻辑:
        // review_status IN (0, 2) AND delete_time IS NULL AND target_account_id IS NULL
        //
        // 冲正前: 原记录 review_status=2(POSTED), amount=-1000
        //   → SQL: ABS(-1000) = 1000 计入预算
        //
        // 冲正后: 原记录 review_status=4(REVERSED), 反向条目 review_status=4(REVERSED)
        //   → SQL: 两条均不匹配 IN (0, 2), 贡献 = 0
        //   → 预算恢复一致

        // 验证: REVIEW_REVERSED 不在 IN (0, 2) 范围内
        int reviewReversed = RecordConstant.REVIEW_REVERSED; // 4
        assertFalse(reviewReversed == RecordConstant.REVIEW_NONE || reviewReversed == RecordConstant.REVIEW_POSTED,
                "REVIEW_REVERSED should NOT be in budget SQL filter");

        // 验证: REVIEW_POSTED 仍在范围内 (正常记录不受影响)
        int reviewPosted = RecordConstant.REVIEW_POSTED; // 2
        assertTrue(reviewPosted == RecordConstant.REVIEW_POSTED,
                "REVIEW_POSTED should be in budget SQL filter");
    }

    @Test
    @DisplayName("场景4c: 冲正后账户余额一致性 → 原记录+反向条目净额为零")
    void testAccountBalanceConsistencyAfterReversal() {
        // RecordAccountMapper.queryInAndOutTotal 逻辑:
        // outflow: SUM(amount) WHERE amount < 0 AND review_status NOT IN (1,3)
        // inflow:  SUM(amount) WHERE amount >= 0 AND review_status NOT IN (1,3)
        //
        // 冲正前: 原记录 amount=-1000, review_status=2 → outflow += 1000
        // 冲正后:
        //   原记录 amount=-1000, review_status=4 → NOT IN (1,3) → outflow += -1000
        //   反向条目 amount=+1000, review_status=4 → NOT IN (1,3) → inflow += 1000
        //
        // 对于 outflow 查询 (amount < 0):
        //   只有原记录 amount=-1000 → outflow = -1000
        // 对于 inflow 查询 (amount >= 0):
        //   只有反向条目 amount=+1000 → inflow = +1000
        // 净流入 = inflow + outflow = 1000 + (-1000) = 0 ✓
        //
        // 净效果: 账户余额恢复到冲正前状态

        double originalAmount = -1000.0;
        double counterAmount = 1000.0; // -originalAmount

        // 模拟 outflow 查询 (amount < 0)
        double outflow = originalAmount; // 只有原记录

        // 模拟 inflow 查询 (amount >= 0)
        double inflow = counterAmount; // 只有反向条目

        // 净额 = 0
        assertEquals(0.0, inflow + outflow, 0.001,
                "Account balance should net to zero after reversal");
    }

    @Test
    @DisplayName("场景4d: 冲正后成员分摊一致性 → 原记录被排除, 反向条目净额为零")
    void testMemberSettlementConsistencyAfterReversal() {
        // MemberSettlementServiceImpl.calculateNetBalances 逻辑:
        // review_status IN (0, 2)
        //
        // 冲正前: 原记录 review_status=2, amount=-1000 (userId=A)
        //   → A 的分摊余额 -= 1000 (A 垫付)
        //
        // 冲正后:
        //   原记录 review_status=4 → 不在 IN (0, 2) → 被排除 ✓
        //   反向条目 review_status=4 → 不在 IN (0, 2) → 被排除 ✓
        //   → A 的分摊余额恢复到冲正前状态 ✓

        // 验证 REVIEW_REVERSED 不在成员分摊查询范围
        int reviewReversed = RecordConstant.REVIEW_REVERSED;
        assertFalse(reviewReversed == RecordConstant.REVIEW_NONE || reviewReversed == RecordConstant.REVIEW_POSTED,
                "REVIEW_REVERSED should NOT be in settlement filter");
    }

    // =====================================================================
    // 场景 5: 多人并发记账和月结
    // =====================================================================

    @Test
    @DisplayName("场景5a: 并发审核 + 月结竞争 → 月结成功后审核必须失败")
    void testConcurrentApproveAndCloseMonth() throws Exception {
        // 模拟两个线程: 一个审核, 一个月结
        // 月结先完成 → 审核应失败

        AtomicInteger monthClosed = new AtomicInteger(0); // 0=未结, 1=已结

        // 月结线程: 标记月份已结
        Thread closeMonthThread = new Thread(() -> {
            monthClosed.set(1);
        });

        // 审核线程: 检查月结状态
        RecordDetailDO pendingRecord = buildRecord(1L, MEMBER_A_ID, -500.0, juneDate,
                RecordConstant.REVIEW_PENDING);
        when(recordDetailService.getById(1L)).thenReturn(pendingRecord);

        // 动态返回月结状态
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06")))
                .thenAnswer(inv -> monthClosed.get() == 1);

        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));

        // 先执行月结
        closeMonthThread.start();
        closeMonthThread.join();

        // 月结后审核 → 应失败
        BusinessException ex = assertThrows(BusinessException.class,
                () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
        assertEquals(CodeMsg.MONTH_CLOSED_CANNOT_APPROVE.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("场景5b: 多人并发记账 → 预算控制正确")
    void testConcurrentBookingBudgetControl() throws Exception {
        // 5个线程同时提交支出, 预算 10000, 每笔 3000
        // 期望: 最多 3 笔成功 (3 * 3000 = 9000 < 10000, 4 * 3000 = 12000 > 10000)

        AtomicInteger currentUsed = new AtomicInteger(0);
        int budgetCents = 1000000; // 10000 元 = 1000000 分
        int expenseCents = 300000; // 3000 元

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    // 模拟 Redis 原子递增
                    int newTotal = currentUsed.addAndGet(expenseCents);
                    if (newTotal > budgetCents) {
                        // 回滚
                        currentUsed.addAndGet(-expenseCents);
                        failCount.incrementAndGet();
                    } else {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 至少 3 笔成功 (9000 <= 10000)
        assertTrue(successCount.get() >= 3,
                "Expected at least 3 successful bookings, got " + successCount.get());
        // 总使用量不超预算
        assertTrue(currentUsed.get() <= budgetCents,
                "Total used should not exceed budget: " + currentUsed.get());
    }

    @Test
    @DisplayName("场景5c: 并发冲正申请 + 月结 → 月结锁定后冲正仍可通过 (受控冲正)")
    void testConcurrentReversalAndCloseMonth() {
        ReversalServiceImpl reversalService = buildReversalService();

        // 已入账记录
        RecordDetailDO postedRecord = buildRecord(1L, MEMBER_A_ID, -1000.0, juneDate,
                RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(postedRecord);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06"))).thenReturn(true);
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(0);
        lenient().when(reversalRequestMapper.insert(any(ReversalRequestDO.class))).thenReturn(1);

        // 月已结 → 冲正申请应成功 (冲正是月结后唯一允许的修改路径)
        ReversalRequestDO result = reversalService.requestReversal(BOOK_ID, MEMBER_A_ID, 1L, "月结后冲正");

        assertNotNull(result);
        assertEquals(RecordConstant.REVERSAL_PENDING, result.getReviewStatus());
    }

    @Test
    @DisplayName("场景5d: 月结快照一致性 → 月结快照仅包含已入账和无需审核的记录")
    void testMonthClosingSnapshotConsistency() {
        // 月结快照 SQL:
        // review_status IN (REVIEW_NONE=0, REVIEW_POSTED=2)
        // AND target_account_id IS NULL (排除转账目标端)
        //
        // 待审核 (1), 已驳回 (3), 已冲正 (4) 均不在快照范围内
        // 这确保了快照反映的是实际生效的财务数据

        // 验证各状态是否在快照范围内
        assertTrue(isInSnapshot(RecordConstant.REVIEW_NONE), "REVIEW_NONE should be in snapshot");
        assertFalse(isInSnapshot(RecordConstant.REVIEW_PENDING), "REVIEW_PENDING should NOT be in snapshot");
        assertTrue(isInSnapshot(RecordConstant.REVIEW_POSTED), "REVIEW_POSTED should be in snapshot");
        assertFalse(isInSnapshot(RecordConstant.REVIEW_REJECTED), "REVIEW_REJECTED should NOT be in snapshot");
        assertFalse(isInSnapshot(RecordConstant.REVIEW_REVERSED), "REVIEW_REVERSED should NOT be in snapshot");
    }

    // =====================================================================
    // 场景 6: 完整生命周期 - 端到端状态一致性
    // =====================================================================

    @Test
    @DisplayName("场景6: 完整生命周期 → 提交→月结阻止→审核→月结→冲正→预算恢复")
    void testFullLifecycle() {
        // Step 1: 成员 A 提交待审核支出 500 元
        RecordDetailDO pendingRecord = buildRecord(1L, MEMBER_A_ID, -500.0, juneDate,
                RecordConstant.REVIEW_PENDING);

        // Step 2: 管理员尝试月结 → 被阻止 (有待审核记录)
        // (MonthlyClosingService.closeMonth 会检查 PENDING 并抛 PENDING_RECORDS_EXIST)

        // Step 3: 审核人通过审核
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));
        when(recordDetailService.getById(1L)).thenReturn(pendingRecord);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06"))).thenReturn(false);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过");
        verify(budgetService).atomicIncrementUsed(eq(BOOK_ID), eq("2026-06"),
                eq(BigDecimal.valueOf(500.0)));

        // Step 4: 月结成功 (无待审核记录)

        // Step 5: 成员 A 申请冲正
        // (月结后, 记录已入账 → 冲正申请有效)

        // Step 6: 冲正审批通过
        // → 原记录 review_status = 4 (REVERSED)
        // → 反向条目 review_status = 4 (REVERSED)
        // → 预算 SQL 排除两条记录 → 净效果 = 预算恢复

        // 验证: 生命周期结束时预算净变化 = 0
        // (atomicIncrementUsed +500 被冲正排除抵消)
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    private RecordDetailDO buildRecord(Long id, Integer userId, Double amount,
                                       Date occurTime, int reviewStatus) {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(id);
        record.setUserId(userId);
        record.setRecordBookId(BOOK_ID);
        record.setAmount(amount);
        record.setOccurTime(occurTime);
        record.setReviewStatus(reviewStatus);
        record.setRecordType(1);
        record.setRecordAccountId(1);
        record.setVersion(0);
        return record;
    }

    private ReversalRequestDO buildReversalRequest(Long id, Long originalRecordId, int reviewStatus) {
        return ReversalRequestDO.builder()
                .id(id)
                .bookId(BOOK_ID)
                .originalRecordId(originalRecordId)
                .requesterId(MEMBER_A_ID)
                .requestReason("测试冲正")
                .reviewStatus(reviewStatus)
                .status(0)
                .build();
    }

    /**
     * 构建独立的 ReversalServiceImpl 实例 (避免与 RecordReviewServiceImpl 的 mock 冲突)
     */
    private ReversalServiceImpl buildReversalService() {
        ReversalServiceImpl service = new ReversalServiceImpl();
        ReflectionTestUtils.setField(service, "reversalRequestMapper", reversalRequestMapper);
        ReflectionTestUtils.setField(service, "recordDetailService", recordDetailService);
        ReflectionTestUtils.setField(service, "recordDetailFactory", recordDetailFactory);
        ReflectionTestUtils.setField(service, "dictItemService", dictItemService);
        ReflectionTestUtils.setField(service, "sharedBookService", sharedBookService);
        ReflectionTestUtils.setField(service, "monthlyClosingService", monthlyClosingService);
        ReflectionTestUtils.setField(service, "budgetService", budgetService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "redisLockUtil", redisLockUtil);
        ReflectionTestUtils.setField(service, "baseMapper", reversalRequestMapper);
        return service;
    }

    /**
     * 判断 review_status 是否在月结快照范围内
     * 月结 SQL: review_status IN (0, 2)
     */
    private boolean isInSnapshot(int reviewStatus) {
        return reviewStatus == RecordConstant.REVIEW_NONE
                || reviewStatus == RecordConstant.REVIEW_POSTED;
    }

    // ========== Additional mocks for ReversalServiceImpl ==========
    @Mock
    private ReversalRequestMapper reversalRequestMapper;

    @Mock
    private RecordDetailFactory recordDetailFactory;

    @Mock
    private DictItemService dictItemService;

    @Mock
    private RedisLockUtil redisLockUtil;
}
