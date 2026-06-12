package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.MonthlyClosingRecalculationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonthlyClosingRecalculationMapper extends BaseMapper<MonthlyClosingRecalculationDO> {
    MonthlyClosingRecalculationDO selectLatestVersion(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);
    List<MonthlyClosingRecalculationDO> selectRecalcHistory(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);
}
