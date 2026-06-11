package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.SharedBookAuditLogDO;
import cn.jackbin.SimpleRecord.service.SharedBookAuditLogService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志控制器
 */
@Api(tags = "审计日志")
@RestController
@RequestMapping("/auditLog")
public class SharedBookAuditLogController {

    @Autowired
    private SharedBookAuditLogService auditLogService;

    @ApiOperation("分页查询审计日志")
    @GetMapping("/{bookId}")
    public Result<PageBO<SharedBookAuditLogDO>> list(@PathVariable Integer bookId,
                                                      @RequestParam(required = false) String actionType,
                                                      @RequestParam(defaultValue = "1") Integer pageNo,
                                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        PageBO<SharedBookAuditLogDO> pageBO = new PageBO<>();
        pageBO.setPageNo(pageNo);
        pageBO.setPageSize(pageSize);
        auditLogService.getLogsByPage(bookId, actionType, pageBO);
        return Result.success(pageBO);
    }
}
