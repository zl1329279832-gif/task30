package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

/**
 * 月结锁定 Mapper
 */
@Repository
public interface MonthlyClosingMapper extends BaseMapper<MonthlyClosingDO> {
}
