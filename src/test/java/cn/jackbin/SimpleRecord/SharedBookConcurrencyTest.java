package cn.jackbin.SimpleRecord;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 多人并发记账测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SharedBookConcurrencyTest {

    @Autowired
    private RecordBookService recordBookService;
    @Autowired
    private RecordDetailService recordDetailService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;

    @Test
    public void testConcurrentRecordInsertion() throws InterruptedException {
        // 创建共享账本
        int ownerId = 1;
        recordBookService.addSharedBook(ownerId, "并发测试账本", "并发测试", 1);

        // 获取刚创建的账本
        List<RecordBookDO> books = recordBookService.getSharedBooksByUser(ownerId);
        RecordBookDO sharedBook = books.stream()
                .filter(b -> "并发测试账本".equals(b.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull("共享账本应创建成功", sharedBook);
        assertEquals("账本类型应为共享", Integer.valueOf(RecordConstant.BOOK_TYPE_SHARED), sharedBook.getBookType());

        Integer bookId = sharedBook.getId().intValue();

        // 添加测试成员
        try {
            sharedBookMemberService.addMember(bookId, 2, RecordConstant.PERM_ALL, "成员2");
        } catch (Exception ignored) {
            // 成员可能已存在
        }

        int threadCount = 5;
        int recordsPerThread = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            final int userId = (threadIdx % 2 == 0) ? ownerId : 2;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    for (int j = 0; j < recordsPerThread; j++) {
                        try {
                            RecordDetailDO record = new RecordDetailDO();
                            record.setUserId(userId);
                            record.setRecordBookId(bookId);
                            record.setRecordType(1); // 假设类型ID
                            record.setRecordCategory("餐饮");
                            record.setAmount(-100.0 - threadIdx * 10 - j);
                            record.setOccurTime(new Date());
                            record.setAuditStatus(RecordConstant.AUDIT_PENDING);
                            record.setRecoverableStatus(RecordConstant.NOT_RECOVERABLE);
                            record.setStatus(0);
                            recordDetailService.save(record);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 同时启动所有线程
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // 验证所有记录都成功插入
        int expectedTotal = threadCount * recordsPerThread;
        assertEquals("所有并发插入应成功", expectedTotal, successCount.get());
        assertEquals("不应有失败的插入", 0, failCount.get());

        // 验证数据库中的记录数
        LambdaQueryWrapper<RecordDetailDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecordDetailDO::getRecordBookId, bookId)
                .eq(RecordDetailDO::getAuditStatus, RecordConstant.AUDIT_PENDING);
        int dbCount = recordDetailService.count(wrapper);
        assertTrue("数据库中应存在并发插入的记录", dbCount >= expectedTotal);

        // 清理测试数据
        recordDetailService.remove(wrapper);
    }
}
