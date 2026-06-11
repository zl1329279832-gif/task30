package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.service.SharedBookService;
import cn.jackbin.SimpleRecord.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 共享账本控制器
 */
@Api(tags = "共享账本管理")
@RestController
@RequestMapping("/sharedBook")
public class SharedBookController {

    @Autowired
    private SharedBookService sharedBookService;

    @ApiOperation("创建共享账本")
    @PostMapping("/")
    public Result<RecordBookDO> create(@Validated @RequestBody CreateSharedBookVO vo) {
        Integer userId = LocalUserId.get().intValue();
        RecordBookDO book = sharedBookService.createSharedBook(userId, vo.getName(), vo.getRemark());
        return Result.success(book);
    }

    @ApiOperation("我的共享账本列表")
    @GetMapping("/list")
    public Result<List<RecordBookDO>> list() {
        Integer userId = LocalUserId.get().intValue();
        return Result.success(sharedBookService.listSharedBooks(userId));
    }

    @ApiOperation("获取/刷新邀请码")
    @GetMapping("/{bookId}/inviteCode")
    public Result<String> getInviteCode(@PathVariable Integer bookId) {
        Integer userId = LocalUserId.get().intValue();
        String code = sharedBookService.regenerateInviteCode(bookId, userId);
        return Result.success(code);
    }

    @ApiOperation("通过邀请码加入账本")
    @PostMapping("/join")
    public Result<RecordBookDO> join(@Validated @RequestBody JoinSharedBookVO vo) {
        Integer userId = LocalUserId.get().intValue();
        RecordBookDO book = sharedBookService.joinByInviteCode(userId, vo.getInviteCode());
        return Result.success(book);
    }

    @ApiOperation("账本成员列表")
    @GetMapping("/{bookId}/members")
    public Result<List<SharedBookMemberDO>> listMembers(@PathVariable Integer bookId) {
        return Result.success(sharedBookService.listMembers(bookId));
    }

    @ApiOperation("添加成员")
    @PostMapping("/{bookId}/members")
    public Result<?> addMember(@PathVariable Integer bookId, @Validated @RequestBody AddMemberVO vo) {
        Integer userId = LocalUserId.get().intValue();
        Integer targetUserId;
        try {
            targetUserId = Integer.parseInt(vo.getUserIdOrUsername());
        } catch (NumberFormatException e) {
            // 用户名模式暂不支持, 需要用户名查询, 此处简化为直接使用ID
            throw new cn.jackbin.SimpleRecord.exception.BusinessException(
                    cn.jackbin.SimpleRecord.constant.CodeMsg.PARAMETER_ILLEGAL, "请传入用户ID");
        }
        sharedBookService.addMember(bookId, userId, targetUserId, vo.getPermissions());
        return Result.success();
    }

    @ApiOperation("移除成员")
    @DeleteMapping("/{bookId}/members/{targetUserId}")
    public Result<?> removeMember(@PathVariable Integer bookId, @PathVariable Integer targetUserId) {
        Integer userId = LocalUserId.get().intValue();
        sharedBookService.removeMember(bookId, userId, targetUserId);
        return Result.success();
    }

    @ApiOperation("更新成员权限")
    @PutMapping("/{bookId}/members/{targetUserId}/permissions")
    public Result<?> updatePermissions(@PathVariable Integer bookId, @PathVariable Integer targetUserId,
                                       @Validated @RequestBody UpdatePermissionVO vo) {
        Integer userId = LocalUserId.get().intValue();
        sharedBookService.updatePermissions(bookId, userId, targetUserId, vo.getPermissions());
        return Result.success();
    }
}
