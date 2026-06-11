package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.impl.RecordReviewServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 记账审核服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("记账审核服务测试")
class RecordReviewServiceTest {

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
    private static final Integer REVIEWER_ID = 100;

    @Test
    @DisplayName("审核通过 → status=2")
    void testApproveRecord() {
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setAmount(-500.0);
        record.setRecordBookId(BOOK_ID);
        record.setOccurTime(new Date());
        when(recordDetailService.getById(1L)).thenReturn(record);

        recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过");

        verify(recordDetailService).update(any(UpdateWrapper.class));
        verify(auditLogService).log(eq(BOOK_ID), eq(REVIEWER_ID), eq("RECORD_REVIEW"), eq("RECORD"), eq(1L), anyString());
    }

    @Test
    @DisplayName("审核驳回 → status=3")
    void testRejectRecord() {
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);
        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(true);

        recordReviewService.rejectRecord(BOOK_ID, REVIEWER_ID, 1L, "金额不符");

        verify(recordDetailService).update(any(UpdateWrapper.class));
        verify(auditLogService).log(eq(BOOK_ID), eq(REVIEWER_ID), eq("RECORD_REJECT"), eq("RECORD"), eq(1L), anyString());
    }

    @Test
    @DisplayName("重复审核 → RECORD_NOT_PENDING")
    void testDoubleApprove() {
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);

        // 记录存在但月份未结
        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setAmount(-500.0);
        record.setRecordBookId(BOOK_ID);
        record.setOccurTime(new Date());
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(false);

        when(recordDetailService.update(any(UpdateWrapper.class))).thenReturn(false); // 0行更新

        BusinessException ex = assertThrows(BusinessException.class,
                () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
        assertEquals(CodeMsg.RECORD_NOT_PENDING.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("月结后审核 → MONTH_CLOSED_CANNOT_APPROVE")
    void testApproveAfterMonthClosed() {
        doNothing().when(sharedBookService).checkPermission(BOOK_ID, REVIEWER_ID, RecordConstant.PERM_REVIEW);

        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setAmount(-500.0);
        record.setRecordBookId(BOOK_ID);
        record.setOccurTime(new Date());
        when(recordDetailService.getById(1L)).thenReturn(record);
        when(monthlyClosingService.isMonthClosed(eq(BOOK_ID), anyString())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> recordReviewService.approveRecord(BOOK_ID, REVIEWER_ID, 1L, "通过"));
        assertEquals(CodeMsg.MONTH_CLOSED_CANNOT_APPROVE.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("提交审核 → review_status设为1")
    void testSubmitForReview() {
        RecordDetailDO record = new RecordDetailDO();
        record.setId(1L);
        record.setUserId(200);
        record.setRecordBookId(BOOK_ID);

        recordReviewService.submitForReview(record);

        assertEquals(RecordConstant.REVIEW_PENDING, record.getReviewStatus());
        verify(recordDetailService).updateById(record);
    }
}
