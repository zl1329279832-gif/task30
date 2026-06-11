package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.SettlementDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.SettlementMapper;
import cn.jackbin.SimpleRecord.service.RecordDetailService;
import cn.jackbin.SimpleRecord.service.SettlementService;
import cn.jackbin.SimpleRecord.service.SharedBookLogService;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SettlementServiceImpl extends ServiceImpl<SettlementMapper, SettlementDO>
        implements SettlementService {

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private SharedBookLogService sharedBookLogService;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public List<SettlementDO> calculateSettlement(Integer recordBookId, String yearMonth) {
        // 查询该月所有已入账支出记录的垫付汇总
        List<Map<String, Object>> summaries = settlementMapper.queryAdvancePaymentSummary(recordBookId, yearMonth);

        // 构建每个用户的净余额：应付金额 - 实际支付金额
        // shouldPay: 每个用户自己消费的总额（以userId记录的支出）
        // actuallyPaid: 每个用户实际垫付的总额（以payerUserId记录的支付）
        Map<Integer, Double> shouldPay = new HashMap<>();
        Map<Integer, Double> actuallyPaid = new HashMap<>();

        for (Map<String, Object> row : summaries) {
            Integer consumerId = ((Number) row.get("userId")).intValue();
            Integer payerId = ((Number) row.get("payerUserId")).intValue();
            Double amount = ((Number) row.get("totalAmount")).doubleValue();

            shouldPay.merge(consumerId, amount, Double::sum);
            actuallyPaid.merge(payerId, amount, Double::sum);
        }

        // 计算净余额：正数表示欠钱，负数表示被欠钱
        Set<Integer> allUsers = new HashSet<>();
        allUsers.addAll(shouldPay.keySet());
        allUsers.addAll(actuallyPaid.keySet());

        Map<Integer, Double> netBalance = new HashMap<>();
        for (Integer uid : allUsers) {
            double owed = shouldPay.getOrDefault(uid, 0.0);
            double paid = actuallyPaid.getOrDefault(uid, 0.0);
            double net = owed - paid; // 正数=欠钱, 负数=被欠钱
            if (Math.abs(net) > 0.01) {
                netBalance.put(uid, net);
            }
        }

        // 贪心匹配算法：最大欠款人向最大债权人还款
        List<SettlementDO> settlements = new ArrayList<>();
        List<Map.Entry<Integer, Double>> debtors = netBalance.entrySet().stream()
                .filter(e -> e.getValue() > 0.01)
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        List<Map.Entry<Integer, Double>> creditors = netBalance.entrySet().stream()
                .filter(e -> e.getValue() < -0.01)
                .sorted(Comparator.comparingDouble(e -> e.getValue()))
                .collect(Collectors.toList());

        int di = 0, ci = 0;
        while (di < debtors.size() && ci < creditors.size()) {
            Map.Entry<Integer, Double> debtor = debtors.get(di);
            Map.Entry<Integer, Double> creditor = creditors.get(ci);

            double debtAmount = debtor.getValue();
            double creditAmount = -creditor.getValue();
            double settleAmount = Math.min(debtAmount, creditAmount);

            if (settleAmount > 0.01) {
                SettlementDO settlement = SettlementDO.builder()
                        .recordBookId(recordBookId)
                        .yearMonth(yearMonth)
                        .fromUserId(debtor.getKey())
                        .toUserId(creditor.getKey())
                        .amount(Math.round(settleAmount * 100.0) / 100.0)
                        .settleStatus(RecordConstant.SETTLE_PENDING)
                        .status(0)
                        .build();
                settlements.add(settlement);
            }

            debtor.setValue(debtAmount - settleAmount);
            creditor.setValue(creditor.getValue() + settleAmount);

            if (debtor.getValue() < 0.01) di++;
            if (creditor.getValue() > -0.01) ci++;
        }

        return settlements;
    }

    @Transactional
    @Override
    public void generateSettlement(Integer recordBookId, String yearMonth, Integer operatorUserId) {
        // 使用Redis简单互斥锁防止并发生成
        String lockKey = RedisKey.SETTLE_CALC_LOCK_PREFIX + recordBookId + ":" + yearMonth;
        boolean acquired = false;
        if (!redisUtil.hasKey(lockKey)) {
            redisUtil.set(lockKey, operatorUserId, 30); // 30秒自动过期
            acquired = true;
        }
        if (!acquired) {
            throw new BusinessException(CodeMsg.OPERATE_FAILED);
        }

        try {
            // 删除已有的待结算记录
            LambdaQueryWrapper<SettlementDO> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(SettlementDO::getRecordBookId, recordBookId)
                    .eq(SettlementDO::getYearMonth, yearMonth)
                    .eq(SettlementDO::getSettleStatus, RecordConstant.SETTLE_PENDING);
            remove(deleteWrapper);

            // 计算并保存新的结算记录
            List<SettlementDO> settlements = calculateSettlement(recordBookId, yearMonth);
            if (!settlements.isEmpty()) {
                saveBatch(settlements);
            }

            sharedBookLogService.log(recordBookId, operatorUserId, "SETTLE", null,
                    "生成 " + yearMonth + " 结算记录 " + settlements.size() + " 条");
        } finally {
            redisUtil.del(lockKey);
        }
    }

    @Override
    public void confirmSettlement(Long settlementId, Integer userId) {
        SettlementDO settlement = getById(settlementId);
        if (settlement == null) {
            throw new BusinessException(CodeMsg.SETTLEMENT_NOT_FOUND);
        }
        if (settlement.getSettleStatus() == RecordConstant.SETTLE_DONE) {
            throw new BusinessException(CodeMsg.SETTLEMENT_ALREADY_DONE);
        }
        settlement.setSettleStatus(RecordConstant.SETTLE_DONE);
        settlement.setSettledBy(userId);
        settlement.setSettleTime(new Date());
        updateById(settlement);

        sharedBookLogService.log(settlement.getRecordBookId(), userId, "SETTLE_CONFIRM",
                settlementId, "确认结算完成");
    }

    @Override
    public List<SettlementDO> getSettlements(Integer recordBookId, String yearMonth) {
        LambdaQueryWrapper<SettlementDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementDO::getRecordBookId, recordBookId)
                .eq(SettlementDO::getYearMonth, yearMonth)
                .orderByAsc(SettlementDO::getFromUserId);
        return list(wrapper);
    }
}
