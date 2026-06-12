package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.dto.MemberResponsibilitySnapshotDTO;
import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;
import cn.jackbin.SimpleRecord.entity.MemberResponsibilitySnapshotDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.mapper.MemberResponsibilitySnapshotMapper;
import cn.jackbin.SimpleRecord.service.impl.MemberResponsibilitySnapshotServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 成员责任快照服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成员责任快照服务测试")
class MemberResponsibilitySnapshotServiceTest {

    @Spy
    @InjectMocks
    private MemberResponsibilitySnapshotServiceImpl snapshotService;

    @Mock
    private MemberResponsibilitySnapshotMapper snapshotMapper;

    @Mock
    private SharedBookService sharedBookService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private ClosingAdjustmentService closingAdjustmentService;

    @Mock
    private RedisLockUtil redisLockUtil;

    private static final Integer BOOK_ID = 1;
    private static final String YEAR_MONTH = "2026-06";
    private static final Long CLOSING_ID = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(snapshotService, "baseMapper", snapshotMapper);
        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn(true);
        lenient().doReturn(true).when(snapshotService).saveBatch(anyCollection());
    }

    @Test
    @DisplayName("两个成员三个分类 → 正确生成快照")
    void testGenerateSnapshots_twoMembers() {
        doReturn(0).when(snapshotService).count(any(QueryWrapper.class));

        List<Map<String, Object>> groupedData = new ArrayList<>();
        groupedData.add(buildGroupRow(100, "餐饮", 1, 0, 500, 3));
        groupedData.add(buildGroupRow(100, "交通", 1, 0, 200, 2));
        groupedData.add(buildGroupRow(200, "餐饮", 1, 0, 300, 1));
        when(snapshotMapper.queryGroupedSnapshot(BOOK_ID, YEAR_MONTH)).thenReturn(groupedData);

        List<SharedBookMemberDO> members = Arrays.asList(
                buildMember(100, "Alice", "entry,view"),
                buildMember(200, "Bob", "entry,review,view")
        );
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(members);

        snapshotService.generateSnapshots(BOOK_ID, YEAR_MONTH, CLOSING_ID);

        verify(snapshotService).saveBatch(argThat(list -> list.size() == 3));
        verify(auditLogService).log(eq(BOOK_ID), eq(0), eq("RESPONSIBILITY_SNAPSHOT"),
                eq("CLOSING"), eq(CLOSING_ID), anyString());
    }

    @Test
    @DisplayName("空月份 → 无快照生成")
    void testGenerateSnapshots_emptyMonth() {
        doReturn(0).when(snapshotService).count(any(QueryWrapper.class));
        when(snapshotMapper.queryGroupedSnapshot(BOOK_ID, YEAR_MONTH)).thenReturn(Collections.emptyList());
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.emptyList());

        snapshotService.generateSnapshots(BOOK_ID, YEAR_MONTH, CLOSING_ID);

        verify(snapshotService, never()).saveBatch(anyCollection());
    }

    @Test
    @DisplayName("幂等: 快照已存在 → 不重复生成")
    void testGenerateSnapshots_idempotent() {
        doReturn(3).when(snapshotService).count(any(QueryWrapper.class));

        snapshotService.generateSnapshots(BOOK_ID, YEAR_MONTH, CLOSING_ID);

        verify(snapshotMapper, never()).queryGroupedSnapshot(anyInt(), anyString());
    }

    @Test
    @DisplayName("快照冻结成员权限 → 权限变更后历史快照不变")
    void testHistoricalAttribution_permissionChange() {
        doReturn(0).when(snapshotService).count(any(QueryWrapper.class));

        List<Map<String, Object>> groupedData = new ArrayList<>();
        groupedData.add(buildGroupRow(100, "餐饮", 1, 0, 500, 3));
        when(snapshotMapper.queryGroupedSnapshot(BOOK_ID, YEAR_MONTH)).thenReturn(groupedData);

        SharedBookMemberDO member = buildMember(100, "Alice", "entry,review,view,settlement");
        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(Collections.singletonList(member));

        snapshotService.generateSnapshots(BOOK_ID, YEAR_MONTH, CLOSING_ID);

        verify(snapshotService).saveBatch(argThat(list -> {
            MemberResponsibilitySnapshotDO snap = (MemberResponsibilitySnapshotDO) list.iterator().next();
            return "entry,review,view,settlement".equals(snap.getUserPermissions())
                    && "Alice".equals(snap.getUserNickname());
        }));
    }

    @Test
    @DisplayName("重算快照: 无调整 → 原始值不变")
    void testRecalculated_noAdjustments() {
        MemberResponsibilitySnapshotDO snap = buildSnapshot(100, "餐饮", null,
                new BigDecimal("100"), new BigDecimal("500"), 5);
        doReturn(Collections.singletonList(snap)).when(snapshotService).getSnapshots(BOOK_ID, YEAR_MONTH, null);
        when(closingAdjustmentService.getAdjustments(BOOK_ID, YEAR_MONTH)).thenReturn(Collections.emptyList());

        List<MemberResponsibilitySnapshotDTO> result = snapshotService.getRecalculatedSnapshots(BOOK_ID, YEAR_MONTH);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getOriginalIncome().compareTo(result.get(0).getAdjustedIncome()));
        assertEquals(0, result.get(0).getOriginalExpend().compareTo(result.get(0).getAdjustedExpend()));
    }

    @Test
    @DisplayName("重算快照: 冲正调整 → 收入/支出调整后正确")
    void testRecalculated_withReversal() {
        MemberResponsibilitySnapshotDO snap = buildSnapshot(100, "餐饮", 1,
                BigDecimal.ZERO, new BigDecimal("500"), 3);
        doReturn(Collections.singletonList(snap)).when(snapshotService).getSnapshots(BOOK_ID, YEAR_MONTH, null);

        ClosingAdjustmentDO adj = ClosingAdjustmentDO.builder()
                .userId(100).recordCategory("餐饮").recordAccountId(1)
                .adjustmentIncome(BigDecimal.ZERO).adjustmentExpend(new BigDecimal("-200")).build();
        when(closingAdjustmentService.getAdjustments(BOOK_ID, YEAR_MONTH)).thenReturn(Collections.singletonList(adj));

        List<MemberResponsibilitySnapshotDTO> result = snapshotService.getRecalculatedSnapshots(BOOK_ID, YEAR_MONTH);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("500").compareTo(result.get(0).getOriginalExpend()));
        assertEquals(0, new BigDecimal("300").compareTo(result.get(0).getAdjustedExpend()));
        assertEquals(0, new BigDecimal("-200").compareTo(result.get(0).getAdjustmentExpend()));
    }

    @Test
    @DisplayName("多维度快照: 同成员不同分类+账户 → 多行快照")
    void testGenerateSnapshots_multipleDimensions() {
        doReturn(0).when(snapshotService).count(any(QueryWrapper.class));

        List<Map<String, Object>> groupedData = new ArrayList<>();
        groupedData.add(buildGroupRow(100, "餐饮", 1, 0, 300, 2));
        groupedData.add(buildGroupRow(100, "餐饮", 2, 0, 200, 1));
        groupedData.add(buildGroupRow(100, "交通", 1, 0, 100, 1));
        groupedData.add(buildGroupRow(100, "交通", 2, 0, 50, 1));
        when(snapshotMapper.queryGroupedSnapshot(BOOK_ID, YEAR_MONTH)).thenReturn(groupedData);

        when(sharedBookService.listMembers(BOOK_ID)).thenReturn(
                Collections.singletonList(buildMember(100, "Alice", "entry,view")));

        snapshotService.generateSnapshots(BOOK_ID, YEAR_MONTH, CLOSING_ID);

        verify(snapshotService).saveBatch(argThat(list -> list.size() == 4));
    }

    private Map<String, Object> buildGroupRow(int userId, String category, int accountId,
                                               double income, double expend, int count) {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("recordCategory", category);
        row.put("recordAccountId", accountId);
        row.put("totalIncome", new BigDecimal(String.valueOf(income)));
        row.put("totalExpend", new BigDecimal(String.valueOf(expend)));
        row.put("recordCount", count);
        return row;
    }

    private SharedBookMemberDO buildMember(int userId, String nickname, String permissions) {
        SharedBookMemberDO m = new SharedBookMemberDO();
        m.setUserId(userId);
        m.setNickname(nickname);
        m.setPermissions(permissions);
        m.setStatus(0);
        return m;
    }

    private MemberResponsibilitySnapshotDO buildSnapshot(int userId, String category, Integer accountId,
                                                           BigDecimal income, BigDecimal expend, int count) {
        return MemberResponsibilitySnapshotDO.builder()
                .bookId(BOOK_ID).yearMonth(YEAR_MONTH).closingId(CLOSING_ID)
                .userId(userId).userNickname("User" + userId).userPermissions("entry,view")
                .recordCategory(category).recordAccountId(accountId)
                .totalIncome(income).totalExpend(expend).recordCount(count).status(0).build();
    }
}
