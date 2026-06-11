package cn.jackbin.SimpleRecord.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
public class UpdateMemberPermissionVO {

    @NotBlank(message = "权限不能为空")
    private String permission;
}
