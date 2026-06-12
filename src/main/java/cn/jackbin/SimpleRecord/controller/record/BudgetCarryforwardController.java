package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardLogDO;
import cn.jackbin.SimpleRecord.entity.BudgetCarryforwardRuleDO;
import cn.jackbin.SimpleRecord.service.BudgetCarryforwardService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Api(tags = "预算结转管理")
@RestController
@RequestMapping("/carryforward")
public class BudgetCarryforwardController {

    @Autowired
    private BudgetCarryforwardService budgetCarryforwardService;

    @ApiOperation("查询活跃结转规则")
    @GetMapping("/{bookId}/rule/{yearMonth}")
    public Result<BudgetCarryforwardRuleDO> getActiveRule(@PathVariable Integer bookId,
                                                           @PathVariable String yearMonth) {
        return Result.success(budgetCarryforwardService.getActiveRule(bookId, yearMonth));
    }

    @ApiOperation("查询规则版本历史")
    @GetMapping("/{bookId}/rule/{yearMonth}/history")
    public Result<List<BudgetCarryforwardRuleDO>> getRuleHistory(@PathVariable Integer bookId,
                                                                   @PathVariable String yearMonth) {
        return Result.success(budgetCarryforwardService.getRuleHistory(bookId, yearMonth));
    }

    @ApiOperation("创建/更新结转规则")
    @PostMapping("/{bookId}/rule")
    public Result<BudgetCarryforwardRuleDO> saveRule(@PathVariable Integer bookId,
                                                      @RequestParam String yearMonth,
                                                      @RequestParam String ruleType,
                                                      @RequestParam(required = false) BigDecimal carryforwardRate,
                                                      @RequestParam(required = false) Long maxCarryforwardAmount,
                                                      @RequestParam(required = false) Integer expireMonths,
                                                      @RequestParam(required = false) Integer includePending,
                                                      @RequestParam(required = false) String overspentMode,
                                                      @RequestParam(required = false) String categoryFilter) {
        Integer userId = LocalUserId.get().intValue();
        BudgetCarryforwardRuleDO rule = budgetCarryforwardService.saveRule(bookId, yearMonth, ruleType,
                carryforwardRate, maxCarryforwardAmount, expireMonths, includePending,
                overspentMode, categoryFilter, userId);
        return Result.success(rule);
    }

    @ApiOperation("查询结转日志")
    @GetMapping("/{bookId}/log")
    public Result<List<BudgetCarryforwardLogDO>> getLogs(@PathVariable Integer bookId,
                                                          @RequestParam String sourceYearMonth) {
        return Result.success(budgetCarryforwardService.getCarryforwardLogs(bookId, sourceYearMonth));
    }

    @ApiOperation("回滚结转")
    @PostMapping("/{bookId}/rollback")
    public Result<?> rollback(@PathVariable Integer bookId, @RequestParam String yearMonth) {
        Integer userId = LocalUserId.get().intValue();
        budgetCarryforwardService.rollbackCarryforward(bookId, yearMonth, userId);
        return Result.success();
    }
}
