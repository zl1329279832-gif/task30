package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ReversalRequestMapper;
import cn.jackbin.SimpleRecord.service.impl.ReversalServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 冲正服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("冲正服务测试")
class ReversalServiceTest {

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

    private static final Integer BOOK_ID = 1;
    private static final Integer CREATOR_ID = 100;
    private static final Integer REVIEWER_ID = 200;

    @Test
    @DisplayName("非创建者申请冲正 → REVERSAL_NOT_BY_CREATOR")
    void testReversalNotByCreator() {
        RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, 999, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_NOT_BY_CREATOR.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("未入账记录申请冲正 → REVERSAL_RECORD_NOT_POSTED")
    void testReversalNotPosted() {
        RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_PENDING);
        when(recordDetailService.getById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_RECORD_NOT_POSTED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("月未结申请冲正 → REVERSAL_MONTH_NOT_CLOSED")
    void testReversalMonthNotClosed() {
        RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_MONTH_NOT_CLOSED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("正常申请冲正 → 成功")
    void testRequestReversalSuccess() {
        RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(true);
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(0);
        // mock save
        lenient().when(reversalRequestMapper.insert(any(ReversalRequestDO.class))).thenReturn(1);

        ReversalRequestDO result = reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "金额有误");

        assertNotNull(result);
        assertEquals(RecordConstant.REVERSAL_PENDING, result.getReviewStatus());
        assertEquals("金额有误", result.getRequestReason());
        verify(auditLogService).log(eq(BOOK_ID), eq(CREATOR_ID), eq("REVERSAL_REQUEST"), eq("RECORD"), eq(1L), anyString());
    }

    @Test
    @DisplayName("重复待审核冲正申请 → REVERSAL_ALREADY_PENDING")
    void testDuplicatePendingReversal() {
        RecordDetailDO record = buildRecord(CREATOR_ID, RecordConstant.REVIEW_POSTED);
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(true);
        when(reversalService.count(any(QueryWrapper.class))).thenReturn(1); // 已有1条待审核

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reversalService.requestReversal(BOOK_ID, CREATOR_ID, 1L, "测试"));
        assertEquals(CodeMsg.REVERSAL_ALREADY_PENDING.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    private RecordDetailDO buildRecord(Integer userId, int reviewStatus) {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setUserId(userId);
        record.setRecordBookId(BOOK_ID);
        record.setReviewStatus(reviewStatus);
        record.setAmount(-1000.0);
        try {
            record.setOccurTime(new SimpleDateFormat("yyyy-MM-dd").parse("2026-06-15"));
        } catch (Exception e) {
            record.setOccurTime(new Date());
        }
        record.setRecordType(1);
        record.setRecordAccountId(1);
        return record;
    }
}
