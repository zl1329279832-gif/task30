package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.dto.BudgetExecutionDTO;
import cn.jackbin.SimpleRecord.dto.BudgetWarnDTO;
import cn.jackbin.SimpleRecord.entity.BudgetDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.BudgetMapper;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BudgetServiceImpl extends ServiceImpl<BudgetMapper, BudgetDO>
        implements BudgetService {

    @Autowired
    private BudgetMapper budgetMapper;

    @Autowired
    private RedisUtil redisUtil;

    private static final long BUDGET_CACHE_TTL = 600; // 10分钟

    @Override
    public void addBudget(Integer recordBookId, Integer budgetType, String categoryName,
                          Integer memberUserId, String yearMonth, Double budgetAmount, Integer warnPercent) {
        BudgetDO budget = BudgetDO.builder()
                .recordBookId(recordBookId)
                .budgetType(budgetType)
                .categoryName(categoryName)
                .memberUserId(memberUserId)
                .yearMonth(yearMonth)
                .budgetAmount(budgetAmount)
                .warnPercent(warnPercent != null ? warnPercent : 80)
                .status(0)
                .build();
        save(budget);
        invalidateCache(recordBookId, yearMonth);
    }

    @Override
    public void editBudget(Long id, Double budgetAmount, Integer warnPercent) {
        BudgetDO budget = getById(id);
        if (budget == null) {
            throw new BusinessException(CodeMsg.BUDGET_NOT_FOUND);
        }
        budget.setBudgetAmount(budgetAmount);
        if (warnPercent != null) {
            budget.setWarnPercent(warnPercent);
        }
        updateById(budget);
        invalidateCache(budget.getRecordBookId(), budget.getYearMonth());
    }

    @Override
    public void deleteBudget(Long id) {
        BudgetDO budget = getById(id);
        if (budget == null) {
            throw new BusinessException(CodeMsg.BUDGET_NOT_FOUND);
        }
        removeById(id);
        invalidateCache(budget.getRecordBookId(), budget.getYearMonth());
    }

    @Override
    public List<BudgetExecutionDTO> getBudgetExecution(Integer recordBookId, String yearMonth) {
        String cacheKey = RedisKey.BUDGET_EXECUTION_PREFIX + recordBookId + ":" + yearMonth + ":total";
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), BudgetExecutionDTO.class);
        }
        List<BudgetExecutionDTO> result = budgetMapper.queryBudgetExecution(recordBookId, yearMonth);
        if (result != null && !result.isEmpty()) {
            redisUtil.set(cacheKey, JSON.toJSONString(result), BUDGET_CACHE_TTL);
        }
        return result;
    }

    @Override
    public List<BudgetExecutionDTO> getBudgetExecutionByCategory(Integer recordBookId, String yearMonth) {
        String cacheKey = RedisKey.BUDGET_EXECUTION_PREFIX + recordBookId + ":" + yearMonth + ":category";
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), BudgetExecutionDTO.class);
        }
        List<BudgetExecutionDTO> result = budgetMapper.queryBudgetExecutionByCategory(recordBookId, yearMonth);
        if (result != null && !result.isEmpty()) {
            redisUtil.set(cacheKey, JSON.toJSONString(result), BUDGET_CACHE_TTL);
        }
        return result;
    }

    @Override
    public List<BudgetExecutionDTO> getBudgetExecutionByMember(Integer recordBookId, String yearMonth) {
        String cacheKey = RedisKey.BUDGET_EXECUTION_PREFIX + recordBookId + ":" + yearMonth + ":member";
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), BudgetExecutionDTO.class);
        }
        List<BudgetExecutionDTO> result = budgetMapper.queryBudgetExecutionByMember(recordBookId, yearMonth);
        if (result != null && !result.isEmpty()) {
            redisUtil.set(cacheKey, JSON.toJSONString(result), BUDGET_CACHE_TTL);
        }
        return result;
    }

    @Override
    public boolean checkOverLimit(Integer recordBookId, String yearMonth) {
        List<BudgetWarnDTO> warnings = getOverLimitWarnings(recordBookId, yearMonth);
        return warnings != null && !warnings.isEmpty();
    }

    @Override
    public List<BudgetWarnDTO> getOverLimitWarnings(Integer recordBookId, String yearMonth) {
        String cacheKey = RedisKey.BUDGET_WARN_PREFIX + recordBookId + ":" + yearMonth;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), BudgetWarnDTO.class);
        }

        List<BudgetWarnDTO> warnings = new ArrayList<>();

        // 检查总预算
        List<BudgetExecutionDTO> totalBudgets = budgetMapper.queryBudgetExecution(recordBookId, yearMonth);
        addWarnings(warnings, totalBudgets, "总预算");

        // 检查分类预算
        List<BudgetExecutionDTO> categoryBudgets = budgetMapper.queryBudgetExecutionByCategory(recordBookId, yearMonth);
        for (BudgetExecutionDTO dto : categoryBudgets) {
            if (dto.getOverLimit()) {
                BudgetWarnDTO warn = new BudgetWarnDTO();
                warn.setLabel(dto.getCategoryName());
                warn.setBudgetAmount(dto.getBudgetAmount());
                warn.setSpentAmount(dto.getSpentAmount());
                warn.setUsedPercent(dto.getUsedPercent());
                warnings.add(warn);
            }
        }

        // 检查成员预算
        List<BudgetExecutionDTO> memberBudgets = budgetMapper.queryBudgetExecutionByMember(recordBookId, yearMonth);
        for (BudgetExecutionDTO dto : memberBudgets) {
            if (dto.getOverLimit()) {
                BudgetWarnDTO warn = new BudgetWarnDTO();
                warn.setLabel(dto.getMemberName() != null ? dto.getMemberName() : "成员#" + dto.getMemberUserId());
                warn.setBudgetAmount(dto.getBudgetAmount());
                warn.setSpentAmount(dto.getSpentAmount());
                warn.setUsedPercent(dto.getUsedPercent());
                warnings.add(warn);
            }
        }

        if (!warnings.isEmpty()) {
            redisUtil.set(cacheKey, JSON.toJSONString(warnings), BUDGET_CACHE_TTL);
        }
        return warnings;
    }

    @Override
    public void invalidateCache(Integer recordBookId, String yearMonth) {
        String prefix = RedisKey.BUDGET_EXECUTION_PREFIX + recordBookId + ":" + yearMonth;
        redisUtil.del(prefix + ":total");
        redisUtil.del(prefix + ":category");
        redisUtil.del(prefix + ":member");
        redisUtil.del(RedisKey.BUDGET_WARN_PREFIX + recordBookId + ":" + yearMonth);
    }

    private void addWarnings(List<BudgetWarnDTO> warnings, List<BudgetExecutionDTO> budgets, String defaultLabel) {
        for (BudgetExecutionDTO dto : budgets) {
            if (dto.getOverLimit()) {
                BudgetWarnDTO warn = new BudgetWarnDTO();
                warn.setLabel(defaultLabel);
                warn.setBudgetAmount(dto.getBudgetAmount());
                warn.setSpentAmount(dto.getSpentAmount());
                warn.setUsedPercent(dto.getUsedPercent());
                warnings.add(warn);
            }
        }
    }
}
