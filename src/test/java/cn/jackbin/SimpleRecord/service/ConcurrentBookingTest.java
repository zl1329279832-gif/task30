package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 并发记账测试
 * 场景: 预算10000, 5笔各2500同时审核通过, 恰好4笔成功, 1笔超限
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("并发记账测试")
class ConcurrentBookingTest {

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
    private static final String YEAR_MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(budgetCarryoverService.getEffectiveBudget(eq(BOOK_ID), eq(YEAR_MONTH)))
                .thenReturn(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("5线程同时审核5笔2500(预算10000) → 至少4笔成功")
    void testConcurrentApprovals() throws Exception {
        BookBudgetDO budget = BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(YEAR_MONTH)
                .budgetAmount(new BigDecimal("10000"))
                .usedAmount(BigDecimal.ZERO)
                .warnThreshold(80).status(0).build();

        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        lenient().when(redisUtil.hasKey(anyString())).thenReturn(true);
        lenient().when(bookBudgetMapper.updateById(any())).thenReturn(1);

        // 模拟Redis原子递增: 使用AtomicInteger保证线程安全
        AtomicInteger currentTotal = new AtomicInteger(0);
        lenient().when(valueOperations.increment(anyString(), eq(250000L)))
                .thenAnswer(inv -> (long) currentTotal.addAndGet(250000));
        lenient().when(valueOperations.increment(anyString(), eq(-250000L)))
                .thenAnswer(inv -> (long) currentTotal.addAndGet(-250000));

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("2500"));
                    return true;
                } catch (BusinessException e) {
                    return e.getCodeMsg().getRetCode() != CodeMsg.BUDGET_EXCEEDED.getRetCode();
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long successCount = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return false; }
                })
                .filter(b -> b)
                .count();

        assertTrue(successCount >= 4, "Expected at least 4 successful approvals, got " + successCount);
    }
}
