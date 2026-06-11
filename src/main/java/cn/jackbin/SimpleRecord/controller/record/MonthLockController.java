package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.common.anotations.CommonLog;
import cn.jackbin.SimpleRecord.common.enums.BusinessType;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.MonthLockService;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;
import java.util.HashMap;
import java.util.Map;

@Api(value = "MonthLockController", tags = {"月结锁定接口"})
@RestController
@RequestMapping("/sharedBook/{bookId}/monthLock")
public class MonthLockController {

    @Autowired
    private MonthLockService monthLockService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;
    @Autowired
    private RecordBookService recordBookService;

    @CommonLog(title = "锁定月份", businessType = BusinessType.INSERT)
    @PostMapping("/{yearMonth}")
    public Result<?> lockMonth(@PathVariable @Positive Integer bookId,
                               @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_SETTLE);
        monthLockService.lockMonth(bookId, yearMonth, userId.intValue());
        return Result.success();
    }

    @CommonLog(title = "解锁月份", businessType = BusinessType.DELETE)
    @DeleteMapping("/{yearMonth}")
    public Result<?> unlockMonth(@PathVariable @Positive Integer bookId,
                                 @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        // 只有账本创建者可以解锁
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || !book.getUserId().equals(userId.intValue())) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
        }
        monthLockService.unlockMonth(bookId, yearMonth, userId.intValue());
        return Result.success();
    }

    @GetMapping("/{yearMonth}")
    public Result<?> checkLockStatus(@PathVariable @Positive Integer bookId,
                                     @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_VIEW);
        Map<String, Object> result = new HashMap<>();
        result.put("locked", monthLockService.isLocked(bookId, yearMonth));
        result.put("lockInfo", monthLockService.getLock(bookId, yearMonth));
        return Result.success(result);
    }
}
