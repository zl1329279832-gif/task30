package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.DifferenceAdjustmentRecordDO;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingRecalculationDO;
import cn.jackbin.SimpleRecord.service.DifferenceAdjustmentService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "差额调整管理")
@RestController
@RequestMapping("/adjustment")
public class DifferenceAdjustmentController {

    @Autowired
    private DifferenceAdjustmentService differenceAdjustmentService;

    @ApiOperation("创建差额调整记录")
    @PostMapping
    public Result<DifferenceAdjustmentRecordDO> createAdjustment(
            @RequestParam Integer bookId,
            @RequestParam String sourceYearMonth,
            @RequestParam String targetYearMonth,
            @RequestParam(required = false) Long originalRecordId,
            @RequestParam(required = false) Long reversalRequestId,
            @RequestParam String adjustmentType,
            @RequestParam Long adjustmentAmount,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Integer memberUserId,
            @RequestParam String reason,
            @RequestParam Long relatedClosingId,
            @RequestParam String idempotencyKey) {
        Integer userId = LocalUserId.get().intValue();
        DifferenceAdjustmentRecordDO adj = differenceAdjustmentService.createAdjustment(
                bookId, sourceYearMonth, targetYearMonth, originalRecordId,
                reversalRequestId, adjustmentType, adjustmentAmount,
                categoryId, accountId, memberUserId, reason,
                relatedClosingId, idempotencyKey, userId);
        return Result.success(adj);
    }

    @ApiOperation("查询源期间调整记录")
    @GetMapping("/source/{bookId}")
    public Result<List<DifferenceAdjustmentRecordDO>> getBySource(@PathVariable Integer bookId,
                                                                    @RequestParam String sourceYearMonth) {
        return Result.success(differenceAdjustmentService.getBySourcePeriod(bookId, sourceYearMonth));
    }

    @ApiOperation("查询目标期间调整记录")
    @GetMapping("/target/{bookId}")
    public Result<List<DifferenceAdjustmentRecordDO>> getByTarget(@PathVariable Integer bookId,
                                                                    @RequestParam String targetYearMonth) {
        return Result.success(differenceAdjustmentService.getByTargetPeriod(bookId, targetYearMonth));
    }

    @ApiOperation("触发月结快照重算")
    @PostMapping("/recalc/{bookId}")
    public Result<MonthlyClosingRecalculationDO> triggerRecalc(@PathVariable Integer bookId,
                                                                @RequestParam String yearMonth,
                                                                @RequestParam String reason) {
        Integer userId = LocalUserId.get().intValue();
        return Result.success(differenceAdjustmentService.triggerRecalculation(bookId, yearMonth, userId, reason));
    }

    @ApiOperation("查询重算历史")
    @GetMapping("/recalc-history/{bookId}")
    public Result<List<MonthlyClosingRecalculationDO>> recalcHistory(@PathVariable Integer bookId,
                                                                       @RequestParam String yearMonth) {
        return Result.success(differenceAdjustmentService.getRecalcHistory(bookId, yearMonth));
    }
}
