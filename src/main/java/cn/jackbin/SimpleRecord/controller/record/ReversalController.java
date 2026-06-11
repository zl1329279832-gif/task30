package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.ReversalRequestDO;
import cn.jackbin.SimpleRecord.service.ReversalService;
import cn.jackbin.SimpleRecord.vo.RequestReversalVO;
import cn.jackbin.SimpleRecord.vo.Result;
import cn.jackbin.SimpleRecord.vo.ReversalReviewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 冲正控制器
 */
@Api(tags = "冲正管理")
@RestController
@RequestMapping("/reversal")
public class ReversalController {

    @Autowired
    private ReversalService reversalService;

    @ApiOperation("申请冲正")
    @PostMapping("/request")
    public Result<ReversalRequestDO> request(@Validated @RequestBody RequestReversalVO vo) {
        Integer userId = LocalUserId.get().intValue();
        ReversalRequestDO req = reversalService.requestReversal(vo.getBookId(), userId, vo.getRecordId(), vo.getReason());
        return Result.success(req);
    }

    @ApiOperation("待审核冲正申请")
    @GetMapping("/pending")
    public Result<PageBO<ReversalRequestDO>> pending(@RequestParam Integer bookId,
                                                      @RequestParam(defaultValue = "1") Integer pageNo,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        PageBO<ReversalRequestDO> pageBO = new PageBO<>();
        pageBO.setPageNo(pageNo);
        pageBO.setPageSize(pageSize);
        reversalService.getReversalRequests(bookId, 1, pageBO);
        return Result.success(pageBO);
    }

    @ApiOperation("审批通过冲正")
    @PostMapping("/{requestId}/approve")
    public Result<?> approve(@PathVariable Long requestId, @Validated @RequestBody ReversalReviewVO vo) {
        Integer userId = LocalUserId.get().intValue();
        reversalService.approveReversal(vo.getBookId(), userId, requestId, vo.getRemark());
        return Result.success();
    }

    @ApiOperation("驳回冲正")
    @PostMapping("/{requestId}/reject")
    public Result<?> reject(@PathVariable Long requestId, @Validated @RequestBody ReversalReviewVO vo) {
        Integer userId = LocalUserId.get().intValue();
        reversalService.rejectReversal(vo.getBookId(), userId, requestId, vo.getRemark());
        return Result.success();
    }
}
