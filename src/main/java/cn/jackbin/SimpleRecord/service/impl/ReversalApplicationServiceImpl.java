package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.dto.ReversalApplicationDTO;
import cn.jackbin.SimpleRecord.entity.RecordDetailDO;
import cn.jackbin.SimpleRecord.entity.ReversalApplicationDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.ReversalApplicationMapper;
import cn.jackbin.SimpleRecord.service.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class ReversalApplicationServiceImpl extends ServiceImpl<ReversalApplicationMapper, ReversalApplicationDO>
        implements ReversalApplicationService {

    @Autowired
    private ReversalApplicationMapper reversalApplicationMapper;

    @Autowired
    private RecordDetailService recordDetailService;

    @Autowired
    private SharedBookLogService sharedBookLogService;

    @Autowired
    private BudgetService budgetService;

    @Override
    public void apply(Integer recordBookId, Long originalRecordId, Integer applicantUserId, String reason) {
        RecordDetailDO original = recordDetailService.getById(originalRecordId);
        if (original == null || !original.getRecordBookId().equals(recordBookId)) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }
        if (original.getAuditStatus() == null || original.getAuditStatus() != RecordConstant.AUDIT_APPROVED) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_RECORD_NOT_PENDING);
        }

        ReversalApplicationDO application = ReversalApplicationDO.builder()
                .recordBookId(recordBookId)
                .originalRecordId(originalRecordId)
                .applicantUserId(applicantUserId)
                .reason(reason)
                .auditStatus(RecordConstant.AUDIT_PENDING)
                .status(0)
                .build();
        save(application);

        sharedBookLogService.log(recordBookId, applicantUserId, "REVERSAL_APPLY",
                application.getId(), "提交冲正申请，原始记录ID: " + originalRecordId);
    }

    @Transactional
    @Override
    public void approve(Long applicationId, Integer auditorId, String auditRemark) {
        ReversalApplicationDO application = getById(applicationId);
        if (application == null) {
            throw new BusinessException(CodeMsg.REVERSAL_NOT_FOUND);
        }
        if (application.getAuditStatus() != RecordConstant.AUDIT_PENDING) {
            throw new BusinessException(CodeMsg.REVERSAL_ALREADY_PROCESSED);
        }

        // 加载原始记录
        RecordDetailDO original = recordDetailService.getById(application.getOriginalRecordId());
        if (original == null) {
            throw new BusinessException(CodeMsg.NOT_FIND_DATA);
        }

        // 创建冲正记录：相同字段但金额取反
        RecordDetailDO reversalRecord = new RecordDetailDO();
        reversalRecord.setUserId(original.getUserId());
        reversalRecord.setRecordAccountId(original.getRecordAccountId());
        reversalRecord.setRecordBookId(original.getRecordBookId());
        reversalRecord.setRecordType(original.getRecordType());
        reversalRecord.setRecordCategory(original.getRecordCategory());
        reversalRecord.setSourceAccountId(original.getSourceAccountId());
        reversalRecord.setTargetAccountId(original.getTargetAccountId());
        reversalRecord.setAmount(-original.getAmount()); // 金额取反
        reversalRecord.setOccurTime(original.getOccurTime());
        reversalRecord.setTag(original.getTag());
        reversalRecord.setRemark("[冲正] 原记录ID:" + original.getId()
                + (original.getRemark() != null ? " | " + original.getRemark() : ""));
        reversalRecord.setStatus(original.getStatus());
        reversalRecord.setRecoverableStatus(original.getRecoverableStatus());
        reversalRecord.setAuditStatus(RecordConstant.AUDIT_APPROVED);
        reversalRecord.setAuditorId(auditorId);
        reversalRecord.setAuditTime(new Date());
        reversalRecord.setPayerUserId(original.getPayerUserId());
        reversalRecord.setRelationRecordId(original.getId().intValue());
        recordDetailService.save(reversalRecord);

        // 更新原始记录状态为已冲正
        original.setAuditStatus(RecordConstant.AUDIT_REVERSED);
        recordDetailService.updateById(original);

        // 更新申请
        application.setReversalRecordId(reversalRecord.getId());
        application.setAuditStatus(RecordConstant.AUDIT_APPROVED);
        application.setAuditorId(auditorId);
        application.setAuditTime(new Date());
        application.setAuditRemark(auditRemark);
        updateById(application);

        // 使预算缓存失效
        String yearMonth = new SimpleDateFormat("yyyy-MM").format(original.getOccurTime());
        budgetService.invalidateCache(original.getRecordBookId(), yearMonth);

        sharedBookLogService.log(application.getRecordBookId(), auditorId, "REVERSAL_APPROVE",
                applicationId, "冲正申请通过，生成冲正记录ID: " + reversalRecord.getId());
    }

    @Override
    public void reject(Long applicationId, Integer auditorId, String auditRemark) {
        ReversalApplicationDO application = getById(applicationId);
        if (application == null) {
            throw new BusinessException(CodeMsg.REVERSAL_NOT_FOUND);
        }
        if (application.getAuditStatus() != RecordConstant.AUDIT_PENDING) {
            throw new BusinessException(CodeMsg.REVERSAL_ALREADY_PROCESSED);
        }

        application.setAuditStatus(RecordConstant.AUDIT_REJECTED);
        application.setAuditorId(auditorId);
        application.setAuditTime(new Date());
        application.setAuditRemark(auditRemark);
        updateById(application);

        sharedBookLogService.log(application.getRecordBookId(), auditorId, "REVERSAL_REJECT",
                applicationId, "冲正申请驳回");
    }

    @Override
    public void getByPage(Integer recordBookId, Integer auditStatus, PageBO<ReversalApplicationDTO> pageBO) {
        Page<ReversalApplicationDTO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        IPage<ReversalApplicationDTO> result = reversalApplicationMapper.queryByBookId(page, recordBookId, auditStatus);
        pageBO.setTotal((int) result.getTotal());
        pageBO.setList(result.getRecords());
    }
}
