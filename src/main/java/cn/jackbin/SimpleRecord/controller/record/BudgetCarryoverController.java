package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.dto.EffectiveBudgetDTO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryoverDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryoverRuleDO;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.service.BudgetCarryoverService;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.vo.Result;
import cn.jackbin.SimpleRecord.vo.SetCarryoverRuleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算结转控制器
 */
@Api(tags = "预算结转")
@RestController
@RequestMapping("/budget/carryover")
public class BudgetCarryoverController {

    @Autowired
    private BudgetCarryoverService budgetCarryoverService;

    @Autowired
    private BudgetService budgetService;

    @ApiOperation("设置结转规则")
    @PutMapping("/{bookId}/rule")
    public Result<BudgetCarryoverRuleDO> setCarryoverRule(@PathVariable Integer bookId,
                                                          @Validated @RequestBody SetCarryoverRuleVO vo) {
        Integer userId = LocalUserId.get().intValue();
        BudgetCarryoverRuleDO rule = budgetCarryoverService.setCarryoverRule(bookId, userId,
                vo.getCarryoverType(), vo.getCarryoverPercent(), vo.getCapAmount(),
                vo.getCarryOverspend(), vo.getPendingRecordPolicy(), vo.getEffectiveFrom());
        return Result.success(rule);
    }

    @ApiOperation("获取当前生效规则")
    @GetMapping("/{bookId}/rule")
    public Result<BudgetCarryoverRuleDO> getActiveRule(@PathVariable Integer bookId,
                                                       @RequestParam String yearMonth) {
        return Result.success(budgetCarryoverService.getActiveRule(bookId, yearMonth));
    }

    @ApiOperation("列出所有规则版本")
    @GetMapping("/{bookId}/rules")
    public Result<List<BudgetCarryoverRuleDO>> listRuleVersions(@PathVariable Integer bookId) {
        return Result.success(budgetCarryoverService.listRuleVersions(bookId));
    }

    @ApiOperation("获取结转记录")
    @GetMapping("/{bookId}/history")
    public Result<BudgetCarryoverDO> getCarryover(@PathVariable Integer bookId,
                                                   @RequestParam String yearMonth) {
        return Result.success(budgetCarryoverService.getCarryover(bookId, yearMonth));
    }

    @ApiOperation("获取有效预算(含结转)")
    @GetMapping("/{bookId}/effective")
    public Result<EffectiveBudgetDTO> getEffectiveBudget(@PathVariable Integer bookId,
                                                         @RequestParam String yearMonth) {
        BigDecimal effectiveBudget = budgetCarryoverService.getEffectiveBudget(bookId, yearMonth);
        BookBudgetDO budget = budgetService.getBudget(bookId, yearMonth);
        BigDecimal originalBudget = (budget != null) ? budget.getBudgetAmount() : BigDecimal.ZERO;
        BigDecimal usedAmount = (budget != null) ? budget.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal carryover = effectiveBudget.subtract(originalBudget);

        EffectiveBudgetDTO dto = EffectiveBudgetDTO.builder()
                .bookId(bookId)
                .yearMonth(yearMonth)
                .originalBudget(originalBudget)
                .carryoverAmount(carryover)
                .effectiveBudget(effectiveBudget)
                .usedAmount(usedAmount)
                .remainingBudget(effectiveBudget.subtract(usedAmount))
                .build();
        return Result.success(dto);
    }
}
