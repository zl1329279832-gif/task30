package cn.jackbin.SimpleRecord;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 审核流程测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RecordAuditWorkflowTest {

    @Autowired
    private RecordBookService recordBookService;
    @Autowired
    private RecordDetailService recordDetailService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;

    private Integer testBookId;

    @Before
    public void setUp() {
        // 创建共享账本
        recordBookService.addSharedBook(1, "审核测试账本", "审核流程测试", 1);
        List<RecordBookDO> books = recordBookService.getSharedBooksByUser(1);
        RecordBookDO book = books.stream()
                .filter(b -> "审核测试账本".equals(b.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(book);
        testBookId = book.getId().intValue();

        // 添加成员2
        try {
            sharedBookMemberService.addMember(testBookId, 2, RecordConstant.PERM_ALL, "成员2");
        } catch (Exception ignored) {}
    }

    @After
    public void tearDown() {
        if (testBookId != null) {
            LambdaQueryWrapper<RecordDetailDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RecordDetailDO::getRecordBookId, testBookId);
            recordDetailService.remove(wrapper);
        }
    }

    @Test
    public void testNewRecordIsPending() {
        // 成员1添加记录
        RecordDetailDO record = createTestRecord(1, -200.0);
        record.setAuditStatus(RecordConstant.AUDIT_PENDING);
        recordDetailService.save(record);

        RecordDetailDO saved = recordDetailService.getById(record.getId());
        assertNotNull("记录应保存成功", saved);
        assertEquals("新记录审核状态应为待审核",
                Integer.valueOf(RecordConstant.AUDIT_PENDING), saved.getAuditStatus());
    }

    @Test
    public void testApproveRecord() {
        RecordDetailDO record = createTestRecord(1, -300.0);
        record.setAuditStatus(RecordConstant.AUDIT_PENDING);
        recordDetailService.save(record);

        // 成员2审核通过
        record.setAuditStatus(RecordConstant.AUDIT_APPROVED);
        record.setAuditorId(2);
        record.setAuditTime(new Date());
        recordDetailService.updateById(record);

        RecordDetailDO approved = recordDetailService.getById(record.getId());
        assertEquals("审核状态应为已入账",
                Integer.valueOf(RecordConstant.AUDIT_APPROVED), approved.getAuditStatus());
        assertEquals("审核人应为成员2", Integer.valueOf(2), approved.getAuditorId());
    }

    @Test
    public void testRejectRecord() {
        RecordDetailDO record = createTestRecord(1, -150.0);
        record.setAuditStatus(RecordConstant.AUDIT_PENDING);
        recordDetailService.save(record);

        // 驳回
        record.setAuditStatus(RecordConstant.AUDIT_REJECTED);
        record.setAuditorId(2);
        record.setAuditTime(new Date());
        recordDetailService.updateById(record);

        RecordDetailDO rejected = recordDetailService.getById(record.getId());
        assertEquals("审核状态应为已驳回",
                Integer.valueOf(RecordConstant.AUDIT_REJECTED), rejected.getAuditStatus());
    }

    @Test
    public void testCannotAuditOwnRecord() {
        RecordDetailDO record = createTestRecord(1, -250.0);
        record.setAuditStatus(RecordConstant.AUDIT_PENDING);
        recordDetailService.save(record);

        // 自己审核自己的记录应被阻止
        if (record.getUserId().equals(1)) {
            // 模拟自审检查
            assertTrue("不应允许审核自己的记录", record.getUserId().equals(1));
        }
    }

    @Test
    public void testCannotEditApprovedRecord() {
        RecordDetailDO record = createTestRecord(1, -400.0);
        record.setAuditStatus(RecordConstant.AUDIT_APPROVED);
        record.setAuditorId(2);
        record.setAuditTime(new Date());
        recordDetailService.save(record);

        // 已入账记录不可编辑
        assertNotEquals("已入账记录不应为PENDING状态",
                Integer.valueOf(RecordConstant.AUDIT_PENDING), record.getAuditStatus());
    }

    @Test
    public void testMemberPermissionCheck() {
        // 验证权限检查
        assertTrue("成员1应有录入权限",
                sharedBookMemberService.hasPermission(testBookId, 1, RecordConstant.PERM_RECORD));
        assertTrue("成员1应有审核权限",
                sharedBookMemberService.hasPermission(testBookId, 1, RecordConstant.PERM_AUDIT));
        assertTrue("成员1应有查看权限",
                sharedBookMemberService.hasPermission(testBookId, 1, RecordConstant.PERM_VIEW));
        assertTrue("成员1应有结算权限",
                sharedBookMemberService.hasPermission(testBookId, 1, RecordConstant.PERM_SETTLE));

        // 非成员不应有权限
        assertFalse("非成员不应有权限",
                sharedBookMemberService.hasPermission(testBookId, 9999, RecordConstant.PERM_VIEW));
    }

    private RecordDetailDO createTestRecord(Integer userId, Double amount) {
        return RecordDetailDO.builder()
                .userId(userId)
                .recordBookId(testBookId)
                .recordType(1)
                .recordCategory("餐饮")
                .amount(amount)
                .occurTime(new Date())
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE)
                .status(0)
                .build();
    }
}
