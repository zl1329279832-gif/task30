package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedBookMemberMapper extends BaseMapper<SharedBookMemberDO> {

    List<SharedBookMemberDO> queryMembersByBookId(@Param("recordBookId") Integer recordBookId);

    SharedBookMemberDO queryByBookAndUser(@Param("recordBookId") Integer recordBookId,
                                          @Param("userId") Integer userId);
}
