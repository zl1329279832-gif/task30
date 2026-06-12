package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.MemberSettlementSnapshotDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.MemberSettlementSnapshotMapper;
import cn.jackbin.SimpleRecord.service.impl.MemberSettlementServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 成员责任快照服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成员责任快照服务测试")
class MemberSettlementSnapshotTest {

    @Spy
    @InjectMocks
    private MemberSettlementServiceImpl settlementService;

    @Mock
    private RecordDetailService recordDetailService;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private MemberSettlementSnapshotMapper memberSettlementSnapshotMapper;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 100;
    private static final String YEAR_MONTH = "2026-05";
    private static final Integer MEMBER_A = 200;
    private static final Integer MEMBER_B = 300;

    @Test
    @DisplayName("捕获快照-包含已移除成员")
    void captureSnapshots_includesRemovedMembers() {
        // Member A active, Member B removed (status=1)
        SharedBookMemberDO memberA = SharedBookMemberDO.builder()
                .bookId(BOOK_ID).userId(MEMBER_A).status(0).build();
        SharedBookMemberDO memberB = SharedBookMemberDO.builder()
                .bookId(BOOK_ID).userId(MEMBER_B).status(1).build(); // removed

        when(sharedBookService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(memberA, memberB));
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(memberSettlementSnapshotMapper.batchInsert(anyList())).thenReturn(2);

        settlementService.captureSnapshots(BOOK_ID, YEAR_MONTH);

        // Both members should be included in snapshot batch insert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemberSettlementSnapshotDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberSettlementSnapshotMapper).batchInsert(captor.capture());
        List<MemberSettlementSnapshotDO> snapshots = captor.getValue();
        assertEquals(2, snapshots.size());
    }

    @Test
    @DisplayName("捕获快照-正确聚合收支")
    void captureSnapshots_correctAggregation() {
        SharedBookMemberDO memberA = SharedBookMemberDO.builder()
                .bookId(BOOK_ID).userId(MEMBER_A).status(0).build();
        when(sharedBookService.list(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(memberA));

        // Income: 500, Expend: 300
        RecordDetailDO incomeRecord = new RecordDetailDO();
        incomeRecord.setAmount(500.0);
        incomeRecord.setUserId(MEMBER_A);
        RecordDetailDO expendRecord = new RecordDetailDO();
        expendRecord.setAmount(-300.0);
        expendRecord.setUserId(MEMBER_A);

        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(incomeRecord, expendRecord));
        when(recordDetailService.count(any(QueryWrapper.class))).thenReturn(0);
        when(memberSettlementSnapshotMapper.batchInsert(anyList())).thenReturn(1);

        settlementService.captureSnapshots(BOOK_ID, YEAR_MONTH);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemberSettlementSnapshotDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberSettlementSnapshotMapper).batchInsert(captor.capture());
        MemberSettlementSnapshotDO snapshot = captor.getValue().get(0);
        assertEquals(50000L, snapshot.getTotalIncome());   // 500 * 100
        assertEquals(30000L, snapshot.getTotalExpend());    // 300 * 100
        assertEquals(20000L, snapshot.getNetResponsibility()); // 50000 - 30000
    }

    @Test
    @DisplayName("查询历史责任-成员被移除后仍可查询")
    void getHistoricalResponsibility_afterMemberRemoved() {
        MemberSettlementSnapshotDO snapshot = MemberSettlementSnapshotDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(YEAR_MONTH)
                .memberUserId(MEMBER_B).totalIncome(10000L)
                .totalExpend(5000L).netResponsibility(5000L)
                .snapshotVersion(1).build();
        when(memberSettlementSnapshotMapper.selectByPeriodAndMember(BOOK_ID, YEAR_MONTH, MEMBER_B))
                .thenReturn(snapshot);

        MemberSettlementSnapshotDO result = settlementService.getHistoricalResponsibility(
                BOOK_ID, YEAR_MONTH, MEMBER_B);

        assertNotNull(result);
        assertEquals(MEMBER_B, result.getMemberUserId());
        assertEquals(10000L, result.getTotalIncome());
    }

    @Test
    @DisplayName("查询成员时间线-跨期")
    void getMemberTimeline_crossPeriod() {
        MemberSettlementSnapshotDO snap1 = MemberSettlementSnapshotDO.builder()
                .bookId(BOOK_ID).yearMonth("2026-04").memberUserId(MEMBER_A).build();
        MemberSettlementSnapshotDO snap2 = MemberSettlementSnapshotDO.builder()
                .bookId(BOOK_ID).yearMonth("2026-05").memberUserId(MEMBER_A).build();
        when(memberSettlementSnapshotMapper.selectMemberHistory(BOOK_ID, MEMBER_A))
                .thenReturn(Arrays.asList(snap1, snap2));

        List<MemberSettlementSnapshotDO> timeline = settlementService.getMemberResponsibilityTimeline(
                BOOK_ID, MEMBER_A);

        assertEquals(2, timeline.size());
        assertEquals("2026-04", timeline.get(0).getYearMonth());
        assertEquals("2026-05", timeline.get(1).getYearMonth());
    }

    @Test
    @DisplayName("重算快照-版本号递增+recalcFlag标记")
    void recalculateSnapshot_versionIncrement_recalcFlag() {
        MemberSettlementSnapshotDO existing = MemberSettlementSnapshotDO.builder()
                .id(1L).bookId(BOOK_ID).yearMonth(YEAR_MONTH)
                .memberUserId(MEMBER_A).snapshotVersion(1)
                .pendingCount(0).pendingAmount(0L)
                .budgetOverspent(0L)
                .totalIncome(10000L).totalExpend(5000L)
                .build();
        when(memberSettlementSnapshotMapper.selectByPeriodAndMember(BOOK_ID, YEAR_MONTH, MEMBER_A))
                .thenReturn(existing);

        // New aggregation: income=8000, expend=6000
        RecordDetailDO incomeRecord = new RecordDetailDO();
        incomeRecord.setAmount(8000.0);
        RecordDetailDO expendRecord = new RecordDetailDO();
        expendRecord.setAmount(-6000.0);
        when(recordDetailService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(incomeRecord, expendRecord));
        when(memberSettlementSnapshotMapper.insert(any())).thenReturn(1);

        settlementService.recalculateSnapshot(BOOK_ID, YEAR_MONTH, MEMBER_A, "冲正导致差异");

        // Verify version incremented to 2 and recalcFlag = 1
        verify(memberSettlementSnapshotMapper).insert(argThat(snap ->
                snap.getSnapshotVersion() == 2 && snap.getRecalcFlag() == 1));
        verify(auditLogService).log(eq(BOOK_ID), eq(0),
                eq(RecordConstant.ACTION_SNAPSHOT_RECALCULATED), eq("CLOSING"), isNull(), anyString());
    }

    @Test
    @DisplayName("查询某期所有成员快照-返回列表")
    void getPeriodSnapshots_returnsList() {
        MemberSettlementSnapshotDO snap1 = MemberSettlementSnapshotDO.builder()
                .memberUserId(MEMBER_A).build();
        MemberSettlementSnapshotDO snap2 = MemberSettlementSnapshotDO.builder()
                .memberUserId(MEMBER_B).build();
        when(memberSettlementSnapshotMapper.selectByPeriod(BOOK_ID, YEAR_MONTH))
                .thenReturn(Arrays.asList(snap1, snap2));

        List<MemberSettlementSnapshotDO> result = settlementService.getPeriodSnapshots(BOOK_ID, YEAR_MONTH);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("捕获快照-空成员列表-不执行批量插入")
    void captureSnapshots_emptyMembers_noBatchInsert() {
        when(sharedBookService.list(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        settlementService.captureSnapshots(BOOK_ID, YEAR_MONTH);

        verify(memberSettlementSnapshotMapper, never()).batchInsert(anyList());
        verify(auditLogService, never()).log(anyInt(), anyInt(), anyString(), anyString(), any(), anyString());
    }
}
