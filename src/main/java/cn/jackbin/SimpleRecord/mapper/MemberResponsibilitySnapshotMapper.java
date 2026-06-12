package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.MemberResponsibilitySnapshotDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 成员责任快照 Mapper
 */
@Repository
public interface MemberResponsibilitySnapshotMapper extends BaseMapper<MemberResponsibilitySnapshotDO> {

    /**
     * 按成员+分类+账户分组查询月度快照数据
     */
    List<Map<String, Object>> queryGroupedSnapshot(@Param("bookId") Integer bookId,
                                                    @Param("yearMonth") String yearMonth);
}
