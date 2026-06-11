package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.RecordDetailBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.vo.RecordDetailVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;

/**
 * @author: create by bin
 * @version: v1.0
 * @description: 记账策略控制
 * @date: 2021/10/8 21:02
 **/
@Component
public class RecordDetailContext {

    @Autowired
    private RecordDetailFactory factory;

    @Autowired
    private DictItemService dictItemService;

    @Autowired
    private DictService dictService;
    @Autowired
    private RecordAccountService recordAccountService;
    @Autowired
    private RecordBookService recordBookService;
    @Autowired
    private RecordDetailService recordDetailService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;
    @Autowired
    private MonthLockService monthLockService;

    /**
     * 新增记账记录
     */
    @Transactional
    public void addOrEdit(Integer userId, RecordDetailVO vo) {
        RecordDetailHandler handler = factory.getHandler(vo.getRecordTypeCode());
        if (handler == null) {
            throw new BusinessException(CodeMsg.BUSINESS_ERROR);
        }
        // 获取dictDO
        DictDO dictDO = dictService.getByCode(RecordConstant.RECORD_TYPE);
        // 从字典获取recordType
        DictItemDO dictItemDO = dictItemService.getByValue(dictDO.getId().intValue(), vo.getRecordTypeCode());
        RecordDetailBO bo = new RecordDetailBO();
        BeanUtils.copyProperties(vo, bo);
        // 设置recordTypeId
        bo.setRecordTypeId(dictItemDO.getId().intValue());

        // 获取账本信息，判断是否为共享账本
        RecordBookDO book = recordBookService.getById(bo.getRecordBookId());
        boolean isSharedBook = book != null
                && book.getBookType() != null
                && book.getBookType() == RecordConstant.BOOK_TYPE_SHARED;

        beforeHandle(vo.getId(), userId, bo.getTargetAccountId(), bo.getRecordBookId(), isSharedBook);

        // 共享账本月结锁定检查
        if (isSharedBook && vo.getOccurTime() != null) {
            String yearMonth = new SimpleDateFormat("yyyy-MM").format(vo.getOccurTime());
            monthLockService.checkNotLocked(bo.getRecordBookId(), yearMonth);
        }

        handler.check(userId, bo);
        // 如果有id就是编辑
        if (vo.getId() != null){
            // 共享账本中，只允许编辑自己的待审核记录
            if (isSharedBook) {
                RecordDetailDO existing = recordDetailService.getById(vo.getId());
                if (existing != null) {
                    if (!existing.getUserId().equals(userId)) {
                        throw new BusinessException(CodeMsg.OPERATE_RECORD_FORBIDDEN);
                    }
                    if (existing.getAuditStatus() != null
                            && existing.getAuditStatus() != RecordConstant.AUDIT_PENDING) {
                        throw new BusinessException(CodeMsg.SHARED_BOOK_RECORD_APPROVED_NO_EDIT);
                    }
                }
            }
            handler.handleUpdate(bo);
        }else {
            handler.handleAdd(userId, bo);
            // 共享账本新增记录，设置审核状态为待审核
            if (isSharedBook) {
                RecordDetailDO newRecord = recordDetailService.getById(bo.getId());
                if (newRecord != null) {
                    newRecord.setAuditStatus(RecordConstant.AUDIT_PENDING);
                    newRecord.setPayerUserId(bo.getPayerUserId());
                    recordDetailService.updateById(newRecord);
                }
            }
        }
    }

    @Transactional
    public void del(Integer userId, Integer id) {
        RecordDetailDO detail = recordDetailService.getById(id);
        // 从字典获取recordType
        DictItemDO dictItemDO = dictItemService.getById(detail.getRecordType());
        RecordDetailHandler handler = factory.getHandler(dictItemDO.getValue());
        if (handler == null) {
            throw new BusinessException(CodeMsg.BUSINESS_ERROR);
        }

        // 获取账本信息
        RecordBookDO book = recordBookService.getById(detail.getRecordBookId());
        boolean isSharedBook = book != null
                && book.getBookType() != null
                && book.getBookType() == RecordConstant.BOOK_TYPE_SHARED;

        if (isSharedBook) {
            // 共享账本：检查权限和状态
            sharedBookMemberService.checkPermission(detail.getRecordBookId(), userId, RecordConstant.PERM_RECORD);
            if (!userId.equals(detail.getUserId())) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_FORBIDDEN);
            }
            if (detail.getAuditStatus() != null
                    && detail.getAuditStatus() != RecordConstant.AUDIT_PENDING) {
                throw new BusinessException(CodeMsg.SHARED_BOOK_RECORD_APPROVED_NO_EDIT);
            }
            // 月结锁定检查
            if (detail.getOccurTime() != null) {
                String yearMonth = new SimpleDateFormat("yyyy-MM").format(detail.getOccurTime());
                monthLockService.checkNotLocked(detail.getRecordBookId(), yearMonth);
            }
        } else {
            // 个人账本：原有逻辑
            if (!userId.equals(detail.getUserId())) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_ACCOUNT_FORBIDDEN);
            }
        }

        handler.handleDel(detail);
    }

    /**
     * 校验记账的数据是否合规
     */
    private void beforeHandle(Long id, Integer userId, Integer targetAccountId,
                              Integer recordBookId, boolean isSharedBook) {
        if (isSharedBook) {
            // 共享账本：检查成员录入权限
            sharedBookMemberService.checkPermission(recordBookId, userId, RecordConstant.PERM_RECORD);
            // 共享账本中账户属于账本创建者，跳过用户所属校验
            RecordAccountDO recordAccountDO = recordAccountService.getById(targetAccountId);
            if (recordAccountDO == null) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_ACCOUNT_FORBIDDEN);
            }
        } else {
            // 个人账本：校验账户和账单是否属于该用户
            RecordAccountDO recordAccountDO = recordAccountService.getById(targetAccountId);
            if (recordAccountDO == null || !recordAccountDO.getUserId().equals(userId)) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_ACCOUNT_FORBIDDEN);
            }
            RecordBookDO recordBookDO = recordBookService.getById(recordBookId);
            if (recordBookDO == null || !recordBookDO.getUserId().equals(userId)) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_BOOK_FORBIDDEN);
            }
        }
        // 如果有id，校验记录是否存在
        if (id != null && recordDetailService.getById(id) == null){
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
    }
}
