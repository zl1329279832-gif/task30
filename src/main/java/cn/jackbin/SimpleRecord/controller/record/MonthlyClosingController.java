package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import cn.jackbin.SimpleRecord.service.MonthlyClosingService;
import cn.jackbin.SimpleRecord.vo.CloseMonthVO;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 月结控制器
 */
@Api(tags = "月结管理")
@RestController
@RequestMapping("/monthlyClosing")
public class MonthlyClosingController {

    @Autowired
    private MonthlyClosingService monthlyClosingService;

    @ApiOperation("执行月结")
    @PostMapping("/{bookId}")
    public Result<?> closeMonth(@PathVariable Integer bookId, @Validated @RequestBody CloseMonthVO vo) {
        Integer userId = LocalUserId.get().intValue();
        monthlyClosingService.closeMonth(bookId, userId, vo.getYearMonth(), vo.getRemark());
        return Result.success();
    }

    @ApiOperation("月结状态")
    @GetMapping("/{bookId}")
    public Result<MonthlyClosingDO> getClosing(@PathVariable Integer bookId, @RequestParam String yearMonth) {
        return Result.success(monthlyClosingService.getClosing(bookId, yearMonth));
    }

    @ApiOperation("月结记录列表")
    @GetMapping("/{bookId}/list")
    public Result<PageBO<MonthlyClosingDO>> list(@PathVariable Integer bookId,
                                                  @RequestParam(defaultValue = "1") Integer pageNo,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        PageBO<MonthlyClosingDO> pageBO = new PageBO<>();
        pageBO.setPageNo(pageNo);
        pageBO.setPageSize(pageSize);
        monthlyClosingService.getClosingsByPage(bookId, pageBO);
        return Result.success(pageBO);
    }
}
