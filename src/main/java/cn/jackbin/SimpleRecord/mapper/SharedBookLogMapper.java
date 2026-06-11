package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.SharedBookLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedBookLogMapper extends BaseMapper<SharedBookLogDO> {

    IPage<SharedBookLogDO> queryByBookId(Page<?> page,
                                         @Param("recordBookId") Integer recordBookId,
                                         @Param("operType") String operType);
}
