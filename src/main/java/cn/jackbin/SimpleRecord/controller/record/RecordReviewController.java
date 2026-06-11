package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.dto.RecordDetailDTO;
import cn.jackbin.SimpleRecord.service.RecordReviewService;
import cn.jackbin.SimpleRecord.vo.ReviewActionVO;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 记账审核控制器
 */
@Api(tags = "记账审核")
@RestController
@RequestMapping("/recordReview")
public class RecordReviewController {

    @Autowired
    private RecordReviewService recordReviewService;

    @ApiOperation("待审核记录列表")
    @GetMapping("/pending")
    public Result<PageBO<RecordDetailDTO>> pending(@RequestParam Integer bookId,
                                                    @RequestParam(defaultValue = "1") Integer pageNo,
                                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        Integer userId = LocalUserId.get().intValue();
        PageBO<RecordDetailDTO> pageBO = new PageBO<>();
        pageBO.setPageNo(pageNo);
        pageBO.setPageSize(pageSize);
        recordReviewService.getPendingReviews(bookId, userId, pageBO);
        return Result.success(pageBO);
    }

    @ApiOperation("审核通过")
    @PostMapping("/{recordId}/approve")
    public Result<?> approve(@PathVariable Long recordId, @RequestParam Integer bookId,
                             @RequestBody(required = false) ReviewActionVO vo) {
        Integer userId = LocalUserId.get().intValue();
        String remark = vo != null ? vo.getRemark() : null;
        recordReviewService.approveRecord(bookId, userId, recordId, remark);
        return Result.success();
    }

    @ApiOperation("审核驳回")
    @PostMapping("/{recordId}/reject")
    public Result<?> reject(@PathVariable Long recordId, @RequestParam Integer bookId,
                            @RequestBody(required = false) ReviewActionVO vo) {
        Integer userId = LocalUserId.get().intValue();
        String reason = vo != null ? vo.getRemark() : "";
        recordReviewService.rejectRecord(bookId, userId, recordId, reason);
        return Result.success();
    }
}
