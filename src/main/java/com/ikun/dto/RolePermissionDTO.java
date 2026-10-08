package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色权限分配参数
 *
 * <p>采用「全量覆盖」语义：提交的列表就是该角色最终的权限集合。
 * 相比增量增删，覆盖式更不容易因为前端漏传而产生残留权限。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "角色权限分配参数")
public class RolePermissionDTO {

    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "勾选的权限ID集合，传空数组表示清空该角色全部权限")
    private List<Long> permissionIds;
}
