package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.RecordBookDO;
import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.SharedBookMemberMapper;
import cn.jackbin.SimpleRecord.service.impl.SharedBookServiceImpl;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 共享账本服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("共享账本服务测试")
class SharedBookServiceTest {

    @InjectMocks
    private SharedBookServiceImpl sharedBookService;

    @Mock
    private SharedBookMemberMapper sharedBookMemberMapper;

    @Mock
    private RecordBookService recordBookService;

    @Mock
    private SharedBookAuditLogService auditLogService;

    @Mock
    private RedisUtil redisUtil;

    private static final Integer BOOK_ID = 1;
    private static final Integer OWNER_ID = 100;
    private static final Integer MEMBER_ID = 200;

    @Test
    @DisplayName("entry权限用户审批 → SHARED_BOOK_PERMISSION_DENIED")
    void testEntryPermissionCannotApprove() {
        SharedBookMemberDO member = SharedBookMemberDO.builder()
                .bookId(BOOK_ID).userId(MEMBER_ID)
                .permissions("entry,view").status(0).build();
        RecordBookDO book = buildBook(OWNER_ID);

        when(recordBookService.getById(BOOK_ID)).thenReturn(book);
        when(redisUtil.get(anyString())).thenReturn(null);
        // 模拟 getMember 返回该成员
        lenient().when(sharedBookMemberMapper.selectOne(any(QueryWrapper.class))).thenReturn(member);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sharedBookService.checkPermission(BOOK_ID, MEMBER_ID, RecordConstant.PERM_REVIEW));
        assertEquals(CodeMsg.SHARED_BOOK_PERMISSION_DENIED.getRetCode(), ex.getCodeMsg().getRetCode());
    }

    @Test
    @DisplayName("非成员操作 → SHARED_BOOK_PERMISSION_DENIED")
    void testNonMemberDenied() {
        RecordBookDO book = buildBook(OWNER_ID);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);
        when(redisUtil.get(anyString())).thenReturn(null);
        lenient().when(sharedBookMemberMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        boolean result = sharedBookService.hasPermission(BOOK_ID, 999, RecordConstant.PERM_ENTRY);
        assertFalse(result);
    }

    @Test
    @DisplayName("owner拥有所有权限")
    void testOwnerHasAllPermissions() {
        RecordBookDO book = buildBook(OWNER_ID);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        assertTrue(sharedBookService.hasPermission(BOOK_ID, OWNER_ID, RecordConstant.PERM_ENTRY));
        assertTrue(sharedBookService.hasPermission(BOOK_ID, OWNER_ID, RecordConstant.PERM_REVIEW));
        assertTrue(sharedBookService.hasPermission(BOOK_ID, OWNER_ID, RecordConstant.PERM_VIEW));
        assertTrue(sharedBookService.hasPermission(BOOK_ID, OWNER_ID, RecordConstant.PERM_SETTLEMENT));
    }

    @Test
    @DisplayName("移除成员 → 权限缓存被清除")
    void testRemoveMemberClearsCache() {
        RecordBookDO book = buildBook(OWNER_ID);
        when(recordBookService.getById(BOOK_ID)).thenReturn(book);

        SharedBookMemberDO member = SharedBookMemberDO.builder()
                .bookId(BOOK_ID).userId(MEMBER_ID).permissions("entry,view").status(0).build();
        lenient().when(sharedBookMemberMapper.selectOne(any(QueryWrapper.class))).thenReturn(member);

        sharedBookService.removeMember(BOOK_ID, OWNER_ID, MEMBER_ID);

        verify(redisUtil).del(contains("shared_book:perms:" + BOOK_ID + ":" + MEMBER_ID));
    }

    private RecordBookDO buildBook(Integer ownerUserId) {
        RecordBookDO book = new RecordBookDO();
        book.setId((long) BOOK_ID);
        book.setUserId(ownerUserId);
        book.setName("测试共享账本");
        book.setBookType(RecordConstant.BOOK_TYPE_SHARED);
        book.setOwnerUserId(ownerUserId);
        return book;
    }
}
