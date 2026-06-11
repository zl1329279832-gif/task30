package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.BudgetDO;
import cn.jackbin.SimpleRecord.dto.BudgetExecutionDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetMapper extends BaseMapper<BudgetDO> {

    List<BudgetExecutionDTO> queryBudgetExecution(@Param("recordBookId") Integer recordBookId,
                                                   @Param("yearMonth") String yearMonth);

    List<BudgetExecutionDTO> queryBudgetExecutionByCategory(@Param("recordBookId") Integer recordBookId,
                                                             @Param("yearMonth") String yearMonth);

    List<BudgetExecutionDTO> queryBudgetExecutionByMember(@Param("recordBookId") Integer recordBookId,
                                                           @Param("yearMonth") String yearMonth);
}
