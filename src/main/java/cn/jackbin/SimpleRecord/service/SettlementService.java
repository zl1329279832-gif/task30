package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.SettlementDO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface SettlementService extends IService<SettlementDO> {

    List<SettlementDO> calculateSettlement(Integer recordBookId, String yearMonth);

    void generateSettlement(Integer recordBookId, String yearMonth, Integer operatorUserId);

    void confirmSettlement(Long settlementId, Integer userId);

    List<SettlementDO> getSettlements(Integer recordBookId, String yearMonth);
}
