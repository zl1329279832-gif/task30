package cn.jackbin.SimpleRecord.dto;

import lombok.Data;

/**
 * 共享账本成员DTO
 */
@Data
public class SharedBookMemberDTO {
    private Integer userId;
    private String username;
    private String nickname;
    private String permissions;
    private String avatarUrl;
}
