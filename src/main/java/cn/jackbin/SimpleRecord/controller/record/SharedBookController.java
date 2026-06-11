package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.common.anotations.CommonLog;
import cn.jackbin.SimpleRecord.common.enums.BusinessType;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.SharedBookLogDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.SharedBookInvitationService;
import cn.jackbin.SimpleRecord.service.SharedBookLogService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.vo.*;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;
import java.util.List;

@Api(value = "SharedBookController", tags = {"共享账本接口"})
@RestController
@RequestMapping("/sharedBook")
public class SharedBookController {

    @Autowired
    private RecordBookService recordBookService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;
    @Autowired
    private SharedBookInvitationService sharedBookInvitationService;
    @Autowired
    private SharedBookLogService sharedBookLogService;

    @CommonLog(title = "创建共享账本", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<?> createSharedBook(@RequestBody @Validated AddSharedBookVO vo) {
        Long userId = LocalUserId.get();
        recordBookService.addSharedBook(userId.intValue(), vo.getName(), vo.getRemark(), vo.getOrderNo());
        return Result.success();
    }

    @GetMapping("/list")
    public Result<?> listSharedBooks() {
        Long userId = LocalUserId.get();
        List<RecordBookDO> list = recordBookService.getSharedBooksByUser(userId.intValue());
        return Result.success(list);
    }

    @GetMapping("/{bookId}/members")
    public Result<?> listMembers(@PathVariable @Positive(message = "bookId需为正数") Integer bookId) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_VIEW);
        List<SharedBookMemberDO> members = sharedBookMemberService.getMembersByBookId(bookId);
        return Result.success(members);
    }

    @CommonLog(title = "邀请成员", businessType = BusinessType.INSERT)
    @PostMapping("/{bookId}/invite")
    public Result<?> inviteMember(@PathVariable @Positive(message = "bookId需为正数") Integer bookId,
                                  @RequestBody @Validated InviteMemberVO vo) {
        Long userId = LocalUserId.get();
        // 只有账本创建者可以邀请
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || book.getBookType() != RecordConstant.BOOK_TYPE_SHARED) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NOT_FOUND);
        }
        if (!book.getUserId().equals(userId.intValue())) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
        }
        return Result.success(sharedBookInvitationService.createInvitation(bookId, userId.intValue(), vo.getPermission()));
    }

    @CommonLog(title = "接受邀请加入账本", businessType = BusinessType.INSERT)
    @PostMapping("/join/{inviteCode}")
    public Result<?> joinSharedBook(@PathVariable String inviteCode) {
        Long userId = LocalUserId.get();
        sharedBookInvitationService.acceptInvitation(inviteCode, userId.intValue());
        return Result.success();
    }

    @CommonLog(title = "修改成员权限", businessType = BusinessType.UPDATE)
    @PutMapping("/{bookId}/member/{memberId}/permission")
    public Result<?> updateMemberPermission(@PathVariable @Positive(message = "bookId需为正数") Integer bookId,
                                            @PathVariable @Positive(message = "memberId需为正数") Long memberId,
                                            @RequestBody @Validated UpdateMemberPermissionVO vo) {
        Long userId = LocalUserId.get();
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || !book.getUserId().equals(userId.intValue())) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
        }
        sharedBookMemberService.updatePermission(memberId, vo.getPermission());
        sharedBookLogService.log(bookId, userId.intValue(), "UPDATE_PERMISSION", memberId, "修改成员权限");
        return Result.success();
    }

    @CommonLog(title = "移除成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bookId}/member/{targetUserId}")
    public Result<?> removeMember(@PathVariable @Positive(message = "bookId需为正数") Integer bookId,
                                  @PathVariable @Positive(message = "targetUserId需为正数") Integer targetUserId) {
        Long userId = LocalUserId.get();
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || !book.getUserId().equals(userId.intValue())) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NO_PERMISSION);
        }
        if (targetUserId.equals(userId.intValue())) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_OWNER_CANNOT_LEAVE);
        }
        sharedBookMemberService.removeMember(bookId, targetUserId);
        sharedBookLogService.log(bookId, userId.intValue(), "REMOVE_MEMBER", targetUserId.longValue(), "移除成员");
        return Result.success();
    }

    @CommonLog(title = "退出共享账本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bookId}/leave")
    public Result<?> leaveSharedBook(@PathVariable @Positive(message = "bookId需为正数") Integer bookId) {
        Long userId = LocalUserId.get();
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_NOT_FOUND);
        }
        if (book.getUserId().equals(userId.intValue())) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_OWNER_CANNOT_LEAVE);
        }
        sharedBookMemberService.removeMember(bookId, userId.intValue());
        sharedBookLogService.log(bookId, userId.intValue(), "LEAVE", null, "退出账本");
        return Result.success();
    }

    @GetMapping("/{bookId}/logs")
    public Result<?> getLogs(@PathVariable @Positive(message = "bookId需为正数") Integer bookId,
                             @RequestParam(required = false) String operType,
                             @RequestParam(defaultValue = "1") int pageNo,
                             @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_VIEW);
        PageBO<SharedBookLogDO> pageBO = new PageBO<>(pageNo, pageSize);
        sharedBookLogService.getByPage(bookId, operType, pageBO);
        return Result.success(pageBO);
    }
}
