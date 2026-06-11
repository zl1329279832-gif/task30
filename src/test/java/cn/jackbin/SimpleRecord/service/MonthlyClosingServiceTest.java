package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
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

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 月结服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("月结服务测试")
class MonthlyClosingServiceTest {

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
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 1;
    private static final Integer USER_ID = 100;

    @BeforeEach
    void setUp() {
        // ServiceImpl needs the baseMapper set manually
        ReflectionTestUtils.setField(monthlyClosingService, "baseMapper", monthlyClosingMapper);
    }

    @Test
    @DisplayName("月结后新增当月记录 → MONTH_CLOSED_CANNOT_MODIFY")
    void testCheckNotClosedAfterClose() {
        when(redisUtil.hasKey(RedisKey.MONTHLY_CLOSING_PREFIX + BOOK_ID + ":2026-06")).thenReturn(true);

        Date juneDate;
        try {
            juneDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-06-15");
        } catch (Exception e) {
            fail("Date parse failed");
            return;
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> monthlyClosingService.checkNotClosed(BOOK_ID, juneDate));
        assertEquals(CodeMsg.MONTH_CLOSED_CANNOT_MODIFY.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("月结后新增次月记录 → 成功")
    void testCheckNotClosedNextMonth() {
        when(redisUtil.hasKey(RedisKey.MONTHLY_CLOSING_PREFIX + BOOK_ID + ":2026-07")).thenReturn(false);

        Date julyDate;
        try {
            julyDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-07-01");
        } catch (Exception e) {
            fail("Date parse failed");
            return;
        }

        // 不应抛异常
        assertDoesNotThrow(() -> monthlyClosingService.checkNotClosed(BOOK_ID, julyDate));
    }

    @Test
    @DisplayName("重复月结 → MONTH_ALREADY_CLOSED")
    void testDoubleClose() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(RedisKey.MONTHLY_CLOSING_PREFIX + BOOK_ID + ":2026-06")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, "2026-06", "test"));
        assertEquals(CodeMsg.MONTH_ALREADY_CLOSED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("月结时存在待审核记录 → PENDING_RECORDS_EXIST")
    void testCloseWithPendingRecords() {
        doNothing().when(sharedBookService).checkPermission(eq(BOOK_ID), eq(USER_ID), eq(RecordConstant.PERM_SETTLEMENT));
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(monthlyClosingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> monthlyClosingService.closeMonth(BOOK_ID, USER_ID, "2026-06", "test"));
        assertEquals(CodeMsg.PENDING_RECORDS_EXIST.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("isMonthClosed → Redis缓存命中返回true")
    void testIsMonthClosedFromRedis() {
        when(redisUtil.hasKey(RedisKey.MONTHLY_CLOSING_PREFIX + BOOK_ID + ":2026-06")).thenReturn(true);
        assertTrue(monthlyClosingService.isMonthClosed(BOOK_ID, "2026-06"));
    }
}
