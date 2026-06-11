package cn.jackbin.SimpleRecord;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.MonthLockService;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

/**
 * 月结锁定测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class MonthLockServiceTest {

    @Autowired
    private MonthLockService monthLockService;
    @Autowired
    private RecordDetailService recordDetailService;

    private static final Integer TEST_BOOK_ID = 99998;
    private static final String TEST_YEAR_MONTH = "2099-06";
    private static final String OTHER_YEAR_MONTH = "2099-07";

    @Before
    public void setUp() {
        // 确保测试前没有锁
        try {
            monthLockService.unlockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);
        } catch (Exception ignored) {}
    }

    @After
    public void tearDown() {
        try {
            monthLockService.unlockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);
        } catch (Exception ignored) {}
    }

    @Test
    public void testLockMonth() {
        assertFalse("锁定前应为未锁定状态", monthLockService.isLocked(TEST_BOOK_ID, TEST_YEAR_MONTH));

        monthLockService.lockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);

        assertTrue("锁定后应为锁定状态", monthLockService.isLocked(TEST_BOOK_ID, TEST_YEAR_MONTH));
    }

    @Test
    public void testLockedMonthPreventsModification() {
        monthLockService.lockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);

        try {
            monthLockService.checkNotLocked(TEST_BOOK_ID, TEST_YEAR_MONTH);
            fail("应抛出月份已锁定异常");
        } catch (BusinessException e) {
            assertEquals("错误码应为SHARED_BOOK_MONTH_LOCKED",
                    CodeMsg.SHARED_BOOK_MONTH_LOCKED.getRetCode(), e.getCodeMsg().getRetCode());
        }
    }

    @Test
    public void testLockDoesNotAffectOtherMonths() {
        monthLockService.lockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);

        // 其他月份不受影响
        assertFalse("其他月份不应被锁定", monthLockService.isLocked(TEST_BOOK_ID, OTHER_YEAR_MONTH));

        // 其他月份可以正常操作
        try {
            monthLockService.checkNotLocked(TEST_BOOK_ID, OTHER_YEAR_MONTH);
        } catch (BusinessException e) {
            fail("其他月份不应抛出锁定异常");
        }
    }

    @Test
    public void testUnlockRestoresModification() {
        monthLockService.lockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);
        assertTrue("锁定后应为锁定状态", monthLockService.isLocked(TEST_BOOK_ID, TEST_YEAR_MONTH));

        monthLockService.unlockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);
        assertFalse("解锁后应为未锁定状态", monthLockService.isLocked(TEST_BOOK_ID, TEST_YEAR_MONTH));

        // 解锁后可以正常操作
        try {
            monthLockService.checkNotLocked(TEST_BOOK_ID, TEST_YEAR_MONTH);
        } catch (BusinessException e) {
            fail("解锁后不应抛出锁定异常");
        }
    }

    @Test
    public void testDuplicateLockThrowsException() {
        monthLockService.lockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);

        try {
            monthLockService.lockMonth(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);
            fail("重复锁定应抛出异常");
        } catch (BusinessException e) {
            assertEquals("错误码应为SHARED_BOOK_MONTH_LOCKED",
                    CodeMsg.SHARED_BOOK_MONTH_LOCKED.getRetCode(), e.getCodeMsg().getRetCode());
        }
    }
}
