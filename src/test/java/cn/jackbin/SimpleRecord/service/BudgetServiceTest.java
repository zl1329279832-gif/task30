package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
import cn.jackbin.SimpleRecord.service.impl.BudgetServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 预算服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预算服务测试")
class BudgetServiceTest {

    @Spy
    @InjectMocks
    private BudgetServiceImpl budgetService;

    @Mock
    private BookBudgetMapper bookBudgetMapper;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private static final Integer BOOK_ID = 1;
    private static final Integer USER_ID = 100;
    private static final String YEAR_MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("预算5000, 支出4500 → 成功且触发80%预警")
    void testBudgetWarning() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), eq(450000L))).thenReturn(450000L);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);

        budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("4500"));

        // 验证预警日志: 4500 >= 5000*0.8=4000 → 触发
        verify(auditLogService).log(eq(BOOK_ID), eq(0), eq("BUDGET_WARN"), eq("BUDGET"), isNull(), anyString());
    }

    @Test
    @DisplayName("预算5000, 已用4500再支出600 → BUDGET_EXCEEDED")
    void testBudgetExceeded() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        // 4500+600=5100 > 5000
        when(valueOperations.increment(anyString(), eq(60000L))).thenReturn(510000L);
        when(valueOperations.increment(anyString(), eq(-60000L))).thenReturn(450000L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("600")));
        assertEquals(CodeMsg.BUDGET_EXCEEDED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("预算5000, 支出3900 → 成功无预警")
    void testBudgetNoWarning() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), eq(390000L))).thenReturn(390000L);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);

        budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("3900"));

        // 3900 < 4000(80%), 无预警
        verify(auditLogService, never()).log(eq(BOOK_ID), eq(0), eq("BUDGET_WARN"), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("未设置预算 → 不检查直接通过")
    void testNoBudgetSet() {
        doReturn(null).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);

        budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("99999"));

        verify(valueOperations, never()).increment(anyString(), anyLong());
    }

    private BookBudgetDO buildBudget(BigDecimal amount, int threshold) {
        return BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(YEAR_MONTH)
                .budgetAmount(amount).usedAmount(BigDecimal.ZERO)
                .warnThreshold(threshold).status(0).build();
    }
}
