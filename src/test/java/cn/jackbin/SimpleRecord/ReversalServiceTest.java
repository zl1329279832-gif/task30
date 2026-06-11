package cn.jackbin.SimpleRecord;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.ReversalApplicationDO;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import cn.jackbin.SimpleRecord.service.ReversalApplicationService;
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

import static org.junit.Assert.*;

/**
 * 冲正恢复统计测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ReversalServiceTest {

    @Autowired
    private ReversalApplicationService reversalApplicationService;
    @Autowired
    private RecordDetailService recordDetailService;
    @Autowired
    private BudgetService budgetService;

    private static final Integer TEST_BOOK_ID = 99997;
    private static final String TEST_YEAR_MONTH = "2099-03";

    @Before
    public void setUp() {
        cleanTestData();
    }

    @After
    public void tearDown() {
        cleanTestData();
    }

    @Test
    public void testReversalCreatesOffsettingEntry() throws Exception {
        // 创建一条已入账的支出记录 -500
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date occurTime = sdf.parse(TEST_YEAR_MONTH + "-15");

        RecordDetailDO original = RecordDetailDO.builder()
                .userId(1)
                .recordBookId(TEST_BOOK_ID)
                .recordType(1)
                .recordCategory("餐饮")
                .amount(-500.0)
                .occurTime(occurTime)
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE)
                .status(0)
                .build();
        recordDetailService.save(original);
        Long originalId = original.getId();
        assertNotNull("原始记录应保存成功", originalId);

        // 提交冲正申请
        reversalApplicationService.apply(TEST_BOOK_ID, originalId, 1, "录入错误，需要冲正");

        // 获取冲正申请
        LambdaQueryWrapper<ReversalApplicationDO> appWrapper = new LambdaQueryWrapper<>();
        appWrapper.eq(ReversalApplicationDO::getOriginalRecordId, originalId);
        ReversalApplicationDO application = reversalApplicationService.getOne(appWrapper);
        assertNotNull("冲正申请应存在", application);
        assertEquals("申请状态应为待审核", Integer.valueOf(RecordConstant.AUDIT_PENDING), application.getAuditStatus());

        // 审批通过
        reversalApplicationService.approve(application.getId(), 2, "同意冲正");

        // 验证冲正记录
        application = reversalApplicationService.getById(application.getId());
        assertEquals("申请状态应为已通过", Integer.valueOf(RecordConstant.AUDIT_APPROVED), application.getAuditStatus());
        assertNotNull("应生成冲正记录ID", application.getReversalRecordId());

        RecordDetailDO reversalRecord = recordDetailService.getById(application.getReversalRecordId());
        assertNotNull("冲正记录应存在", reversalRecord);
        assertEquals("冲正记录金额应为+500", Double.valueOf(500.0), reversalRecord.getAmount());
        assertEquals("冲正记录审核状态应为已入账",
                Integer.valueOf(RecordConstant.AUDIT_APPROVED), reversalRecord.getAuditStatus());

        // 验证原始记录状态
        RecordDetailDO updatedOriginal = recordDetailService.getById(originalId);
        assertEquals("原始记录应标记为已冲正",
                Integer.valueOf(RecordConstant.AUDIT_REVERSED), updatedOriginal.getAuditStatus());

        // 验证净效果：原始-500 + 冲正+500 = 0
        LambdaQueryWrapper<RecordDetailDO> sumWrapper = new LambdaQueryWrapper<>();
        sumWrapper.eq(RecordDetailDO::getRecordBookId, TEST_BOOK_ID)
                .in(RecordDetailDO::getAuditStatus, RecordConstant.AUDIT_APPROVED, RecordConstant.AUDIT_REVERSED);
        double netAmount = recordDetailService.list(sumWrapper).stream()
                .mapToDouble(RecordDetailDO::getAmount)
                .sum();
        assertEquals("冲正后净金额应为0", 0.0, netAmount, 0.01);
    }

    private void cleanTestData() {
        LambdaQueryWrapper<RecordDetailDO> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(RecordDetailDO::getRecordBookId, TEST_BOOK_ID);
        recordDetailService.remove(recordWrapper);

        LambdaQueryWrapper<ReversalApplicationDO> appWrapper = new LambdaQueryWrapper<>();
        appWrapper.eq(ReversalApplicationDO::getRecordBookId, TEST_BOOK_ID);
        reversalApplicationService.remove(appWrapper);
    }
}
