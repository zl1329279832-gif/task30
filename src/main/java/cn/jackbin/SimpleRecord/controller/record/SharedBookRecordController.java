package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.common.anotations.CommonLog;
import cn.jackbin.SimpleRecord.common.enums.BusinessType;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.dto.SharedRecordDetailDTO;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.RecordDetailMapper;
import cn.jackbin.SimpleRecord.service.*;
import cn.jackbin.SimpleRecord.vo.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(value = "SharedBookRecordController", tags = {"共享账本记账接口"})
@RestController
@RequestMapping("/sharedBook/{bookId}/record")
public class SharedBookRecordController {

    @Autowired
    private RecordDetailContext recordDetailContext;
    @Autowired
    private RecordDetailService recordDetailService;
    @Autowired
    private RecordDetailMapper recordDetailMapper;
    @Autowired
    private RecordBookService recordBookService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;
    @Autowired
    private SharedBookLogService sharedBookLogService;
    @Autowired
    private BudgetService budgetService;
    @Autowired
    private MonthLockService monthLockService;

    @CommonLog(title = "共享账本新增记录", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<?> addRecord(@PathVariable @Positive Integer bookId,
                               @RequestBody @Validated SharedBookRecordVO vo) {
        Long userId = LocalUserId.get();
        checkSharedBook(bookId);
        vo.setRecordBookId(bookId);
        recordDetailContext.addOrEdit(userId.intValue(), vo);

        sharedBookLogService.log(bookId, userId.intValue(), "RECORD", null, "新增记录");
        return Result.success();
    }

    @CommonLog(title = "共享账本修改记录", businessType = BusinessType.UPDATE)
    @PutMapping("/{recordId}")
    public Result<?> editRecord(@PathVariable @Positive Integer bookId,
                                @PathVariable @Positive Long recordId,
                                @RequestBody @Validated SharedBookRecordVO vo) {
        Long userId = LocalUserId.get();
        checkSharedBook(bookId);
        vo.setId(recordId);
        vo.setRecordBookId(bookId);
        recordDetailContext.addOrEdit(userId.intValue(), vo);
        return Result.success();
    }

    @CommonLog(title = "共享账本删除记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordId}")
    public Result<?> deleteRecord(@PathVariable @Positive Integer bookId,
                                  @PathVariable @Positive Integer recordId) {
        Long userId = LocalUserId.get();
        checkSharedBook(bookId);
        recordDetailContext.del(userId.intValue(), recordId);
        return Result.success();
    }

    @PostMapping("/page")
    public Result<?> listRecords(@PathVariable @Positive Integer bookId,
                                 @RequestBody @Validated GetSharedBookRecordsVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_VIEW);
        Page<SharedRecordDetailDTO> page = new Page<>(vo.getPageNo(), vo.getPageSize());
        IPage<SharedRecordDetailDTO> result = recordDetailMapper.queryByMonthAndSharedBook(
                page, bookId, vo.getAuditStatus(), vo.getMonth());
        PageBO<SharedRecordDetailDTO> pageBO = new PageBO<>(vo.getPageNo(), vo.getPageSize());
        pageBO.setTotal((int) result.getTotal());
        pageBO.setList(result.getRecords());
        return Result.success(pageBO);
    }

    @CommonLog(title = "审核通过记录", businessType = BusinessType.UPDATE)
    @PostMapping("/{recordId}/approve")
    public Result<?> approveRecord(@PathVariable @Positive Integer bookId,
                                   @PathVariable @Positive Long recordId,
                                   @RequestBody(required = false) AuditRecordVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_AUDIT);

        RecordDetailDO record = recordDetailService.getById(recordId);
        validateAuditRecord(record, bookId, userId.intValue());

        record.setAuditStatus(RecordConstant.AUDIT_APPROVED);
        record.setAuditorId(userId.intValue());
        record.setAuditTime(new Date());
        recordDetailService.updateById(record);

        // 检查预算超限
        String yearMonth = new SimpleDateFormat("yyyy-MM").format(record.getOccurTime());
        budgetService.invalidateCache(bookId, yearMonth);

        sharedBookLogService.log(bookId, userId.intValue(), "AUDIT", recordId, "审核通过");

        Map<String, Object> result = new HashMap<>();
        result.put("approved", true);
        result.put("budgetOverLimit", budgetService.checkOverLimit(bookId, yearMonth));
        if (budgetService.checkOverLimit(bookId, yearMonth)) {
            result.put("budgetWarnings", budgetService.getOverLimitWarnings(bookId, yearMonth));
        }
        return Result.success(result);
    }

    @CommonLog(title = "驳回记录", businessType = BusinessType.UPDATE)
    @PostMapping("/{recordId}/reject")
    public Result<?> rejectRecord(@PathVariable @Positive Integer bookId,
                                  @PathVariable @Positive Long recordId,
                                  @RequestBody(required = false) AuditRecordVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_AUDIT);

        RecordDetailDO record = recordDetailService.getById(recordId);
        validateAuditRecord(record, bookId, userId.intValue());

        record.setAuditStatus(RecordConstant.AUDIT_REJECTED);
        record.setAuditorId(userId.intValue());
        record.setAuditTime(new Date());
        if (vo != null && vo.getRemark() != null) {
            record.setRemark((record.getRemark() != null ? record.getRemark() + " | " : "")
                    + "[驳回] " + vo.getRemark());
        }
        recordDetailService.updateById(record);

        sharedBookLogService.log(bookId, userId.intValue(), "REJECT", recordId, "驳回记录");
        return Result.success();
    }

    @CommonLog(title = "批量审核通过", businessType = BusinessType.UPDATE)
    @PostMapping("/batchApprove")
    public Result<?> batchApprove(@PathVariable @Positive Integer bookId,
                                  @RequestBody @Validated BatchAuditVO vo) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_AUDIT);

        for (Long recordId : vo.getRecordIds()) {
            RecordDetailDO record = recordDetailService.getById(recordId);
            if (record == null || !record.getRecordBookId().equals(bookId)) {
                continue;
            }
            if (record.getAuditStatus() == null
                    || record.getAuditStatus() != RecordConstant.AUDIT_PENDING) {
                continue;
            }
            if (record.getUserId().equals(userId.intValue())) {
                continue; // 不能审核自己的记录
            }
            record.setAuditStatus(RecordConstant.AUDIT_APPROVED);
            record.setAuditorId(userId.intValue());
            record.setAuditTime(new Date());
            recordDetailService.updateById(record);
        }

        sharedBookLogService.log(bookId, userId.intValue(), "BATCH_AUDIT", null,
                "批量审核通过 " + vo.getRecordIds().size() + " 条记录");
        return Result.success();
    }

    private void checkSharedBook(Integer bookId) {
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || book.getBookType() == null
                || book.getBookType() != RecordConstant.BOOK_TYPE_SHARED) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NOT_FOUND);
        }
    }

    private void validateAuditRecord(RecordDetailDO record, Integer bookId, Integer userId) {
        if (record == null || !record.getRecordBookId().equals(bookId)) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
        if (record.getAuditStatus() == null
                || record.getAuditStatus() != RecordConstant.AUDIT_PENDING) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_RECORD_NOT_PENDING);
        }
        if (record.getUserId().equals(userId)) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_CANNOT_AUDIT_SELF);
        }
    }
}
