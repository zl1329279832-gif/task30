package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ClosingAdjustmentMapper;
import cn.jackbin.SimpleRecord.service.impl.ClosingAdjustmentServiceImpl;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 月结调整单服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("月结调整单服务测试")
class ClosingAdjustmentServiceTest {

    @Spy
    @InjectMocks
    private ClosingAdjustmentServiceImpl closingAdjustmentService;

    @Mock
    private ClosingAdjustmentMapper closingAdjustmentMapper;

    @Mock
    private MonthlyClosingService monthlyClosingService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 1;
    private static final String YEAR_MONTH = "2026-06";
    private static final Integer OPERATOR_ID = 100;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(closingAdjustmentService, "baseMapper", closingAdjustmentMapper);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("冲正调整单: 正确生成收支调整量")
    void testCreateAdjustment_reversal() {
        doReturn(null).when(closingAdjustmentService).getOne(any(QueryWrapper.class));
        MonthlyClosingDO closing = MonthlyClosingDO.builder().id(10L).bookId(BOOK_ID).yearMonth(YEAR_MONTH).build();
        when(monthlyClosingService.getClosing(BOOK_ID, YEAR_MONTH)).thenReturn(closing);

        ClosingAdjustmentDO result = closingAdjustmentService.createAdjustmentIdempotent(
                "REVERSAL:1:100", BOOK_ID, YEAR_MONTH, "REVERSAL",
                100L, 101L, 200, "餐饮", 1,
                BigDecimal.ZERO, new BigDecimal("-500"), new BigDecimal("-500"),
                OPERATOR_ID, "冲正调整");

        assertNotNull(result);
        assertEquals("REVERSAL", result.getAdjustmentType());
        assertEquals(0, new BigDecimal("-500").compareTo(result.getBudgetImpact()));
        verify(auditLogService).log(eq(BOOK_ID), eq(OPERATOR_ID), eq("CLOSING_ADJUSTMENT"),
                eq("CLOSING"), any(), anyString());
    }

    @Test
    @DisplayName("补审核调整单: 正确生成")
    void testCreateAdjustment_supplementaryAudit() {
        doReturn(null).when(closingAdjustmentService).getOne(any(QueryWrapper.class));
        MonthlyClosingDO closing = MonthlyClosingDO.builder().id(10L).bookId(BOOK_ID).yearMonth(YEAR_MONTH).build();
        when(monthlyClosingService.getClosing(BOOK_ID, YEAR_MONTH)).thenReturn(closing);

        ClosingAdjustmentDO result = closingAdjustmentService.createAdjustmentIdempotent(
                "SUPPLEMENTARY_AUDIT:200", BOOK_ID, YEAR_MONTH, "SUPPLEMENTARY_AUDIT",
                200L, null, 100, "交通", 2,
                BigDecimal.ZERO, new BigDecimal("300"), new BigDecimal("300"),
                OPERATOR_ID, "补充审核入账");

        assertNotNull(result);
        assertEquals("SUPPLEMENTARY_AUDIT", result.getAdjustmentType());
    }

    @Test
    @DisplayName("幂等: 相同key → 返回已有记录")
    void testIdempotent_duplicateKey() {
        ClosingAdjustmentDO existing = ClosingAdjustmentDO.builder()
                .id(1L).idempotencyKey("REVERSAL:1:100").build();
        doReturn(existing).when(closingAdjustmentService).getOne(any(QueryWrapper.class));

        ClosingAdjustmentDO result = closingAdjustmentService.createAdjustmentIdempotent(
                "REVERSAL:1:100", BOOK_ID, YEAR_MONTH, "REVERSAL",
                100L, 101L, 200, "餐饮", 1,
                BigDecimal.ZERO, new BigDecimal("-500"), new BigDecimal("-500"),
                OPERATOR_ID, "冲正调整");

        assertSame(existing, result);
        verify(closingAdjustmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("幂等: 不同key → 均创建成功")
    void testIdempotent_differentKeys() {
        doReturn(null).when(closingAdjustmentService).getOne(any(QueryWrapper.class));
        MonthlyClosingDO closing = MonthlyClosingDO.builder().id(10L).bookId(BOOK_ID).yearMonth(YEAR_MONTH).build();
        when(monthlyClosingService.getClosing(BOOK_ID, YEAR_MONTH)).thenReturn(closing);

        closingAdjustmentService.createAdjustmentIdempotent(
                "REVERSAL:1:100", BOOK_ID, YEAR_MONTH, "REVERSAL",
                100L, 101L, 200, "餐饮", 1,
                BigDecimal.ZERO, new BigDecimal("-500"), new BigDecimal("-500"),
                OPERATOR_ID, "冲正调整1");

        closingAdjustmentService.createAdjustmentIdempotent(
                "REVERSAL:2:200", BOOK_ID, YEAR_MONTH, "REVERSAL",
                200L, 201L, 300, "交通", 2,
                BigDecimal.ZERO, new BigDecimal("-300"), new BigDecimal("-300"),
                OPERATOR_ID, "冲正调整2");

        verify(closingAdjustmentService, times(2)).save(any());
    }

    @Test
    @DisplayName("预算影响净额: 多个调整 → 正确汇总")
    void testNetBudgetImpact() {
        when(closingAdjustmentMapper.queryNetBudgetImpact(BOOK_ID, YEAR_MONTH))
                .thenReturn(new BigDecimal("-800"));

        BigDecimal impact = closingAdjustmentService.getNetBudgetImpact(BOOK_ID, YEAR_MONTH);
        assertEquals(0, new BigDecimal("-800").compareTo(impact));
    }

    @Test
    @DisplayName("无月结记录 → ADJUSTMENT_CLOSING_NOT_FOUND")
    void testNoClosing_throwsException() {
        doReturn(null).when(closingAdjustmentService).getOne(any(QueryWrapper.class));
        when(monthlyClosingService.getClosing(BOOK_ID, YEAR_MONTH)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> closingAdjustmentService.createAdjustmentIdempotent(
                        "REVERSAL:1:100", BOOK_ID, YEAR_MONTH, "REVERSAL",
                        100L, null, 200, "餐饮", 1,
                        BigDecimal.ZERO, new BigDecimal("-500"), new BigDecimal("-500"),
                        OPERATOR_ID, "test"));
        assertEquals(CodeMsg.ADJUSTMENT_CLOSING_NOT_FOUND.getRetCode(), ex.getCodeMsg().getRetCode());
    }
}
