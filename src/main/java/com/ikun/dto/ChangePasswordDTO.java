package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改当前登录用户密码请求参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "修改密码请求参数")
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码", example = "123456")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需在 6-20 位之间")
    @Schema(description = "新密码（6-20 位）", example = "abc123456")
    private String newPassword;
}
