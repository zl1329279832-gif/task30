package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetCarryforwardLogMapper extends BaseMapper<BudgetCarryforwardLogDO> {
    List<BudgetCarryforwardLogDO> selectBySourcePeriod(@Param("bookId") Integer bookId, @Param("sourceYearMonth") String sourceYearMonth);
    List<BudgetCarryforwardLogDO> selectByTargetPeriod(@Param("bookId") Integer bookId, @Param("targetYearMonth") String targetYearMonth);
    List<BudgetCarryforwardLogDO> selectByMember(@Param("bookId") Integer bookId, @Param("memberUserId") Integer memberUserId, @Param("yearMonth") String yearMonth);
    int batchInsert(@Param("list") List<BudgetCarryforwardLogDO> list);
}
