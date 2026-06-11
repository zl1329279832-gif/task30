package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.dto.ReversalApplicationDTO;
import cn.jackbin.SimpleRecord.entity.ReversalApplicationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReversalApplicationMapper extends BaseMapper<ReversalApplicationDO> {

    IPage<ReversalApplicationDTO> queryByBookId(Page<?> page,
                                                @Param("recordBookId") Integer recordBookId,
                                                @Param("auditStatus") Integer auditStatus);
}
