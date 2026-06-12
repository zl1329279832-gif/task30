package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.MemberSettlementSnapshotDO;
import cn.jackbin.SimpleRecord.service.MemberSettlementService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成员结算控制器
 */
@Api(tags = "成员结算")
@RestController
@RequestMapping("/settlement")
public class MemberSettlementController {

    @Autowired
    private MemberSettlementService memberSettlementService;

    @ApiOperation("成员净余额")
    @GetMapping("/{bookId}/balances")
    public Result<Map<Integer, BigDecimal>> balances(@PathVariable Integer bookId,
                                                      @RequestParam String yearMonth) {
        return Result.success(memberSettlementService.calculateNetBalances(bookId, yearMonth));
    }

    @ApiOperation("执行结算")
    @PostMapping("/{bookId}/execute")
    public Result<List<Long>> execute(@PathVariable Integer bookId, @RequestParam String yearMonth) {
        Integer userId = LocalUserId.get().intValue();
        return Result.success(memberSettlementService.executeSettlement(bookId, userId, yearMonth));
    }

    @ApiOperation("查询成员责任快照")
    @GetMapping("/{bookId}/snapshot/{yearMonth}/{memberUserId}")
    public Result<MemberSettlementSnapshotDO> getResponsibility(@PathVariable Integer bookId,
                                                                 @PathVariable String yearMonth,
                                                                 @PathVariable Integer memberUserId) {
        return Result.success(memberSettlementService.getHistoricalResponsibility(bookId, yearMonth, memberUserId));
    }

    @ApiOperation("查询成员责任时间线")
    @GetMapping("/{bookId}/timeline/{memberUserId}")
    public Result<List<MemberSettlementSnapshotDO>> getTimeline(@PathVariable Integer bookId,
                                                                  @PathVariable Integer memberUserId) {
        return Result.success(memberSettlementService.getMemberResponsibilityTimeline(bookId, memberUserId));
    }

    @ApiOperation("查询某期所有成员快照")
    @GetMapping("/{bookId}/snapshots")
    public Result<List<MemberSettlementSnapshotDO>> getPeriodSnapshots(@PathVariable Integer bookId,
                                                                        @RequestParam String yearMonth) {
        return Result.success(memberSettlementService.getPeriodSnapshots(bookId, yearMonth));
    }
}
