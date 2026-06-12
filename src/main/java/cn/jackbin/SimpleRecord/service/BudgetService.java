package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算服务
 */
public interface BudgetService extends IService<BookBudgetDO> {

    /**
     * 设置/更新月度预算
     */
    void setBudget(Integer bookId, Integer userId, String yearMonth, BigDecimal amount, Integer warnThreshold);

    /**
     * 获取某月预算
     */
    BookBudgetDO getBudget(Integer bookId, String yearMonth);

    /**
     * 软检查预算 (仅警告, 不阻止)
     */
    boolean checkBudgetWarning(Integer bookId, BigDecimal newExpenseAmount, String yearMonth);

    /**
     * 原子递增已使用金额 (审核通过时调用, 超限抛异常)
     */
    void atomicIncrementUsed(Integer bookId, String yearMonth, BigDecimal amount);

    /**
     * 原子递减已使用金额 (冲正时调用)
     */
    void atomicDecrementUsed(Integer bookId, String yearMonth, BigDecimal amount);

    /**
     * 列出某年所有月份预算
     */
    List<BookBudgetDO> listBudgets(Integer bookId, String year);
}
