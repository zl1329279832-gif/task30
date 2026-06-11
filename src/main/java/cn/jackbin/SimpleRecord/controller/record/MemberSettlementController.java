package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
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
}
