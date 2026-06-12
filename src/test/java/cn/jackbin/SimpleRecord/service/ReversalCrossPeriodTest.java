package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.ReversalRequestDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ReversalRequestMapper;
import cn.jackbin.SimpleRecord.service.impl.ReversalServiceImpl;
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

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 跨期冲销测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("跨期冲销测试")
class ReversalCrossPeriodTest {

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
    private DifferenceAdjustmentService differenceAdjustmentService;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 100;
    private static final Integer CREATOR_ID = 200;
    private static final Integer OTHER_USER_ID = 300;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reversalService, "baseMapper", reversalRequestMapper);
    }

    @Test
    @DisplayName("申请冲正-跨期-正确设置跨期标记")
    void requestReversal_crossPeriod_setsCorrectFlags() {
        RecordDetailDO record = buildRecord(1L, CREATOR_ID, RecordConstant.REVIEW_POSTED, "2026-05-15");
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-05"))).thenReturn(true);
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(0);
        lenient().when(reversalRequestMapper.insert(any(ReversalRequestDO.class))).thenReturn(1);
        lenient().when(reversalRequestMapper.updateById(any(ReversalRequestDO.class))).thenReturn(1);

        ReversalRequestDO result = reversalService.requestReversal(
                BOOK_ID, CREATOR_ID, 1L, "跨期冲正测试");

        assertNotNull(result);
        assertEquals(1, result.getCrossPeriod());
        assertEquals("2026-05", result.getSourceYearMonth());
        assertEquals(RecordConstant.REVERSAL_PENDING, result.getReviewStatus());
    }

    @Test
    @DisplayName("申请冲正-非创建者-抛出错误")
    void requestReversal_nonCreator_throwsError() {
        RecordDetailDO record = buildRecord(1L, CREATOR_ID, RecordConstant.REVIEW_POSTED, "2026-05-15");
        when(recordDetailService.getById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, OTHER_USER_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_NOT_BY_CREATOR.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("申请冲正-记录未入账-抛出错误")
    void requestReversal_recordNotPosted_throwsError() {
        RecordDetailDO record = buildRecord(1L, CREATOR_ID, RecordConstant.REVIEW_PENDING, "2026-05-15");
        when(recordDetailService.getById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_RECORD_NOT_POSTED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("申请冲正-月份未结-抛出错误")
    void requestReversal_monthNotClosed_throwsError() {
        RecordDetailDO record = buildRecord(1L, CREATOR_ID, RecordConstant.REVIEW_POSTED, "2026-05-15");
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-05"))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_MONTH_NOT_CLOSED.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("申请冲正-重复待审核申请-抛出错误")
    void requestReversal_duplicatePending_throwsError() {
        RecordDetailDO record = buildRecord(1L, CREATOR_ID, RecordConstant.REVIEW_POSTED, "2026-05-15");
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-05"))).thenReturn(true);
        // Already has 1 pending request
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_ALREADY_PENDING.getRetCode(),
                ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("申请冲正-成功-创建申请记录")
    void requestReversal_success_createsRequest() {
        RecordDetailDO record = buildRecord(1L, CREATOR_ID, RecordConstant.REVIEW_POSTED, "2026-06-15");
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), eq("2026-06"))).thenReturn(true);
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(0);
        lenient().when(reversalRequestMapper.insert(any(ReversalRequestDO.class))).thenReturn(1);
        lenient().when(reversalRequestMapper.updateById(any(ReversalRequestDO.class))).thenReturn(1);

        ReversalRequestDO result = reversalService.requestReversal(
                BOOK_ID, CREATOR_ID, 1L, "金额有误");

        assertNotNull(result);
        assertEquals(RecordConstant.REVERSAL_PENDING, result.getReviewStatus());
        assertEquals("金额有误", result.getRequestReason());
        assertEquals(BOOK_ID, result.getBookId());
        assertEquals(1L, result.getOriginalRecordId());
        assertEquals(CREATOR_ID, result.getRequesterId());
        verify(auditLogService).log(eq(BOOK_ID), eq(CREATOR_ID), eq("REVERSAL_REQUEST"),
                eq("RECORD"), eq(1L), anyString());
    }

    // ========== Helper Methods ==========

    private RecordDetailDO buildRecord(Long id, Integer userId, int reviewStatus, String dateStr) {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(id);
        record.setUserId(userId);
        record.setRecordBookId(BOOK_ID);
        record.setReviewStatus(reviewStatus);
        record.setAmount(-1000.0);
        record.setRecordType(1);
        record.setRecordAccountId(1);
        try {
            record.setOccurTime(new SimpleDateFormat("yyyy-MM-dd").parse(dateStr));
        } catch (Exception e) {
            record.setOccurTime(new Date());
        }
        return record;
    }
}
