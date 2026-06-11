package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.SharedBookLogDO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SharedBookLogService extends IService<SharedBookLogDO> {

    void log(Integer recordBookId, Integer operUserId, String operType, Long targetId, String content);

    void getByPage(Integer recordBookId, String operType, PageBO<SharedBookLogDO> pageBO);
}
