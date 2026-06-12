package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 账本预算 Mapper
 */
@Repository
public interface BookBudgetMapper extends BaseMapper<BookBudgetDO> {

    /**
     * 查询某账本某月已使用金额 (仅统计已入账和无需审核的记录)
     */
    BigDecimal queryUsedAmountByMonth(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);

    @Select("SELECT IFNULL(SUM(ABS(used_amount)), 0) FROM tb_book_budget WHERE book_id = #{bookId} AND year_month = #{yearMonth}")
    Long sumUsedAmountByPeriod(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);

    @Update("UPDATE tb_book_budget SET carryforward_amount = #{amount}, source_year_month = #{src}, " +
            "rule_version = #{ver}, budget_amount = budget_amount + #{amountDecimal} " +
            "WHERE book_id = #{bookId} AND year_month = #{targetYearMonth}")
    int applyCarryforward(@Param("bookId") Integer bookId, @Param("targetYearMonth") String target,
                          @Param("amount") Long amount, @Param("amountDecimal") BigDecimal amountDecimal,
                          @Param("src") String src, @Param("ver") Integer ver);
}
