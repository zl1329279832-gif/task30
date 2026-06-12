package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.dto.MemberResponsibilitySnapshotDTO;
import cn.jackbin.SimpleRecord.entity.MemberResponsibilitySnapshotDO;
import cn.jackbin.SimpleRecord.service.MemberResponsibilitySnapshotService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成员责任快照控制器
 */
@Api(tags = "成员责任快照")
@RestController
@RequestMapping("/responsibility")
public class MemberResponsibilityController {

    @Autowired
    private MemberResponsibilitySnapshotService snapshotService;

    @ApiOperation("查询责任快照")
    @GetMapping("/{bookId}/snapshots")
    public Result<?> getSnapshots(@PathVariable Integer bookId,
                                  @RequestParam String yearMonth,
                                  @RequestParam(required = false) Integer userId,
                                  @RequestParam(defaultValue = "1") Integer pageNo,
                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        if (userId != null) {
            List<MemberResponsibilitySnapshotDO> list = snapshotService.getSnapshots(bookId, yearMonth, userId);
            return Result.success(list);
        }
        PageBO<MemberResponsibilitySnapshotDO> pageBO = new PageBO<>(pageNo, pageSize);
        snapshotService.getSnapshotsByPage(bookId, yearMonth, pageBO);
        return Result.success(pageBO);
    }

    @ApiOperation("获取重算后的快照(原始+调整)")
    @GetMapping("/{bookId}/recalculated")
    public Result<List<MemberResponsibilitySnapshotDTO>> getRecalculatedSnapshots(
            @PathVariable Integer bookId, @RequestParam String yearMonth) {
        return Result.success(snapshotService.getRecalculatedSnapshots(bookId, yearMonth));
    }
}
