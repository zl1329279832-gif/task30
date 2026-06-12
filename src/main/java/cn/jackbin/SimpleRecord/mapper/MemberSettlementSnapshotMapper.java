package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.MemberSettlementSnapshotDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberSettlementSnapshotMapper extends BaseMapper<MemberSettlementSnapshotDO> {
    MemberSettlementSnapshotDO selectByPeriodAndMember(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth, @Param("memberUserId") Integer memberUserId);
    List<MemberSettlementSnapshotDO> selectByPeriod(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);
    List<MemberSettlementSnapshotDO> selectMemberHistory(@Param("bookId") Integer bookId, @Param("memberUserId") Integer memberUserId);
    int batchInsert(@Param("list") List<MemberSettlementSnapshotDO> list);
}
