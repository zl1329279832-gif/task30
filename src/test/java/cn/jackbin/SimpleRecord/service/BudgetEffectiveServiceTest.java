package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryoverDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
import cn.jackbin.SimpleRecord.service.impl.BudgetServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
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
 * 有效预算(含结转)测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("有效预算(含结转)测试")
class BudgetEffectiveServiceTest {

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
    private BudgetCarryoverService budgetCarryoverService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private static final Integer BOOK_ID = 1;
    private static final String YEAR_MONTH = "2026-07";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("有效预算含结转: 5000+2000=7000, 支出6500 → 成功(未超7000)")
    void testAtomicIncrement_usesEffectiveBudget() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(budgetCarryoverService.getEffectiveBudget(BOOK_ID, YEAR_MONTH))
                .thenReturn(new BigDecimal("7000"));
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), eq(650000L))).thenReturn(650000L);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);

        // 6500 < 7000(effective) → 成功
        assertDoesNotThrow(() -> budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("6500")));
    }

    @Test
    @DisplayName("有效预算含结转: 5000+2000=7000, 支出7500 → BUDGET_EXCEEDED")
    void testAtomicIncrement_exceedsEffectiveBudget() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(budgetCarryoverService.getEffectiveBudget(BOOK_ID, YEAR_MONTH))
                .thenReturn(new BigDecimal("7000"));
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), eq(750000L))).thenReturn(750000L);
        when(valueOperations.increment(anyString(), eq(-750000L))).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("7500")));
        assertEquals(CodeMsg.BUDGET_EXCEEDED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("预算预警使用有效预算: 7000*80%=5600, 支出5700 → 触发预警")
    void testBudgetWarning_usesEffectiveBudget() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(budgetCarryoverService.getEffectiveBudget(BOOK_ID, YEAR_MONTH))
                .thenReturn(new BigDecimal("7000"));
        // Redis返回已使用0
        when(redisUtil.get(anyString())).thenReturn(0L);

        boolean warned = budgetService.checkBudgetWarning(BOOK_ID, new BigDecimal("5700"), YEAR_MONTH);
        assertTrue(warned); // 5700 >= 7000*0.8=5600
    }

    @Test
    @DisplayName("无结转 → 有效预算=原始预算")
    void testEffectiveBudget_noCarryover() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(budgetCarryoverService.getEffectiveBudget(BOOK_ID, YEAR_MONTH))
                .thenReturn(new BigDecimal("5000"));
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), eq(400000L))).thenReturn(400000L);
        when(bookBudgetMapper.updateById(any())).thenReturn(1);

        // 4000 < 5000 → 成功
        assertDoesNotThrow(() -> budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("4000")));
    }

    @Test
    @DisplayName("负结转(超支): 5000-1000=4000有效, 支出4500 → BUDGET_EXCEEDED")
    void testEffectiveBudget_negativeCarryover() {
        BookBudgetDO budget = buildBudget(new BigDecimal("5000"), 80);
        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        when(budgetCarryoverService.getEffectiveBudget(BOOK_ID, YEAR_MONTH))
                .thenReturn(new BigDecimal("4000"));
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), eq(450000L))).thenReturn(450000L);
        when(valueOperations.increment(anyString(), eq(-450000L))).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("4500")));
        assertEquals(CodeMsg.BUDGET_EXCEEDED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    private BookBudgetDO buildBudget(BigDecimal amount, int threshold) {
        return BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(YEAR_MONTH)
                .budgetAmount(amount).usedAmount(BigDecimal.ZERO)
                .warnThreshold(threshold).status(0).build();
    }
}
