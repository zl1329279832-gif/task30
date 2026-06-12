package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * 月结锁定 Mapper
 */
@Repository
public interface MonthlyClosingMapper extends BaseMapper<MonthlyClosingDO> {

    @Update("UPDATE tb_monthly_closing SET snapshot_version = #{newVersion}, " +
            "total_income = #{income}, total_expend = #{expend}, " +
            "carryforward_total = #{cf}, overspent_total = #{os}, pending_impact_total = #{pi}, " +
            "last_recalc_time = NOW() WHERE book_id = #{bookId} AND year_month = #{yearMonth} " +
            "AND snapshot_version = #{oldVersion}")
    int updateWithRecalcVersion(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth,
                                @Param("oldVersion") Integer oldVersion, @Param("newVersion") Integer newVersion,
                                @Param("income") BigDecimal income, @Param("expend") BigDecimal expend,
                                @Param("cf") Long cf, @Param("os") Long os, @Param("pi") Long pi);

    @Update("UPDATE tb_monthly_closing SET carryforward_executed = 1, carryforward_total = #{cf}, " +
            "overspent_total = #{os}, pending_impact_total = #{pi} " +
            "WHERE book_id = #{bookId} AND year_month = #{yearMonth}")
    int markCarryforwardExecuted(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth,
                                  @Param("cf") Long cf, @Param("os") Long os, @Param("pi") Long pi);
}
