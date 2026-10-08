package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 角色新增/编辑参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "角色新增/编辑参数")
public class SysRoleDTO {

    @Schema(description = "角色ID，新增时为空")
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称", example = "景区管理员")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,30}$", message = "角色编码需为 3-31 位大写字母、数字或下划线，且以字母开头")
    @Schema(description = "角色编码，创建后不建议修改", example = "SCENIC_ADMIN")
    private String roleCode;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "状态：1启用 0禁用", example = "1")
    private Integer status;
}
