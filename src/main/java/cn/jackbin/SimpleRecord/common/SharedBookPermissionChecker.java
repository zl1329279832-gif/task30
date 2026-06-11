package cn.jackbin.SimpleRecord.common;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.service.MonthlyClosingService;
import cn.jackbin.SimpleRecord.service.RecordBookService;
import cn.jackbin.SimpleRecord.service.SharedBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 共享账本权限检查工具
 */
@Component
public class SharedBookPermissionChecker {

    @Autowired
    private SharedBookService sharedBookService;

    @Autowired
    private RecordBookService recordBookService;

    @Autowired
    @Lazy
    private MonthlyClosingService monthlyClosingService;

    /**
     * 校验某权限
     */
    public void check(Integer bookId, Integer userId, String permission) {
        sharedBookService.checkPermission(bookId, userId, permission);
    }

    /**
     * 校验可以修改记录 (entry权限 + 月未结)
     * 月结后只允许通过冲正流程修改
     */
    public void checkCanModifyRecord(Integer bookId, Integer userId, Date occurTime) {
        sharedBookService.checkPermission(bookId, userId, RecordConstant.PERM_ENTRY);
        // 校验月结状态: 已月结的月份禁止普通修改
        if (occurTime != null) {
            monthlyClosingService.checkNotClosed(bookId, occurTime);
        }
    }

    /**
     * 校验是否为owner
     */
    public void checkOwner(Integer bookId, Integer userId) {
        RecordBookDO book = recordBookService.getById(bookId);
        if (book == null || !userId.equals(book.getOwnerUserId())) {
            throw new BusinessException(CodeMsg.NOT_BOOK_OWNER);
        }
    }

    /**
     * 判断账本是否为共享账本
     */
    public boolean isSharedBook(Integer bookId) {
        RecordBookDO book = recordBookService.getById(bookId);
        return book != null && book.getBookType() != null
                && book.getBookType() == RecordConstant.BOOK_TYPE_SHARED;
    }
}
