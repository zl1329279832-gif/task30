package cn.jackbin.SimpleRecord.dto;

import cn.jackbin.SimpleRecord.entity.SharedBookMemberDO;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SharedBookMemberDTO extends SharedBookMemberDO {

    private static final long serialVersionUID = 1L;

    private String username;

    private String userNickname;

    private String avatarUrl;
}
