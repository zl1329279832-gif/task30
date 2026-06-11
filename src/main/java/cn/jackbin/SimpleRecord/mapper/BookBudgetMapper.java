package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

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
}
