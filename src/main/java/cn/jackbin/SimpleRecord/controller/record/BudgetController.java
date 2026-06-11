package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.common.anotations.CommonLog;
import cn.jackbin.SimpleRecord.common.enums.BusinessType;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.dto.BudgetExecutionDTO;
import cn.jackbin.SimpleRecord.dto.BudgetWarnDTO;
import cn.jackbin.SimpleRecord.entity.BudgetDO;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.BudgetService;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.vo.AddBudgetVO;
import cn.jackbin.SimpleRecord.vo.EditBudgetVO;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;
import java.util.List;

@Api(value = "BudgetController", tags = {"预算管理接口"})
@RestController
@RequestMapping("/budget")
public class BudgetController {

    @Autowired
    private BudgetService budgetService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;
    @Autowired
    private RecordBookService recordBookService;

    @CommonLog(title = "创建预算", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<?> addBudget(@RequestBody @Validated AddBudgetVO vo) {
        Long userId = LocalUserId.get();
        checkBudgetPermission(vo.getRecordBookId(), userId.intValue());
        budgetService.addBudget(vo.getRecordBookId(), vo.getBudgetType(), vo.getCategoryName(),
                vo.getMemberUserId(), vo.getYearMonth(), vo.getBudgetAmount(), vo.getWarnPercent());
        return Result.success();
    }

    @CommonLog(title = "修改预算", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public Result<?> editBudget(@PathVariable @Positive Long id,
                                @RequestBody @Validated EditBudgetVO vo) {
        Long userId = LocalUserId.get();
        BudgetDO budget = budgetService.getById(id);
        if (budget == null) {
            throw new BusinessException(CodeMsg.BUDGET_NOT_FOUND);
        }
        checkBudgetPermission(budget.getRecordBookId(), userId.intValue());
        budgetService.editBudget(id, vo.getBudgetAmount(), vo.getWarnPercent());
        return Result.success();
    }

    @CommonLog(title = "删除预算", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<?> deleteBudget(@PathVariable @Positive Long id) {
        Long userId = LocalUserId.get();
        BudgetDO budget = budgetService.getById(id);
        if (budget == null) {
            throw new BusinessException(CodeMsg.BUDGET_NOT_FOUND);
        }
        checkBudgetPermission(budget.getRecordBookId(), userId.intValue());
        budgetService.deleteBudget(id);
        return Result.success();
    }

    @GetMapping("/{bookId}/{yearMonth}/execution")
    public Result<?> getBudgetExecution(@PathVariable @Positive Integer bookId,
                                        @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        checkViewPermission(bookId, userId.intValue());
        List<BudgetExecutionDTO> result = budgetService.getBudgetExecution(bookId, yearMonth);
        return Result.success(result);
    }

    @GetMapping("/{bookId}/{yearMonth}/execution/category")
    public Result<?> getBudgetExecutionByCategory(@PathVariable @Positive Integer bookId,
                                                   @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        checkViewPermission(bookId, userId.intValue());
        List<BudgetExecutionDTO> result = budgetService.getBudgetExecutionByCategory(bookId, yearMonth);
        return Result.success(result);
    }

    @GetMapping("/{bookId}/{yearMonth}/execution/member")
    public Result<?> getBudgetExecutionByMember(@PathVariable @Positive Integer bookId,
                                                 @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        checkViewPermission(bookId, userId.intValue());
        List<BudgetExecutionDTO> result = budgetService.getBudgetExecutionByMember(bookId, yearMonth);
        return Result.success(result);
    }

    @GetMapping("/{bookId}/{yearMonth}/warnings")
    public Result<?> getWarnings(@PathVariable @Positive Integer bookId,
                                  @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        checkViewPermission(bookId, userId.intValue());
        List<BudgetWarnDTO> warnings = budgetService.getOverLimitWarnings(bookId, yearMonth);
        return Result.success(warnings);
    }

    private void checkBudgetPermission(Integer bookId, Integer userId) {
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NOT_FOUND);
        }
        if (book.getBookType() != null && book.getBookType() == RecordConstant.BOOK_TYPE_SHARED) {
            sharedBookMemberService.checkPermission(bookId, userId, RecordConstant.PERM_SETTLE);
        } else {
            if (!book.getUserId().equals(userId)) {
                throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
            }
        }
    }

    private void checkViewPermission(Integer bookId, Integer userId) {
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NOT_FOUND);
        }
        if (book.getBookType() != null && book.getBookType() == RecordConstant.BOOK_TYPE_SHARED) {
            sharedBookMemberService.checkPermission(bookId, userId, RecordConstant.PERM_VIEW);
        } else {
            if (!book.getUserId().equals(userId)) {
                throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
            }
        }
    }
}
