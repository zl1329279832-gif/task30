package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.DifferenceAdjustmentRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DifferenceAdjustmentRecordMapper extends BaseMapper<DifferenceAdjustmentRecordDO> {
    DifferenceAdjustmentRecordDO selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    List<DifferenceAdjustmentRecordDO> selectBySourcePeriod(@Param("bookId") Integer bookId, @Param("sourceYearMonth") String sourceYearMonth);
    List<DifferenceAdjustmentRecordDO> selectByTargetPeriod(@Param("bookId") Integer bookId, @Param("targetYearMonth") String targetYearMonth);
    Long sumAdjustmentByTargetPeriod(@Param("bookId") Integer bookId, @Param("targetYearMonth") String targetYearMonth);
}
