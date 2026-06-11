package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.SharedBookAuditLogDO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 共享账本审计日志服务
 */
public interface SharedBookAuditLogService extends IService<SharedBookAuditLogDO> {

    /**
     * 记录审计日志
     */
    void log(Integer bookId, Integer operatorId, String actionType,
             String targetType, Long targetId, String detail);

    /**
     * 分页查询审计日志
     */
    void getLogsByPage(Integer bookId, String actionType, PageBO<SharedBookAuditLogDO> pageBO);
}
