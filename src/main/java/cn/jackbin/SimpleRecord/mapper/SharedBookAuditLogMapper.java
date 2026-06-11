package cn.jackbin.SimpleRecord.mapper;

import cn.jackbin.SimpleRecord.entity.SharedBookAuditLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

/**
 * 共享账本审计日志 Mapper
 */
@Repository
public interface SharedBookAuditLogMapper extends BaseMapper<SharedBookAuditLogDO> {
}
