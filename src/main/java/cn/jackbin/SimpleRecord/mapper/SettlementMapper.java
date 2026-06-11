package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.SettlementDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface SettlementMapper extends BaseMapper<SettlementDO> {

    List<Map<String, Object>> queryAdvancePaymentSummary(@Param("recordBookId") Integer recordBookId,
                                                         @Param("yearMonth") String yearMonth);
}
