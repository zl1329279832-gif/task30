package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * 月结调整单 Mapper
 */
@Repository
public interface ClosingAdjustmentMapper extends BaseMapper<ClosingAdjustmentDO> {

    /**
     * 查询某月所有调整单的预算影响净额
     */
    BigDecimal queryNetBudgetImpact(@Param("bookId") Integer bookId,
                                    @Param("yearMonth") String yearMonth);
}
