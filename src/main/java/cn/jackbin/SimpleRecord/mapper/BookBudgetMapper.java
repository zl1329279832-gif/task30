package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 账本预算 Mapper
 */
@Repository
public interface BookBudgetMapper extends BaseMapper<BookBudgetDO> {

    /**
     * 查询某账本某月已使用金额 (仅统计已入账和无需审核的记录, 排除冲正条目)
     */
    BigDecimal queryUsedAmountByMonth(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);

    /**
     * 查询某账本某月各成员分摊金额 (排除冲正条目)
     */
    List<Map<String, Object>> queryMemberShareByMonth(@Param("bookId") Integer bookId, @Param("yearMonth") String yearMonth);
}
