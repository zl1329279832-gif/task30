package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.RecordDetailBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.*;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.vo.RecordDetailVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author: create by bin
 * @version: v1.0
 * @description: 记账策略控制 (已集成共享账本逻辑)
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
    @Lazy
    private SharedBookService sharedBookService;

    @Autowired
    @Lazy
    private SharedBookAuditLogService auditLogService;

    @Autowired
    @Lazy
    private MonthlyClosingService monthlyClosingService;

    @Autowired
    @Lazy
    private BudgetService budgetService;

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

        // 获取账本信息用于判断类型
        RecordBookDO bookDO = recordBookService.getById(bo.getRecordBookId());
        if (bookDO == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
        boolean isShared = bookDO.getBookType() != null && bookDO.getBookType() == RecordConstant.BOOK_TYPE_SHARED;

        beforeHandle(vo.getId(), userId, bo.getTargetAccountId(), bo.getRecordBookId(), bookDO, isShared);

        // 编辑时检查: 共享账本已入账记录不可编辑
        if (vo.getId() != null && isShared) {
            RecordDetailDO existing = recordDetailService.getById(vo.getId());
            if (existing != null && (existing.getReviewStatus() == RecordConstant.REVIEW_POSTED
                    || existing.getReviewStatus() == RecordConstant.REVIEW_REVERSED)) {
                throw new BusinessException(CodeMsg.CANNOT_EDIT_POSTED_RECORD);
            }
            // 检查月结锁定
            monthlyClosingService.checkNotClosed(vo.getRecordBookId(), bo.getOccurTime());
        }

        handler.check(userId, bo);
        // 如果有id就是编辑
        if (vo.getId() != null){
            handler.handleUpdate(bo);
        }else {
            int newRecordId = handler.handleAdd(userId, bo);
            // 共享账本: 新记录进入待审核状态
            if (isShared && newRecordId > 0) {
                RecordDetailDO newRecord = recordDetailService.getById(newRecordId);
                if (newRecord != null) {
                    newRecord.setReviewStatus(RecordConstant.REVIEW_PENDING);
                    recordDetailService.updateById(newRecord);
                    auditLogService.log(bookDO.getId().intValue(), userId,
                            "RECORD_SUBMIT", "RECORD", (long) newRecordId, null);
                    // 预算软检查 (仅支出, 不阻止)
                    if (RecordDetailHandler.EXPEND_TYPE.equals(vo.getRecordTypeCode())) {
                        String ym = new SimpleDateFormat("yyyy-MM").format(bo.getOccurTime());
                        budgetService.checkBudgetWarning(vo.getRecordBookId(),
                                BigDecimal.valueOf(vo.getAmount()), ym);
                    }
                }
            }
        }
    }

    @Transactional
    public void del(Integer userId, Integer id) {
        RecordDetailDO detail = recordDetailService.getById(id);
        if (detail == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }

        RecordBookDO bookDO = recordBookService.getById(detail.getRecordBookId());
        boolean isShared = bookDO != null && bookDO.getBookType() != null
                && bookDO.getBookType() == RecordConstant.BOOK_TYPE_SHARED;

        if (isShared) {
            // 共享账本: 检查entry权限
            sharedBookService.checkPermission(detail.getRecordBookId(), userId, RecordConstant.PERM_ENTRY);
            // 已入账记录不可删除
            if (detail.getReviewStatus() == RecordConstant.REVIEW_POSTED
                    || detail.getReviewStatus() == RecordConstant.REVIEW_REVERSED) {
                throw new BusinessException(CodeMsg.CANNOT_DELETE_POSTED_RECORD);
            }
            // 月结检查
            monthlyClosingService.checkNotClosed(detail.getRecordBookId(), detail.getOccurTime());
        } else {
            // 个人账本: 原始逻辑
            if (!userId.equals(detail.getUserId())){
                throw new BusinessException(CodeMsg.OPERATE_RECORD_ACCOUNT_FORBIDDEN);
            }
        }

        // 从字典获取recordType
        DictItemDO dictItemDO = dictItemService.getById(detail.getRecordType());
        RecordDetailHandler handler = factory.getHandler(dictItemDO.getValue());
        if (handler == null) {
            throw new BusinessException(CodeMsg.BUSINESS_ERROR);
        }
        handler.handleDel(detail);
    }

    /**
     * 校验记账的数据是否合规
     */
    private void beforeHandle(Long id, Integer userId, Integer targetAccountId, Integer recordBookId,
                              RecordBookDO bookDO, boolean isShared) {
        if (isShared) {
            // 共享账本: 检查成员权限 + 月结锁定
            sharedBookService.checkPermission(recordBookId, userId, RecordConstant.PERM_ENTRY);
            // 目标账户存在性检查 (共享账本中账户可被所有成员使用)
            RecordAccountDO recordAccountDO = recordAccountService.getById(targetAccountId);
            if (recordAccountDO == null) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_ACCOUNT_FORBIDDEN);
            }
        } else {
            // 个人账本: 校验账户和账单是否属于该用户
            RecordAccountDO recordAccountDO = recordAccountService.getById(targetAccountId);
            if (recordAccountDO == null || !recordAccountDO.getUserId().equals(userId)) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_ACCOUNT_FORBIDDEN);
            }
            if (bookDO == null || !bookDO.getUserId().equals(userId)) {
                throw new BusinessException(CodeMsg.OPERATE_RECORD_BOOK_FORBIDDEN);
            }
        }
        // 如果有id，校验记录是否存在
        if (id != null && recordDetailService.getById(id) == null){
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
    }
}
