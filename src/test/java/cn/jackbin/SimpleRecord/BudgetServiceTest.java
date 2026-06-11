package cn.jackbin.SimpleRecord;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.dto.BudgetExecutionDTO;
import cn.jackbin.SimpleRecord.dto.BudgetWarnDTO;
import cn.jackbin.SimpleRecord.entity.BudgetDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 预算超限测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class BudgetServiceTest {

    @Autowired
    private BudgetService budgetService;
    @Autowired
    private RecordDetailService recordDetailService;

    private static final Integer TEST_BOOK_ID = 99999;
    private static final String TEST_YEAR_MONTH = "2099-01";
    private static final String TEST_CATEGORY = "测试餐饮";

    @Before
    public void setUp() {
        cleanTestData();
    }

    @After
    public void tearDown() {
        cleanTestData();
    }

    @Test
    public void testBudgetNotExceeded() {
        // 创建1000元总预算，预警阈值80%
        budgetService.addBudget(TEST_BOOK_ID, RecordConstant.BUDGET_TYPE_TOTAL,
                null, null, TEST_YEAR_MONTH, 1000.0, 80);

        // 添加900元已入账支出
        addTestRecord(-900.0, RecordConstant.AUDIT_APPROVED);

        // 验证未超限
        boolean overLimit = budgetService.checkOverLimit(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertTrue("900/1000=90%已超过80%预警阈值", overLimit);

        // 清除缓存后再次测试较小金额
        budgetService.invalidateCache(TEST_BOOK_ID, TEST_YEAR_MONTH);
        cleanTestRecords();
        addTestRecord(-700.0, RecordConstant.AUDIT_APPROVED);

        boolean overLimit2 = budgetService.checkOverLimit(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertFalse("700/1000=70%未超过80%预警阈值", overLimit2);
    }

    @Test
    public void testBudgetExceeded() {
        // 创建1000元总预算
        budgetService.addBudget(TEST_BOOK_ID, RecordConstant.BUDGET_TYPE_TOTAL,
                null, null, TEST_YEAR_MONTH, 1000.0, 80);

        // 添加1100元已入账支出（超出预算）
        addTestRecord(-600.0, RecordConstant.AUDIT_APPROVED);
        addTestRecord(-500.0, RecordConstant.AUDIT_APPROVED);

        budgetService.invalidateCache(TEST_BOOK_ID, TEST_YEAR_MONTH);

        // 验证超限
        boolean overLimit = budgetService.checkOverLimit(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertTrue("1100/1000已超出预算", overLimit);

        // 验证预警信息
        List<BudgetWarnDTO> warnings = budgetService.getOverLimitWarnings(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertNotNull("应返回预警列表", warnings);
        assertFalse("预警列表不应为空", warnings.isEmpty());
        assertTrue("使用百分比应超过100", warnings.get(0).getUsedPercent() >= 100);
    }

    @Test
    public void testCategoryBudget() {
        // 创建分类预算
        budgetService.addBudget(TEST_BOOK_ID, RecordConstant.BUDGET_TYPE_CATEGORY,
                TEST_CATEGORY, null, TEST_YEAR_MONTH, 500.0, 80);

        // 添加该分类的支出
        addTestRecordWithCategory(-450.0, TEST_CATEGORY, RecordConstant.AUDIT_APPROVED);

        budgetService.invalidateCache(TEST_BOOK_ID, TEST_YEAR_MONTH);

        List<BudgetExecutionDTO> execution = budgetService.getBudgetExecutionByCategory(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertNotNull("分类预算执行报告不应为空", execution);
        assertFalse("应有执行数据", execution.isEmpty());
        assertEquals("分类名称应匹配", TEST_CATEGORY, execution.get(0).getCategoryName());
    }

    @Test
    public void testPendingRecordNotCountedInBudget() {
        // 创建总预算
        budgetService.addBudget(TEST_BOOK_ID, RecordConstant.BUDGET_TYPE_TOTAL,
                null, null, TEST_YEAR_MONTH, 1000.0, 80);

        // 添加待审核记录（不应计入预算）
        addTestRecord(-900.0, RecordConstant.AUDIT_PENDING);

        budgetService.invalidateCache(TEST_BOOK_ID, TEST_YEAR_MONTH);

        boolean overLimit = budgetService.checkOverLimit(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertFalse("待审核记录不应计入预算", overLimit);
    }

    private void addTestRecord(Double amount, Integer auditStatus) {
        addTestRecordWithCategory(amount, TEST_CATEGORY, auditStatus);
    }

    private void addTestRecordWithCategory(Double amount, String category, Integer auditStatus) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date occurTime = sdf.parse(TEST_YEAR_MONTH + "-15");
            RecordDetailDO record = RecordDetailDO.builder()
                    .userId(1)
                    .recordBookId(TEST_BOOK_ID)
                    .recordType(1)
                    .recordCategory(category)
                    .amount(amount)
                    .occurTime(occurTime)
                    .auditStatus(auditStatus)
                    .recoverableStatus(RecordConstant.NOT_RECOVERABLE)
                    .status(0)
                    .build();
            recordDetailService.save(record);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanTestRecords() {
        LambdaQueryWrapper<RecordDetailDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecordDetailDO::getRecordBookId, TEST_BOOK_ID);
        recordDetailService.remove(wrapper);
    }

    private void cleanTestData() {
        cleanTestRecords();
        LambdaQueryWrapper<BudgetDO> budgetWrapper = new LambdaQueryWrapper<>();
        budgetWrapper.eq(BudgetDO::getRecordBookId, TEST_BOOK_ID);
        budgetService.remove(budgetWrapper);
        budgetService.invalidateCache(TEST_BOOK_ID, TEST_YEAR_MONTH);
    }
}
