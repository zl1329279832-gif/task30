package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.dto.MemberResponsibilitySnapshotDTO;
import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;
import cn.jackbin.SimpleRecord.entity.MemberResponsibilitySnapshotDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.MemberResponsibilitySnapshotMapper;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.utils.RedisLockUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成员责任快照服务实现
 */
@Service
public class MemberResponsibilitySnapshotServiceImpl
        extends ServiceImpl<MemberResponsibilitySnapshotMapper, MemberResponsibilitySnapshotDO>
        implements MemberResponsibilitySnapshotService {

    @Autowired
    private MemberResponsibilitySnapshotMapper snapshotMapper;

    @Autowired
    @Lazy
    private SharedBookService sharedBookService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    @Lazy
    private ClosingAdjustmentService closingAdjustmentService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Override
    @Transactional
    public void generateSnapshots(Integer bookId, String yearMonth, Long closingId) {
        String lockKey = RedisKey.LOCK_PREFIX + "snapshot:" + bookId + ":" + yearMonth;
        if (!redisLockUtil.tryLock(lockKey, 30)) {
            throw new BusinessException(CodeMsg.OPERATION_IN_PROGRESS);
        }
        try {
            // 幂等: 已存在则跳过
            long existingCount = count(new QueryWrapper<MemberResponsibilitySnapshotDO>()
                    .eq("closing_id", closingId));
            if (existingCount > 0) {
                return;
            }

            // 查询分组数据
            List<Map<String, Object>> groupedData = snapshotMapper.queryGroupedSnapshot(bookId, yearMonth);

            // 查询所有活跃成员, 建立 userId -> member 映射
            List<SharedBookMemberDO> members = sharedBookService.listMembers(bookId);
            Map<Integer, SharedBookMemberDO> memberMap = new HashMap<>();
            for (SharedBookMemberDO m : members) {
                memberMap.put(m.getUserId(), m);
            }

            // 为每个分组生成快照
            List<MemberResponsibilitySnapshotDO> snapshots = new ArrayList<>();
            for (Map<String, Object> row : groupedData) {
                Integer userId = ((Number) row.get("userId")).intValue();
                String recordCategory = (String) row.get("recordCategory");
                Integer recordAccountId = row.get("recordAccountId") != null
                        ? ((Number) row.get("recordAccountId")).intValue() : null;
                BigDecimal totalIncome = new BigDecimal(row.get("totalIncome").toString());
                BigDecimal totalExpend = new BigDecimal(row.get("totalExpend").toString());
                int recordCount = ((Number) row.get("recordCount")).intValue();

                // 快照成员信息 (即使成员已被移除, 仍用DB查到的历史数据)
                SharedBookMemberDO member = memberMap.get(userId);
                String nickname = member != null ? member.getNickname() : null;
                String permissions = member != null ? member.getPermissions() : null;

                MemberResponsibilitySnapshotDO snapshot = MemberResponsibilitySnapshotDO.builder()
                        .bookId(bookId)
                        .yearMonth(yearMonth)
                        .closingId(closingId)
                        .userId(userId)
                        .userNickname(nickname)
                        .userPermissions(permissions)
                        .recordCategory(recordCategory)
                        .recordAccountId(recordAccountId)
                        .totalIncome(totalIncome)
                        .totalExpend(totalExpend)
                        .recordCount(recordCount)
                        .status(0)
                        .build();
                snapshots.add(snapshot);
            }

            if (!snapshots.isEmpty()) {
                saveBatch(snapshots);
            }

            auditLogService.log(bookId, 0, "RESPONSIBILITY_SNAPSHOT", "CLOSING", closingId,
                    "{\"yearMonth\":\"" + yearMonth + "\",\"snapshotCount\":" + snapshots.size() + "}");
        } finally {
            redisLockUtil.releaseLock(lockKey);
        }
    }

    @Override
    public List<MemberResponsibilitySnapshotDO> getSnapshots(Integer bookId, String yearMonth, Integer userId) {
        QueryWrapper<MemberResponsibilitySnapshotDO> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId)
                .eq("year_month", yearMonth);
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByAsc("user_id", "record_category");
        return list(wrapper);
    }

    @Override
    public List<MemberResponsibilitySnapshotDTO> getRecalculatedSnapshots(Integer bookId, String yearMonth) {
        // 获取原始快照
        List<MemberResponsibilitySnapshotDO> originals = getSnapshots(bookId, yearMonth, null);
        if (originals.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取调整单
        List<ClosingAdjustmentDO> adjustments = closingAdjustmentService.getAdjustments(bookId, yearMonth);

        // 按 (userId, category, accountId) 聚合调整量
        Map<String, BigDecimal[]> adjustmentMap = new HashMap<>();
        for (ClosingAdjustmentDO adj : adjustments) {
            String key = adj.getUserId() + ":" + adj.getRecordCategory() + ":" + adj.getRecordAccountId();
            adjustmentMap.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] deltas = adjustmentMap.get(key);
            deltas[0] = deltas[0].add(adj.getAdjustmentIncome());
            deltas[1] = deltas[1].add(adj.getAdjustmentExpend());
        }

        // 合并原始快照与调整
        return originals.stream().map(snap -> {
            String key = snap.getUserId() + ":" + snap.getRecordCategory() + ":" + snap.getRecordAccountId();
            BigDecimal[] deltas = adjustmentMap.getOrDefault(key,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            return MemberResponsibilitySnapshotDTO.builder()
                    .userId(snap.getUserId())
                    .userNickname(snap.getUserNickname())
                    .userPermissions(snap.getUserPermissions())
                    .recordCategory(snap.getRecordCategory())
                    .recordAccountId(snap.getRecordAccountId())
                    .originalIncome(snap.getTotalIncome())
                    .originalExpend(snap.getTotalExpend())
                    .originalRecordCount(snap.getRecordCount())
                    .adjustmentIncome(deltas[0])
                    .adjustmentExpend(deltas[1])
                    .adjustedIncome(snap.getTotalIncome().add(deltas[0]))
                    .adjustedExpend(snap.getTotalExpend().add(deltas[1]))
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public void getSnapshotsByPage(Integer bookId, String yearMonth,
                                    PageBO<MemberResponsibilitySnapshotDO> pageBO) {
        IPage<MemberResponsibilitySnapshotDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        QueryWrapper<MemberResponsibilitySnapshotDO> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId);
        if (yearMonth != null) {
            wrapper.eq("year_month", yearMonth);
        }
        wrapper.orderByAsc("user_id", "record_category");

        IPage<MemberResponsibilitySnapshotDO> result = page(page, wrapper);
        pageBO.setList(result.getRecords());
        pageBO.setTotal((int) result.getTotal());
    }
}
