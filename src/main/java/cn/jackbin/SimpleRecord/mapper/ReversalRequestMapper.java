package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.ReversalRequestDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 冲正申请 Mapper
 */
@Repository
public interface ReversalRequestMapper extends BaseMapper<ReversalRequestDO> {

    @Select("SELECT * FROM tb_reversal_request WHERE original_record_id = #{recordId} AND cross_period = 1 AND delete_time IS NULL")
    List<ReversalRequestDO> selectCrossPeriodByOriginalRecord(@Param("recordId") Long recordId);
}
