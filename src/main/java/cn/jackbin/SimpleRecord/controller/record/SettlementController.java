package cn.jackbin.SimpleRecord.controller.record;

import cn.jackbin.SimpleRecord.common.LocalUserId;
import cn.jackbin.SimpleRecord.common.anotations.CommonLog;
import cn.jackbin.SimpleRecord.common.enums.BusinessType;
import cn.jackbin.SimpleRecord.constant.RecordConstant;
import cn.jackbin.SimpleRecord.entity.SettlementDO;
import cn.jackbin.SimpleRecord.service.SettlementService;
import cn.jackbin.SimpleRecord.service.SharedBookMemberService;
import cn.jackbin.SimpleRecord.vo.Result;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;
import java.util.List;

@Api(value = "SettlementController", tags = {"结算接口"})
@RestController
@RequestMapping("/sharedBook/{bookId}/settlement")
public class SettlementController {

    @Autowired
    private SettlementService settlementService;
    @Autowired
    private SharedBookMemberService sharedBookMemberService;

    @GetMapping("/{yearMonth}/preview")
    public Result<?> previewSettlement(@PathVariable @Positive Integer bookId,
                                       @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_SETTLE);
        List<SettlementDO> settlements = settlementService.calculateSettlement(bookId, yearMonth);
        return Result.success(settlements);
    }

    @CommonLog(title = "生成结算记录", businessType = BusinessType.INSERT)
    @PostMapping("/{yearMonth}/generate")
    public Result<?> generateSettlement(@PathVariable @Positive Integer bookId,
                                        @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_SETTLE);
        settlementService.generateSettlement(bookId, yearMonth, userId.intValue());
        return Result.success();
    }

    @CommonLog(title = "确认结算", businessType = BusinessType.UPDATE)
    @PostMapping("/{settlementId}/confirm")
    public Result<?> confirmSettlement(@PathVariable @Positive Integer bookId,
                                       @PathVariable @Positive Long settlementId) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_SETTLE);
        settlementService.confirmSettlement(settlementId, userId.intValue());
        return Result.success();
    }

    @GetMapping("/{yearMonth}")
    public Result<?> listSettlements(@PathVariable @Positive Integer bookId,
                                     @PathVariable String yearMonth) {
        Long userId = LocalUserId.get();
        sharedBookMemberService.checkPermission(bookId, userId.intValue(), RecordConstant.PERM_VIEW);
        List<SettlementDO> settlements = settlementService.getSettlements(bookId, yearMonth);
        return Result.success(settlements);
    }
}
