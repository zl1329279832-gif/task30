package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.ClosingAdjustmentDO;
import cn.jackbin.SimpleRecord.service.ClosingAdjustmentService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 月结调整单控制器
 */
@Api(tags = "月结调整单")
@RestController
@RequestMapping("/closing/adjustment")
public class ClosingAdjustmentController {

    @Autowired
    private ClosingAdjustmentService closingAdjustmentService;

    @ApiOperation("查询调整单列表")
    @GetMapping("/{bookId}/list")
    public Result<PageBO<ClosingAdjustmentDO>> getAdjustments(@PathVariable Integer bookId,
                                                               @RequestParam(required = false) String yearMonth,
                                                               @RequestParam(defaultValue = "1") Integer pageNo,
                                                               @RequestParam(defaultValue = "20") Integer pageSize) {
        PageBO<ClosingAdjustmentDO> pageBO = new PageBO<>(pageNo, pageSize);
        closingAdjustmentService.getAdjustmentsByPage(bookId, yearMonth, pageBO);
        return Result.success(pageBO);
    }

    @ApiOperation("查询预算影响净额")
    @GetMapping("/{bookId}/impact")
    public Result<BigDecimal> getNetBudgetImpact(@PathVariable Integer bookId,
                                                  @RequestParam String yearMonth) {
        return Result.success(closingAdjustmentService.getNetBudgetImpact(bookId, yearMonth));
    }
}
