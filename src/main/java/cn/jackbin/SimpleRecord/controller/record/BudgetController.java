package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.BookBudgetDO;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.vo.Result;
import cn.jackbin.SimpleRecord.vo.SetBudgetVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算控制器
 */
@Api(tags = "预算管理")
@RestController
@RequestMapping("/budget")
public class BudgetController {

    @Autowired
    private BudgetService budgetService;

    @ApiOperation("设置/更新月度预算")
    @PutMapping("/{bookId}")
    public Result<?> setBudget(@PathVariable Integer bookId, @Validated @RequestBody SetBudgetVO vo) {
        Integer userId = LocalUserId.get().intValue();
        budgetService.setBudget(bookId, userId, vo.getYearMonth(), vo.getBudgetAmount(), vo.getWarnThreshold());
        return Result.success();
    }

    @ApiOperation("获取某月预算")
    @GetMapping("/{bookId}")
    public Result<BookBudgetDO> getBudget(@PathVariable Integer bookId, @RequestParam String yearMonth) {
        return Result.success(budgetService.getBudget(bookId, yearMonth));
    }

    @ApiOperation("某年预算列表")
    @GetMapping("/{bookId}/list")
    public Result<List<BookBudgetDO>> listBudgets(@PathVariable Integer bookId, @RequestParam String year) {
        return Result.success(budgetService.listBudgets(bookId, year));
    }
}
