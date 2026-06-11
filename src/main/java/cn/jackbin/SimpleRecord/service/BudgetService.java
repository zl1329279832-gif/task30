package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.dto.BudgetExecutionDTO;
import cn.jackbin.SimpleRecord.dto.BudgetWarnDTO;
import cn.jackbin.SimpleRecord.entity.BudgetDO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BudgetService extends IService<BudgetDO> {

    void addBudget(Integer recordBookId, Integer budgetType, String categoryName,
                   Integer memberUserId, String yearMonth, Double budgetAmount, Integer warnPercent);

    void editBudget(Long id, Double budgetAmount, Integer warnPercent);

    void deleteBudget(Long id);

    List<BudgetExecutionDTO> getBudgetExecution(Integer recordBookId, String yearMonth);

    List<BudgetExecutionDTO> getBudgetExecutionByCategory(Integer recordBookId, String yearMonth);

    List<BudgetExecutionDTO> getBudgetExecutionByMember(Integer recordBookId, String yearMonth);

    boolean checkOverLimit(Integer recordBookId, String yearMonth);

    List<BudgetWarnDTO> getOverLimitWarnings(Integer recordBookId, String yearMonth);

    void invalidateCache(Integer recordBookId, String yearMonth);
}
