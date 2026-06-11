package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.common.anotations.CommonLog;
import cn.jackbin.SimpleRecord.common.enums.BusinessType;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.dto.ReversalApplicationDTO;
import cn.jackbin.SimpleRecord.service.ReversalApplicationService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.vo.ReversalApplyVO;
import cn.jackbin.SimpleRecord.vo.ReversalAuditVO;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;

@Api(value = "ReversalController", tags = {"冲正申请接口"})
@RestController
@RequestMapping("/sharedBook/{bookId}/reversal")
public class ReversalController {

    @Autowired
    private ReversalApplicationService reversalApplicationService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;

    @CommonLog(title = "提交冲正申请", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<?> apply(@PathVariable @Positive Integer bookId,
                           @RequestBody @Validated ReversalApplyVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_RECORD);
        reversalApplicationService.apply(bookId, vo.getOriginalRecordId(), userId.intValue(), vo.getReason());
        return Result.success();
    }

    @CommonLog(title = "审批通过冲正申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{applicationId}/approve")
    public Result<?> approve(@PathVariable @Positive Integer bookId,
                             @PathVariable @Positive Long applicationId,
                             @RequestBody(required = false) ReversalAuditVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_AUDIT);
        String auditRemark = vo != null ? vo.getAuditRemark() : null;
        reversalApplicationService.approve(applicationId, userId.intValue(), auditRemark);
        return Result.success();
    }

    @CommonLog(title = "驳回冲正申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{applicationId}/reject")
    public Result<?> reject(@PathVariable @Positive Integer bookId,
                            @PathVariable @Positive Long applicationId,
                            @RequestBody(required = false) ReversalAuditVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_AUDIT);
        String auditRemark = vo != null ? vo.getAuditRemark() : null;
        reversalApplicationService.reject(applicationId, userId.intValue(), auditRemark);
        return Result.success();
    }

    @PostMapping("/page")
    public Result<?> listApplications(@PathVariable @Positive Integer bookId,
                                      @RequestParam(required = false) Integer auditStatus,
                                      @RequestParam(defaultValue = "1") int pageNo,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_VIEW);
        PageBO<ReversalApplicationDTO> pageBO = new PageBO<>(pageNo, pageSize);
        reversalApplicationService.getByPage(bookId, auditStatus, pageBO);
        return Result.success(pageBO);
    }
}
