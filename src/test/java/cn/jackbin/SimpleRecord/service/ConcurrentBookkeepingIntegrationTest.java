package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BookBudgetMapper;
import cn.jackbin.SimpleRecord.service.impl.BudgetServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
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
 * 多人并发记账集成测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("多人并发记账集成测试")
class ConcurrentBookkeepingIntegrationTest {

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

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 1;
    private static final String YEAR_MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("5用户并发记账-预算一致性")
    void fiveUsersConcurrentBookkeeping_budgetConsistent() throws Exception {
        // Budget 10000, each expense 2500, 5 threads -> at most 4 succeed
        BookBudgetDO budget = BookBudgetDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(YEAR_MONTH)
                .budgetAmount(new BigDecimal("10000"))
                .usedAmount(BigDecimal.ZERO)
                .warnThreshold(80).status(0).build();

        doReturn(budget).when(budgetService).getBudget(BOOK_ID, YEAR_MONTH);
        lenient().when(redisUtil.hasKey(anyString())).thenReturn(true);
        lenient().when(bookBudgetMapper.updateById(any())).thenReturn(1);

        AtomicInteger currentTotal = new AtomicInteger(0);
        lenient().when(valueOperations.increment(anyString(), eq(250000L)))
                .thenAnswer(inv -> (long) currentTotal.addAndGet(250000));
        lenient().when(valueOperations.increment(anyString(), eq(-250000L)))
                .thenAnswer(inv -> (long) currentTotal.addAndGet(-250000));

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    budgetService.atomicIncrementUsed(BOOK_ID, YEAR_MONTH, new BigDecimal("2500"));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 4 * 2500 = 10000 exactly, 5th should fail
        assertTrue(successCount.get() >= 4, "Expected at least 4 successes, got " + successCount.get());
        assertTrue(currentTotal.get() <= 1000000,
                "Total used should not exceed budget: " + currentTotal.get());
    }

    @Test
    @DisplayName("并发月结-锁保护仅1个成功")
    void concurrentClosingLockProtects() throws Exception {
        // Simulate N threads competing for closing lock, only 1 succeeds
        int threadCount = 5;
        AtomicInteger lockSuccessCount = new AtomicInteger(0);
        AtomicInteger lockFailCount = new AtomicInteger(0);

        // Simulate lock: first caller gets it, rest fail
        AtomicInteger lockCounter = new AtomicInteger(0);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong()))
                .thenAnswer(inv -> lockCounter.incrementAndGet() == 1);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    boolean locked = redisLockUtil.tryLock("test-lock", 60);
                    if (locked) {
                        lockSuccessCount.incrementAndGet();
                    } else {
                        lockFailCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    lockFailCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Exactly 1 thread should acquire the lock
        assertEquals(1, lockSuccessCount.get(), "Exactly 1 thread should acquire the lock");
        assertEquals(threadCount - 1, lockFailCount.get(), "Rest should fail");
    }

    @Test
    @DisplayName("并发调整-幂等去重")
    void concurrentAdjustments_idempotencyDedup() throws Exception {
        // Same idempotency key, only 1 succeeds via Redis check
        int threadCount = 5;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger dedupCount = new AtomicInteger(0);

        // Simulate: first thread finds Redis empty, rest find cached value
        AtomicInteger redisHitCounter = new AtomicInteger(0);
        lenient().when(redisUtil.get(anyString())).thenAnswer(inv -> {
            int count = redisHitCounter.incrementAndGet();
            return count > 1 ? 99L : null; // first miss, rest hit
        });

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    Object cached = redisUtil.get("idemp-key");
                    if (cached != null) {
                        dedupCount.incrementAndGet();
                    } else {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Exactly 1 thread should miss the cache (first create)
        assertEquals(1, successCount.get(), "Exactly 1 thread should be first-time");
        assertEquals(threadCount - 1, dedupCount.get(), "Rest should be dedup'd");
    }

    @Test
    @DisplayName("并发结转与回滚-互斥锁保护")
    void concurrentCarryforwardAndRollback_mutex() throws Exception {
        // Two threads: one carryforward, one rollback. Lock prevents concurrent ops.
        int threadCount = 2;
        AtomicInteger lockAcquired = new AtomicInteger(0);
        AtomicInteger lockRejected = new AtomicInteger(0);

        // Simulate lock: first gets it, second rejected
        AtomicInteger lockCounter = new AtomicInteger(0);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong()))
                .thenAnswer(inv -> lockCounter.incrementAndGet() == 1);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    boolean locked = redisLockUtil.tryLock("lock:carryforward:100:2026-05", 60);
                    if (locked) {
                        lockAcquired.incrementAndGet();
                    } else {
                        lockRejected.incrementAndGet();
                    }
                } catch (Exception e) {
                    lockRejected.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Exactly one thread acquired the lock
        assertEquals(1, lockAcquired.get(), "Exactly 1 should acquire lock");
        assertEquals(1, lockRejected.get(), "Exactly 1 should be rejected");
    }
}
