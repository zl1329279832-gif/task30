package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardRuleDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetCarryforwardRuleMapper extends BaseMapper<BudgetCarryforwardRuleDO> {
    BudgetCarryforwardRuleDO selectActiveRule(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);
    List<BudgetCarryforwardRuleDO> selectRuleHistory(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);
}
