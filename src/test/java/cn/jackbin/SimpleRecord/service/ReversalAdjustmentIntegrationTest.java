package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.RecordDetailBO;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.mapper.ReversalRequestMapper;
import cn.jackbin.SimpleRecord.service.impl.ReversalServiceImpl;
import cn.jackbin.SimpleRecord.service.impl.RecordReviewServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 冲正/补审核调整单集成测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("冲正调整单集成测试")
class ReversalAdjustmentIntegrationTest {

    @Spy
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
    private ClosingAdjustmentService closingAdjustmentService;

    @Mock
    private RedisLockUtil redisLockUtil;

    @Mock
    private RecordDetailHandler handler;

    private static final Integer BOOK_ID = 1;
    private static final Integer REVIEWER_ID = 200;
    private static final Long REQUEST_ID = 50L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reversalService, "baseMapper", reversalRequestMapper);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("冲正审批 → 生成调整单")
    void testApproveReversal_generatesAdjustment() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));

        ReversalRequestDO req = buildRequest(REQUEST_ID, 100L, RecordConstant.REVERSAL_PENDING);
        doReturn(req).when(reversalService).getById(REQUEST_ID);
        when(reversalRequestMapper.updateById(any())).thenReturn(1);

        RecordDetailDO original = buildRecord(100L, -500.0, RecordConstant.REVIEW_POSTED, "餐饮", 1);
        when(recordDetailService.getById(100L)).thenReturn(original);
        when(recordDetailService.updateById(any())).thenReturn(true);

        DictItemDO dictItem = new DictItemDO();
        dictItem.setValue("expendType");
        when(dictItemService.getById(anyInt())).thenReturn(dictItem);
        when(recordDetailFactory.getHandler("expendType")).thenReturn(handler);
        when(handler.handleAdd(anyInt(), any(RecordDetailBO.class))).thenReturn(101);

        RecordDetailDO counterEntry = buildRecord(101L, 500.0, RecordConstant.REVIEW_POSTED, "餐饮", 1);
        when(recordDetailService.getById(101)).thenReturn(counterEntry);

        reversalService.approveReversal(BOOK_ID, REVIEWER_ID, REQUEST_ID, "approved");

        verify(closingAdjustmentService).createAdjustmentIdempotent(
                eq("REVERSAL:" + REQUEST_ID + ":100"),
                eq(BOOK_ID), anyString(), eq(RecordConstant.ADJUSTMENT_REVERSAL),
                eq(100L), eq(101L), eq(original.getUserId()),
                eq("餐饮"), eq(1),
                any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
                eq(REVIEWER_ID), anyString());
    }

    @Test
    @DisplayName("冲正调整幂等: 重复审批 → 调整单不重复")
    void testApproveReversal_adjustmentIdempotent() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));

        ReversalRequestDO req = buildRequest(REQUEST_ID, 100L, RecordConstant.REVERSAL_PENDING);
        doReturn(req).when(reversalService).getById(REQUEST_ID);
        when(reversalRequestMapper.updateById(any())).thenReturn(1);

        RecordDetailDO original = buildRecord(100L, -500.0, RecordConstant.REVIEW_POSTED, "餐饮", 1);
        when(recordDetailService.getById(100L)).thenReturn(original);
        when(recordDetailService.updateById(any())).thenReturn(true);

        DictItemDO dictItem = new DictItemDO();
        dictItem.setValue("expendType");
        when(dictItemService.getById(anyInt())).thenReturn(dictItem);
        when(recordDetailFactory.getHandler("expendType")).thenReturn(handler);
        when(handler.handleAdd(anyInt(), any(RecordDetailBO.class))).thenReturn(101);

        RecordDetailDO counterEntry = buildRecord(101L, 500.0, RecordConstant.REVIEW_POSTED, "餐饮", 1);
        when(recordDetailService.getById(101)).thenReturn(counterEntry);

        // 幂等: closingAdjustmentService 内部处理重复key
        ClosingAdjustmentDO existing = ClosingAdjustmentDO.builder().id(1L).build();
        when(closingAdjustmentService.createAdjustmentIdempotent(anyString(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(existing);

        reversalService.approveReversal(BOOK_ID, REVIEWER_ID, REQUEST_ID, "approved");

        // 验证调用了幂等方法
        verify(closingAdjustmentService).createAdjustmentIdempotent(
                eq("REVERSAL:" + REQUEST_ID + ":100"),
                eq(BOOK_ID), anyString(), eq(RecordConstant.ADJUSTMENT_REVERSAL),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("支出冲正: -1000 → adjustExpend=-1000, budgetImpact=-1000")
    void testApproveReversal_expenseAmounts() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));

        ReversalRequestDO req = buildRequest(REQUEST_ID, 100L, RecordConstant.REVERSAL_PENDING);
        doReturn(req).when(reversalService).getById(REQUEST_ID);
        when(reversalRequestMapper.updateById(any())).thenReturn(1);

        RecordDetailDO original = buildRecord(100L, -1000.0, RecordConstant.REVIEW_POSTED, "餐饮", 1);
        when(recordDetailService.getById(100L)).thenReturn(original);
        when(recordDetailService.updateById(any())).thenReturn(true);

        DictItemDO dictItem = new DictItemDO();
        dictItem.setValue("expendType");
        when(dictItemService.getById(anyInt())).thenReturn(dictItem);
        when(recordDetailFactory.getHandler("expendType")).thenReturn(handler);
        when(handler.handleAdd(anyInt(), any(RecordDetailBO.class))).thenReturn(101);
        when(recordDetailService.getById(101)).thenReturn(buildRecord(101L, 1000.0, RecordConstant.REVIEW_POSTED, "餐饮", 1));

        reversalService.approveReversal(BOOK_ID, REVIEWER_ID, REQUEST_ID, "approved");

        verify(closingAdjustmentService).createAdjustmentIdempotent(
                anyString(), eq(BOOK_ID), anyString(), eq(RecordConstant.ADJUSTMENT_REVERSAL),
                eq(100L), eq(101L), any(), any(), any(),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.valueOf(-1000.0)),
                eq(BigDecimal.valueOf(-1000.0)),
                eq(REVIEWER_ID), anyString());
    }

    @Test
    @DisplayName("收入冲正: +500 → adjustIncome=-500")
    void testApproveReversal_incomeAmounts() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));

        ReversalRequestDO req = buildRequest(REQUEST_ID, 100L, RecordConstant.REVERSAL_PENDING);
        doReturn(req).when(reversalService).getById(REQUEST_ID);
        when(reversalRequestMapper.updateById(any())).thenReturn(1);

        RecordDetailDO original = buildRecord(100L, 500.0, RecordConstant.REVIEW_POSTED, "工资", 1);
        when(recordDetailService.getById(100L)).thenReturn(original);
        when(recordDetailService.updateById(any())).thenReturn(true);

        DictItemDO dictItem = new DictItemDO();
        dictItem.setValue("incomeType");
        when(dictItemService.getById(anyInt())).thenReturn(dictItem);
        when(recordDetailFactory.getHandler("incomeType")).thenReturn(handler);
        when(handler.handleAdd(anyInt(), any(RecordDetailBO.class))).thenReturn(101);
        when(recordDetailService.getById(101)).thenReturn(buildRecord(101L, -500.0, RecordConstant.REVIEW_POSTED, "工资", 1));

        reversalService.approveReversal(BOOK_ID, REVIEWER_ID, REQUEST_ID, "approved");

        verify(closingAdjustmentService).createAdjustmentIdempotent(
                anyString(), eq(BOOK_ID), anyString(), eq(RecordConstant.ADJUSTMENT_REVERSAL),
                eq(100L), eq(101L), any(), any(), any(),
                eq(BigDecimal.valueOf(-500.0)),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO),
                eq(REVIEWER_ID), anyString());
    }

    @Test
    @DisplayName("补审核: 月结后审批 → 生成SUPPLEMENTARY_AUDIT调整单")
    void testSupplementaryAudit_generatesAdjustment() {
        RecordReviewServiceImpl reviewService = new RecordReviewServiceImpl();
        ReflectionTestUtils.setField(reviewService, "recordDetailService", recordDetailService);
        ReflectionTestUtils.setField(reviewService, "sharedBookService", sharedBookService);
        ReflectionTestUtils.setField(reviewService, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(reviewService, "budgetService", budgetService);
        ReflectionTestUtils.setField(reviewService, "monthlyClosingService", monthlyClosingService);
        ReflectionTestUtils.setField(reviewService, "closingAdjustmentService", closingAdjustmentService);
        ReflectionTestUtils.setField(reviewService, "redisUtil", mock(RedisUtil.class));

        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(REVIEWER_ID), eq(RecordConstant.PERM_REVIEW));
        RecordDetailDO record = buildRecord(200L, -300.0, RecordConstant.REVIEW_PENDING, "餐饮", 1);
        when(recordDetailService.getById(200L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(true);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        reviewService.approveRecord(BOOK_ID, REVIEWER_ID, 200L, "补审核");

        verify(closingAdjustmentService).createAdjustmentIdempotent(
                eq("SUPPLEMENTARY_AUDIT:200"), eq(BOOK_ID), anyString(),
                eq(RecordConstant.ADJUSTMENT_SUPPLEMENTARY),
                eq(200L), isNull(), eq(record.getUserId()),
                eq("餐饮"), eq(1),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.valueOf(300.0)),
                eq(BigDecimal.valueOf(300.0)),
                eq(REVIEWER_ID), eq("补充审核入账"));
    }

    private ReversalRequestDO buildRequest(Long id, Long originalRecordId, int status) {
        return ReversalRequestDO.builder()
                .id(id).bookId(BOOK_ID).originalRecordId(originalRecordId)
                .requesterId(100).requestReason("test reason")
                .reviewStatus(status).status(0).build();
    }

    private RecordDetailDO buildRecord(Long id, double amount, int reviewStatus, String category, int accountId) {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(id);
        record.setUserId(100);
        record.setRecordBookId(BOOK_ID);
        record.setRecordType(1);
        record.setRecordCategory(category);
        record.setRecordAccountId(accountId);
        record.setAmount(amount);
        record.setOccurTime(new Date());
        record.setReviewStatus(reviewStatus);
        record.setVersion(0);
        return record;
    }
}
