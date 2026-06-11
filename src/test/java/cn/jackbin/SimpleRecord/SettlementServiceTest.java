package cn.jackbin.SimpleRecord;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.SettlementDO;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import cn.jackbin.SimpleRecord.service.SettlementService;
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
 * 结算算法测试
 *
 * 场景：3个成员(A=1, B=2, C=3)
 * - A记录支出300元，A自己付的（A消费300，A支付300）
 * - B记录支出150元，B自己付的（B消费150，B支付150）
 * - C记录支出90元，A代付的（C消费90，A支付90）
 *
 * 总消费 = 300+150+90 = 540, 人均 = 180
 * A应付180，实付390(300+90)，被欠 210
 * B应付180，实付150，欠 30
 * C应付180，实付0，欠 180
 *
 * 结算结果：B->A: 30, C->A: 180
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SettlementServiceTest {

    @Autowired
    private SettlementService settlementService;
    @Autowired
    private RecordDetailService recordDetailService;

    private static final Integer TEST_BOOK_ID = 99996;
    private static final String TEST_YEAR_MONTH = "2099-05";

    @Before
    public void setUp() {
        cleanTestData();
    }

    @After
    public void tearDown() {
        cleanTestData();
    }

    @Test
    public void testSettlementCalculation() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date occurTime = sdf.parse(TEST_YEAR_MONTH + "-15");

        // A记录支出300元，A自己付的
        RecordDetailDO recordA = RecordDetailDO.builder()
                .userId(1).recordBookId(TEST_BOOK_ID).recordType(1).recordCategory("餐饮")
                .amount(-300.0).occurTime(occurTime)
                .payerUserId(null) // null表示自己付的
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE).status(0)
                .build();
        recordDetailService.save(recordA);

        // B记录支出150元，B自己付的
        RecordDetailDO recordB = RecordDetailDO.builder()
                .userId(2).recordBookId(TEST_BOOK_ID).recordType(1).recordCategory("交通")
                .amount(-150.0).occurTime(occurTime)
                .payerUserId(null)
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE).status(0)
                .build();
        recordDetailService.save(recordB);

        // C记录支出90元，A代付的
        RecordDetailDO recordC = RecordDetailDO.builder()
                .userId(3).recordBookId(TEST_BOOK_ID).recordType(1).recordCategory("购物")
                .amount(-90.0).occurTime(occurTime)
                .payerUserId(1) // A代付
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE).status(0)
                .build();
        recordDetailService.save(recordC);

        // 计算结算
        List<SettlementDO> settlements = settlementService.calculateSettlement(TEST_BOOK_ID, TEST_YEAR_MONTH);

        assertNotNull("结算结果不应为空", settlements);
        assertFalse("应有结算记录", settlements.isEmpty());

        // 验证结算金额总和
        double totalSettlement = settlements.stream().mapToDouble(SettlementDO::getAmount).sum();
        assertEquals("结算总额应为210（30+180）", 210.0, totalSettlement, 0.01);

        // 验证所有结算都是向A支付的
        for (SettlementDO s : settlements) {
            assertEquals("所有结算的收款人应为A(userId=1)", Integer.valueOf(1), s.getToUserId());
        }

        // 验证B向A支付30
        SettlementDO bToA = settlements.stream()
                .filter(s -> s.getFromUserId().equals(2))
                .findFirst().orElse(null);
        assertNotNull("B应向A结算", bToA);
        assertEquals("B应向A支付30", 30.0, bToA.getAmount(), 0.01);

        // 验证C向A支付180
        SettlementDO cToA = settlements.stream()
                .filter(s -> s.getFromUserId().equals(3))
                .findFirst().orElse(null);
        assertNotNull("C应向A结算", cToA);
        assertEquals("C应向A支付180", 180.0, cToA.getAmount(), 0.01);
    }

    @Test
    public void testGenerateAndConfirmSettlement() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date occurTime = sdf.parse(TEST_YEAR_MONTH + "-10");

        // 简单场景：A付了200，B消费200
        RecordDetailDO record = RecordDetailDO.builder()
                .userId(2).recordBookId(TEST_BOOK_ID).recordType(1).recordCategory("餐饮")
                .amount(-200.0).occurTime(occurTime)
                .payerUserId(1) // A代付
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE).status(0)
                .build();
        recordDetailService.save(record);

        // 生成结算
        settlementService.generateSettlement(TEST_BOOK_ID, TEST_YEAR_MONTH, 1);

        // 查询结算记录
        List<SettlementDO> settlements = settlementService.getSettlements(TEST_BOOK_ID, TEST_YEAR_MONTH);
        assertFalse("应有结算记录", settlements.isEmpty());

        SettlementDO settlement = settlements.get(0);
        assertEquals("结算状态应为待结算",
                Integer.valueOf(RecordConstant.SETTLE_PENDING), settlement.getSettleStatus());

        // 确认结算
        settlementService.confirmSettlement(settlement.getId(), 1);

        SettlementDO confirmed = settlementService.getById(settlement.getId());
        assertEquals("结算状态应为已结算",
                Integer.valueOf(RecordConstant.SETTLE_DONE), confirmed.getSettleStatus());
        assertNotNull("应记录结算时间", confirmed.getSettleTime());
    }

    @Test
    public void testNoSettlementWhenBalanced() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date occurTime = sdf.parse(TEST_YEAR_MONTH + "-20");

        // A和B各自消费100，各自付的
        RecordDetailDO r1 = RecordDetailDO.builder()
                .userId(1).recordBookId(TEST_BOOK_ID).recordType(1).recordCategory("餐饮")
                .amount(-100.0).occurTime(occurTime).payerUserId(null)
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE).status(0)
                .build();
        RecordDetailDO r2 = RecordDetailDO.builder()
                .userId(2).recordBookId(TEST_BOOK_ID).recordType(1).recordCategory("餐饮")
                .amount(-100.0).occurTime(occurTime).payerUserId(null)
                .auditStatus(RecordConstant.AUDIT_APPROVED)
                .recoverableStatus(RecordConstant.NOT_RECOVERABLE).status(0)
                .build();
        recordDetailService.save(r1);
        recordDetailService.save(r2);

        List<SettlementDO> settlements = settlementService.calculateSettlement(TEST_BOOK_ID, TEST_YEAR_MONTH);

        // 各自消费各自付，不需要结算
        assertTrue("均衡消费不应产生结算", settlements.isEmpty());
    }

    private void cleanTestData() {
        LambdaQueryWrapper<RecordDetailDO> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(RecordDetailDO::getRecordBookId, TEST_BOOK_ID);
        recordDetailService.remove(recordWrapper);

        LambdaQueryWrapper<SettlementDO> settleWrapper = new LambdaQueryWrapper<>();
        settleWrapper.eq(SettlementDO::getRecordBookId, TEST_BOOK_ID);
        settlementService.remove(settleWrapper);
    }
}
