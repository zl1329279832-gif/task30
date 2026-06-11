package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.SharedBookLogDO;
import cn.jackbin.SimpleRecord.mapper.SharedBookLogMapper;
import cn.jackbin.SimpleRecord.service.SharedBookLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SharedBookLogServiceImpl extends ServiceImpl<SharedBookLogMapper, SharedBookLogDO>
        implements SharedBookLogService {

    @Autowired
    private SharedBookLogMapper sharedBookLogMapper;

    @Async
    @Override
    public void log(Integer recordBookId, Integer operUserId, String operType, Long targetId, String content) {
        SharedBookLogDO logDO = SharedBookLogDO.builder()
                .recordBookId(recordBookId)
                .operUserId(operUserId)
                .operType(operType)
                .targetId(targetId)
                .content(content)
                .status(0)
                .build();
        save(logDO);
    }

    @Override
    public void getByPage(Integer recordBookId, String operType, PageBO<SharedBookLogDO> pageBO) {
        Page<SharedBookLogDO> page = new Page<>(pageBO.getPageNo(), pageBO.getPageSize());
        IPage<SharedBookLogDO> result = sharedBookLogMapper.queryByBookId(page, recordBookId, operType);
        pageBO.setTotal((int) result.getTotal());
        pageBO.setList(result.getRecords());
    }
}
