package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.MemberSettlementSnapshotDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.MemberSettlementSnapshotMapper;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成员结算服务实现
 * 复用现有 Loan/Transfer 内部转账机制进行成员间结算
 */
@Service
public class MemberSettlementServiceImpl implements MemberSettlementService {

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    @Lazy
    private SharedBookService sharedBookService;

    @Autowired
    private MemberSettlementSnapshotMapper memberSettlementSnapshotMapper;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Override
    public Map<Integer, BigDecimal> calculateNetBalances(Integer bookId, String yearMonth) {
        Map<Integer, BigDecimal> balances = new HashMap<>();

        // 查询该账本中该月所有transfer/loan类型的记录 (已入账或无需审核)
        List<RecordDetailDO> records = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                .eq("record_book_id", bookId)
                .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED)
                .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                .isNotNull("source_account_id")); // 仅transfer/loan有关联账户

        // 每条记录: userId是操作人, amount为正表示资金流入该账户
        for (RecordDetailDO r : records) {
            if (r.getAmount() != null && r.getAmount() > 0 && r.getUserId() != null) {
                BigDecimal amt = BigDecimal.valueOf(r.getAmount());
                balances.merge(r.getUserId(), amt, BigDecimal::add);
            } else if (r.getAmount() != null && r.getAmount() < 0 && r.getUserId() != null) {
                BigDecimal amt = BigDecimal.valueOf(Math.abs(r.getAmount()));
                balances.merge(r.getUserId(), amt.negate(), BigDecimal::add);
            }
        }

        return balances;
    }

    @Override
    @Transactional
    public List<Long> executeSettlement(Integer bookId, Integer operatorId, String yearMonth) {
        sharedBookService.checkPermission(bookId, operatorId, RecordConstant.PERM_SETTLEMENT);

        Map<Integer, BigDecimal> balances = calculateNetBalances(bookId, yearMonth);
        if (balances.isEmpty()) {
            return Collections.emptyList();
        }

        // 分离债权人和债务人
        List<Map.Entry<Integer, BigDecimal>> creditors = new ArrayList<>(); // 正余额 = 应收
        List<Map.Entry<Integer, BigDecimal>> debtors = new ArrayList<>();   // 负余额 = 应付

        for (Map.Entry<Integer, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            } else if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().abs()));
            }
        }

        // 排序: 大额优先
        creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        debtors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<Long> recordIds = new ArrayList<>();

        // 贪心匹配结算
        int ci = 0, di = 0;
        while (ci < creditors.size() && di < debtors.size()) {
            Map.Entry<Integer, BigDecimal> creditor = creditors.get(ci);
            Map.Entry<Integer, BigDecimal> debtor = debtors.get(di);

            BigDecimal settleAmount = creditor.getValue().min(debtor.getValue());

            // 记录结算信息 (实际转账由前端确认后通过现有transfer API执行)
            // 这里仅记录结算建议
            recordIds.add(settleAmount.longValue()); // placeholder

            creditor.setValue(creditor.getValue().subtract(settleAmount));
            debtor.setValue(debtor.getValue().subtract(settleAmount));

            if (creditor.getValue().compareTo(BigDecimal.ZERO) == 0) ci++;
            if (debtor.getValue().compareTo(BigDecimal.ZERO) == 0) di++;
        }

        return recordIds;
    }

    @Override
    public void captureSnapshots(Integer bookId, String yearMonth) {
        // 查询所有成员 (含已移除)
        List<SharedBookMemberDO> members = sharedBookService.list(new QueryWrapper<SharedBookMemberDO>()
                .eq("book_id", bookId));

        List<MemberSettlementSnapshotDO> snapshots = new ArrayList<>();

        for (SharedBookMemberDO member : members) {
            // 查询该成员在该期的所有有效记录
            List<RecordDetailDO> memberRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .eq("user_id", member.getUserId())
                    .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                    .isNull("target_account_id"));

            long totalIncomeCents = 0;
            long totalExpendCents = 0;
            for (RecordDetailDO r : memberRecords) {
                if (r.getAmount() != null) {
                    if (r.getAmount() > 0) {
                        totalIncomeCents += (long)(r.getAmount() * 100);
                    } else {
                        totalExpendCents += (long)(Math.abs(r.getAmount()) * 100);
                    }
                }
            }

            // 待审核
            long pendingCount = recordDetailService.count(new QueryWrapper<RecordDetailDO>()
                    .eq("record_book_id", bookId)
                    .eq("user_id", member.getUserId())
                    .eq("review_status", RecordConstant.REVIEW_PENDING)
                    .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth));

            BigDecimal pendingAmount = BigDecimal.ZERO;
            if (pendingCount > 0) {
                List<RecordDetailDO> pendingRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                        .eq("record_book_id", bookId)
                        .eq("user_id", member.getUserId())
                        .eq("review_status", RecordConstant.REVIEW_PENDING)
                        .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                        .isNull("target_account_id"));
                for (RecordDetailDO r : pendingRecords) {
                    if (r.getAmount() != null && r.getAmount() < 0) {
                        pendingAmount = pendingAmount.add(BigDecimal.valueOf(Math.abs(r.getAmount())));
                    }
                }
            }

            MemberSettlementSnapshotDO snapshot = MemberSettlementSnapshotDO.builder()
                    .bookId(bookId)
                    .yearMonth(yearMonth)
                    .memberUserId(member.getUserId())
                    .totalIncome(totalIncomeCents)
                    .totalExpend(totalExpendCents)
                    .netResponsibility(totalIncomeCents - totalExpendCents)
                    .pendingCount((int) pendingCount)
                    .pendingAmount(pendingAmount.multiply(BigDecimal.valueOf(100)).longValue())
                    .budgetUsed(totalExpendCents)
                    .budgetOverspent(0L)
                    .snapshotVersion(1)
                    .recalcFlag(0)
                    .build();
            snapshots.add(snapshot);
        }

        if (!snapshots.isEmpty()) {
            memberSettlementSnapshotMapper.batchInsert(snapshots);

            auditLogService.log(bookId, 0,
                    RecordConstant.ACTION_SNAPSHOT_CAPTURED, "CLOSING", null,
                    "{\"yearMonth\":\"" + yearMonth + "\",\"memberCount\":" + snapshots.size() + "}");
        }
    }

    @Override
    public MemberSettlementSnapshotDO getHistoricalResponsibility(Integer bookId, String yearMonth, Integer memberUserId) {
        MemberSettlementSnapshotDO snapshot = memberSettlementSnapshotMapper.selectByPeriodAndMember(bookId, yearMonth, memberUserId);
        if (snapshot == null) {
            throw new BusinessException(CodeMsg.SETTLEMENT_SNAPSHOT_NOT_FOUND);
        }
        return snapshot;
    }

    @Override
    public List<MemberSettlementSnapshotDO> getMemberResponsibilityTimeline(Integer bookId, Integer memberUserId) {
        return memberSettlementSnapshotMapper.selectMemberHistory(bookId, memberUserId);
    }

    @Override
    public void recalculateSnapshot(Integer bookId, String yearMonth, Integer memberUserId, String reason) {
        MemberSettlementSnapshotDO existing = memberSettlementSnapshotMapper.selectByPeriodAndMember(bookId, yearMonth, memberUserId);
        if (existing == null) {
            throw new BusinessException(CodeMsg.SETTLEMENT_SNAPSHOT_NOT_FOUND);
        }

        // 重新聚合
        List<RecordDetailDO> memberRecords = recordDetailService.list(new QueryWrapper<RecordDetailDO>()
                .eq("record_book_id", bookId)
                .eq("user_id", memberUserId)
                .in("review_status", RecordConstant.REVIEW_NONE, RecordConstant.REVIEW_POSTED)
                .apply("DATE_FORMAT(occur_time, '%Y-%m') = {0}", yearMonth)
                .isNull("target_account_id"));

        long totalIncomeCents = 0;
        long totalExpendCents = 0;
        for (RecordDetailDO r : memberRecords) {
            if (r.getAmount() != null) {
                if (r.getAmount() > 0) {
                    totalIncomeCents += (long)(r.getAmount() * 100);
                } else {
                    totalExpendCents += (long)(Math.abs(r.getAmount()) * 100);
                }
            }
        }

        int newVersion = existing.getSnapshotVersion() + 1;
        MemberSettlementSnapshotDO newSnapshot = MemberSettlementSnapshotDO.builder()
                .bookId(bookId)
                .yearMonth(yearMonth)
                .memberUserId(memberUserId)
                .totalIncome(totalIncomeCents)
                .totalExpend(totalExpendCents)
                .netResponsibility(totalIncomeCents - totalExpendCents)
                .pendingCount(existing.getPendingCount())
                .pendingAmount(existing.getPendingAmount())
                .budgetUsed(totalExpendCents)
                .budgetOverspent(existing.getBudgetOverspent())
                .snapshotVersion(newVersion)
                .recalcFlag(1)
                .build();
        memberSettlementSnapshotMapper.insert(newSnapshot);

        auditLogService.log(bookId, 0,
                RecordConstant.ACTION_SNAPSHOT_RECALCULATED, "CLOSING", null,
                "{\"yearMonth\":\"" + yearMonth + "\",\"memberUserId\":" + memberUserId
                + ",\"oldVersion\":" + existing.getSnapshotVersion() + ",\"newVersion\":" + newVersion + "}");
    }

    @Override
    public List<MemberSettlementSnapshotDO> getPeriodSnapshots(Integer bookId, String yearMonth) {
        return memberSettlementSnapshotMapper.selectByPeriod(bookId, yearMonth);
    }
}
