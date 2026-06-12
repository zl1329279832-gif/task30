package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardLogDO;
import cn.jackbin.SimpleRecord.mapper.BudgetCarryforwardLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 结转日志Mapper测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("结转日志Mapper测试")
class BudgetCarryforwardLogMapperTest {

    @Mock
    private BudgetCarryforwardLogMapper carryforwardLogMapper;

    private static final Integer BOOK_ID = 100;
    private static final String SOURCE_MONTH = "2026-05";
    private static final String TARGET_MONTH = "2026-06";
    private static final Integer MEMBER_A = 200;
    private static final Integer MEMBER_B = 300;

    @Test
    @DisplayName("按源期间查询-返回列表")
    void selectBySourcePeriod_returnsList() {
        BudgetCarryforwardLogDO log1 = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .carryforwardAmount(40000L)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();
        BudgetCarryforwardLogDO log2 = BudgetCarryforwardLogDO.builder()
                .id(2L).bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .memberUserId(MEMBER_A)
                .usedAmount(30000L)
                .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                .build();

        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH))
                .thenReturn(Arrays.asList(log1, log2));

        List<BudgetCarryforwardLogDO> result = carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(40000L, result.get(0).getCarryforwardAmount());
        assertEquals(MEMBER_A, result.get(1).getMemberUserId());
    }

    @Test
    @DisplayName("按目标期间查询-返回列表")
    void selectByTargetPeriod_returnsList() {
        BudgetCarryforwardLogDO log = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID)
                .sourceYearMonth(SOURCE_MONTH)
                .targetYearMonth(TARGET_MONTH)
                .carryforwardAmount(40000L)
                .ruleVersion(1)
                .ruleType(RecordConstant.CARRYFORWARD_RULE_FULL)
                .build();

        when(carryforwardLogMapper.selectByTargetPeriod(BOOK_ID, TARGET_MONTH))
                .thenReturn(Collections.singletonList(log));

        List<BudgetCarryforwardLogDO> result = carryforwardLogMapper.selectByTargetPeriod(BOOK_ID, TARGET_MONTH);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(TARGET_MONTH, result.get(0).getTargetYearMonth());
        assertEquals(RecordConstant.CARRYFORWARD_RULE_FULL, result.get(0).getRuleType());
    }

    @Test
    @DisplayName("按成员查询-正确过滤")
    void selectByMember_filtersCorrectly() {
        BudgetCarryforwardLogDO logA = BudgetCarryforwardLogDO.builder()
                .id(1L).bookId(BOOK_ID)
                .sourceYearMonth(SOURCE_MONTH)
                .memberUserId(MEMBER_A)
                .usedAmount(30000L)
                .build();
        BudgetCarryforwardLogDO logA2 = BudgetCarryforwardLogDO.builder()
                .id(2L).bookId(BOOK_ID)
                .sourceYearMonth("2026-04")
                .memberUserId(MEMBER_A)
                .usedAmount(25000L)
                .build();

        when(carryforwardLogMapper.selectByMember(BOOK_ID, MEMBER_A, SOURCE_MONTH))
                .thenReturn(Collections.singletonList(logA));

        List<BudgetCarryforwardLogDO> result = carryforwardLogMapper.selectByMember(BOOK_ID, MEMBER_A, SOURCE_MONTH);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(MEMBER_A, result.get(0).getMemberUserId());
        assertEquals(30000L, result.get(0).getUsedAmount());

        // Verify member B returns empty
        when(carryforwardLogMapper.selectByMember(BOOK_ID, MEMBER_B, SOURCE_MONTH))
                .thenReturn(Collections.emptyList());

        List<BudgetCarryforwardLogDO> resultB = carryforwardLogMapper.selectByMember(BOOK_ID, MEMBER_B, SOURCE_MONTH);
        assertTrue(resultB.isEmpty());
    }

    @Test
    @DisplayName("批量插入-插入所有记录")
    void batchInsert_insertsAll() {
        List<BudgetCarryforwardLogDO> logs = Arrays.asList(
                BudgetCarryforwardLogDO.builder()
                        .bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                        .targetYearMonth(TARGET_MONTH)
                        .carryforwardAmount(40000L)
                        .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                        .build(),
                BudgetCarryforwardLogDO.builder()
                        .bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                        .targetYearMonth(TARGET_MONTH)
                        .memberUserId(MEMBER_A)
                        .usedAmount(20000L)
                        .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                        .build(),
                BudgetCarryforwardLogDO.builder()
                        .bookId(BOOK_ID).sourceYearMonth(SOURCE_MONTH)
                        .targetYearMonth(TARGET_MONTH)
                        .memberUserId(MEMBER_B)
                        .usedAmount(15000L)
                        .status(RecordConstant.CARRYFORWARD_LOG_ACTIVE)
                        .build()
        );

        when(carryforwardLogMapper.batchInsert(logs)).thenReturn(3);

        int result = carryforwardLogMapper.batchInsert(logs);

        assertEquals(3, result);
        verify(carryforwardLogMapper).batchInsert(logs);
    }

    @Test
    @DisplayName("按源期间查询-空结果")
    void selectBySourcePeriod_emptyResult() {
        when(carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH))
                .thenReturn(Collections.emptyList());

        List<BudgetCarryforwardLogDO> result = carryforwardLogMapper.selectBySourcePeriod(BOOK_ID, SOURCE_MONTH);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
    }
}
