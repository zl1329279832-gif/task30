package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.dto.MemberResponsibilitySnapshotDTO;
import cn.jackbin.SimpleRecord.entity.MemberResponsibilitySnapshotDO;

import java.util.List;

/**
 * 成员责任快照服务
 */
public interface MemberResponsibilitySnapshotService {

    /**
     * 月结时生成责任快照
     */
    void generateSnapshots(Integer bookId, String yearMonth, Long closingId);

    /**
     * 查询某月快照 (可按成员过滤)
     */
    List<MemberResponsibilitySnapshotDO> getSnapshots(Integer bookId, String yearMonth, Integer userId);

    /**
     * 获取重算后的快照 (原始 + 调整合并, 不修改DB)
     */
    List<MemberResponsibilitySnapshotDTO> getRecalculatedSnapshots(Integer bookId, String yearMonth);

    /**
     * 分页查询快照
     */
    void getSnapshotsByPage(Integer bookId, String yearMonth, PageBO<MemberResponsibilitySnapshotDO> pageBO);
}
