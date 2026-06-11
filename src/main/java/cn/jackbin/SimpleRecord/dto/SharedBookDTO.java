package cn.jackbin.SimpleRecord.dto;

import lombok.Data;

/**
 * 共享账本DTO
 */
@Data
public class SharedBookDTO {
    private Long bookId;
    private String name;
    private String ownerName;
    private Integer memberCount;
    private String currentUserRole;
    private String inviteCode;
    private String remark;
}
