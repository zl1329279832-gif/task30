package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.SharedBookAuditLogDO;
import cn.jackbin.SimpleRecord.mapper.SharedBookAuditLogMapper;
import cn.jackbin.SimpleRecord.service.SharedBookAuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 共享账本审计日志服务实现
 */
@Service
public class SharedBookAuditLogServiceImpl extends ServiceImpl<SharedBookAuditLogMapper, SharedBookAuditLogDO>
        implements SharedBookAuditLogService {

    @Override
    public void log(Integer bookId, Integer operatorId, String actionType,
                    String targetType, Long targetId, String detail) {
        SharedBookAuditLogDO logEntry = SharedBookAuditLogDO.builder()
                .bookId(bookId)
                .operatorId(operatorId)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .createTime(new Date())
                .build();
        save(logEntry);
    }

    @Override
    public void getLogsByPage(Integer bookId, String actionType, PageBO<SharedBookAuditLogDO> pageBO) {
        QueryWrapper<SharedBookAuditLogDO> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId);
        if (StringUtils.isNotBlank(actionType)) {
            wrapper.eq("action_type", actionType);
        }
        wrapper.orderByDesc("create_time");

        IPage<SharedBookAuditLogDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        IPage<SharedBookAuditLogDO> result = page(page, wrapper);
        pageBO.setList(result.getRecords());
        pageBO.setTotal((int) result.getTotal());
    }
}
