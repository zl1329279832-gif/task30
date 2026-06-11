package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.service.impl.MemberSettlementServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 成员结算服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成员结算服务测试")
class MemberSettlementServiceTest {

    @InjectMocks
    private MemberSettlementServiceImpl memberSettlementService;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private SharedBookService sharedBookService;

    private static final Integer BOOK_ID = 1;
    private static final String YEAR_MONTH = "2026-06";

    @Test
    @DisplayName("成员间借贷结算 → 净余额正确")
    void testCalculateNetBalances() {
        // 模拟: 用户A垫付500给用户B, 用户B垫付300给用户A
        // 记录1: A的账户, source=B, amount=-500 (A流出500给B)
        RecordDetailDO r1 = new RecordDetailDO();
        r1.setUserId(100);
        r1.setAmount(-500.0);
        r1.setSourceAccountId(2);
        r1.setRecordAccountId(1);
        r1.setReviewStatus(RecordConstant.REVIEW_POSTED);

        // 记录2: A的账户, source=B, amount=500 (A收到500)
        RecordDetailDO r2 = new RecordDetailDO();
        r2.setUserId(100);
        r2.setAmount(500.0);
        r2.setSourceAccountId(2);
        r2.setRecordAccountId(1);
        r2.setReviewStatus(RecordConstant.REVIEW_POSTED);

        // 记录3: B的账户, amount=-300 (B流出300)
        RecordDetailDO r3 = new RecordDetailDO();
        r3.setUserId(200);
        r3.setAmount(-300.0);
        r3.setSourceAccountId(1);
        r3.setRecordAccountId(2);
        r3.setReviewStatus(RecordConstant.REVIEW_POSTED);

        // 记录4: B的账户, amount=300 (B收到300)
        RecordDetailDO r4 = new RecordDetailDO();
        r4.setUserId(200);
        r4.setAmount(300.0);
        r4.setSourceAccountId(1);
        r4.setRecordAccountId(2);
        r4.setReviewStatus(RecordConstant.REVIEW_POSTED);

        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(r1, r2, r3, r4));

        Map<Integer, BigDecimal> balances = memberSettlementService.calculateNetBalances(BOOK_ID, YEAR_MONTH);

        assertNotNull(balances);
        // A: -500 + 500 = 0
        BigDecimal balanceA = balances.getOrDefault(100, BigDecimal.ZERO);
        assertEquals(0, balanceA.compareTo(BigDecimal.ZERO));

        // B: -300 + 300 = 0
        BigDecimal balanceB = balances.getOrDefault(200, BigDecimal.ZERO);
        assertEquals(0, balanceB.compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("无记录 → 空余额")
    void testEmptyBalances() {
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(List.of());

        Map<Integer, BigDecimal> balances = memberSettlementService.calculateNetBalances(BOOK_ID, YEAR_MONTH);

        assertTrue(balances.isEmpty());
    }
}
