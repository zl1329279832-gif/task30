package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.dto.ReversalApplicationDTO;
import cn.jackbin.SimpleRecord.entity.ReversalApplicationDO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ReversalApplicationService extends IService<ReversalApplicationDO> {

    void apply(Integer recordBookId, Long originalRecordId, Integer applicantUserId, String reason);

    void approve(Long applicationId, Integer auditorId, String auditRemark);

    void reject(Long applicationId, Integer auditorId, String auditRemark);

    void getByPage(Integer recordBookId, Integer auditStatus, PageBO<ReversalApplicationDTO> pageBO);
}
