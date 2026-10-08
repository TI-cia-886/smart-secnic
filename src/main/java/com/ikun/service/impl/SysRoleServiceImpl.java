package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.common.BusinessException;
import com.ikun.dto.RolePermissionDTO;
import com.ikun.dto.SysRoleDTO;
import com.ikun.entity.SysPermission;
import com.ikun.entity.SysRole;
import com.ikun.entity.SysRolePermission;
import com.ikun.entity.SysUser;
import com.ikun.mapper.SysPermissionMapper;
import com.ikun.mapper.SysRoleMapper;
import com.ikun.mapper.SysRolePermissionMapper;
import com.ikun.mapper.SysUserMapper;
import com.ikun.service.PermissionService;
import com.ikun.service.SysRoleService;
import com.ikun.vo.PermissionTreeVO;
import com.ikun.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    /** 超级管理员角色编码：禁止删除、禁止改编码，否则系统会失去唯一的全权账号 */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysUserMapper sysUserMapper;
    private final PermissionService permissionService;

    @Override
    public List<SysRoleVO> listAll() {
        List<SysRole> roles = sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getId));
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        // 一次性统计各角色的账号数与权限数，避免在循环里逐条 count 造成 N+1 查询
        Map<Long, Long> userCountMap = countByGroup(
                sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery().select(SysUser::getRoleId)));
        Map<Long, Long> permCountMap = countByGroup(
                sysRolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .select(SysRolePermission::getRoleId)));

        return roles.stream().map(role -> {
            SysRoleVO vo = toVO(role);
            vo.setUserCount(userCountMap.getOrDefault(role.getId(), 0L));
            vo.setPermissionCount(permCountMap.getOrDefault(role.getId(), 0L));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public SysRoleVO getDetail(Long id) {
        SysRole role = requireRole(id);
        SysRoleVO vo = toVO(role);
        List<Long> permissionIds = sysRolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, id))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
        vo.setPermissionIds(permissionIds);
        vo.setPermissionCount((long) permissionIds.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(SysRoleDTO dto) {
        assertRoleCodeUnique(dto.getRoleCode(), null);
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        sysRoleMapper.insert(role);
        log.info("新增角色成功：roleCode={}", role.getRoleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRoleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        SysRole exist = requireRole(dto.getId());
        // 超管编码是代码里的硬约定（权限校验会短路放行），改掉会让整个鉴权体系失效
        if (SUPER_ADMIN_CODE.equals(exist.getRoleCode())
                && !SUPER_ADMIN_CODE.equals(dto.getRoleCode())) {
            throw new BusinessException("超级管理员角色编码不允许修改");
        }
        assertRoleCodeUnique(dto.getRoleCode(), dto.getId());

        SysRole role = new SysRole();
        role.setId(dto.getId());
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus());
        sysRoleMapper.updateById(role);
        // 角色被禁用后，其权限缓存必须立刻失效，否则旧权限仍会在 TTL 内生效
        permissionService.evict(dto.getId());
        log.info("编辑角色成功：id={}", dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRole(Long id) {
        SysRole role = requireRole(id);
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BusinessException("超级管理员角色不允许删除");
        }
        Long userCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleId, id));
        if (userCount != null && userCount > 0) {
            // 直接删会让这些账号的 roleId 变成悬空引用，登录后取不到任何权限
            throw new BusinessException("该角色下仍有 " + userCount + " 个账号，请先转移账号后再删除");
        }
        sysRoleMapper.deleteById(id);
        sysRolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, id));
        permissionService.evict(id);
        log.info("删除角色成功：id={}，roleCode={}", id, role.getRoleCode());
    }

    @Override
    public List<PermissionTreeVO> permissionTree(Long roleId) {
        List<SysPermission> all = sysPermissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getStatus, 1)
                .orderByAsc(SysPermission::getSort)
                .orderByAsc(SysPermission::getId));
        Set<Long> granted = roleId == null
                ? Collections.emptySet()
                : sysRolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toSet());

        // 先全量建节点，再按 parentId 挂接，避免依赖数据库返回顺序
        Map<Long, PermissionTreeVO> nodeMap = new LinkedHashMap<>();
        for (SysPermission perm : all) {
            PermissionTreeVO node = new PermissionTreeVO();
            node.setId(perm.getId());
            node.setParentId(perm.getParentId());
            node.setPermName(perm.getPermName());
            node.setPermCode(perm.getPermCode());
            node.setPermType(perm.getPermType());
            node.setPath(perm.getPath());
            node.setIcon(perm.getIcon());
            node.setSort(perm.getSort());
            node.setChecked(granted.contains(perm.getId()));
            nodeMap.put(perm.getId(), node);
        }

        List<PermissionTreeVO> roots = new ArrayList<>();
        for (PermissionTreeVO node : nodeMap.values()) {
            Long parentId = node.getParentId();
            PermissionTreeVO parent = parentId == null ? null : nodeMap.get(parentId);
            if (parent == null) {
                // 父节点为空或已被禁用：提升为顶级，保证节点不会凭空消失
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(RolePermissionDTO dto) {
        SysRole role = requireRole(dto.getRoleId());
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BusinessException("超级管理员默认拥有全部权限，无需分配");
        }

        List<Long> permissionIds = dto.getPermissionIds() == null
                ? Collections.emptyList()
                : dto.getPermissionIds().stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());

        // 覆盖式更新：先清空再写入，语义明确且不会残留旧权限
        sysRolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, dto.getRoleId()));
        if (!CollectionUtils.isEmpty(permissionIds)) {
            // 校验权限ID真实存在，防止前端传入非法ID污染关联表
            List<Long> validIds = sysPermissionMapper.selectBatchIds(permissionIds).stream()
                    .map(SysPermission::getId)
                    .collect(Collectors.toList());
            if (validIds.size() != permissionIds.size()) {
                throw new BusinessException("提交的权限中包含不存在的权限项，请刷新页面后重试");
            }
            for (Long permissionId : validIds) {
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(dto.getRoleId());
                relation.setPermissionId(permissionId);
                sysRolePermissionMapper.insert(relation);
            }
        }

        // 必须清缓存：否则新权限要等 TTL 过期才生效，用户会以为没保存成功
        permissionService.evict(dto.getRoleId());
        log.info("角色权限分配完成：roleId={}，权限数={}", dto.getRoleId(), permissionIds.size());
    }

    /* ==================== 私有方法 ==================== */

    private SysRole requireRole(Long id) {
        SysRole role = id == null ? null : sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private void assertRoleCodeUnique(String roleCode, Long excludeId) {
        if (!StringUtils.hasText(roleCode)) {
            return;
        }
        Long count = sysRoleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在：" + roleCode);
        }
    }

    private SysRoleVO toVO(SysRole role) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(role.getId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setCreateTime(role.getCreateTime());
        vo.setBuiltin(SUPER_ADMIN_CODE.equals(role.getRoleCode()));
        return vo;
    }

    /** 按 roleId 分组计数，替代逐条 count 查询 */
    private Map<Long, Long> countByGroup(List<? extends Object> rows) {
        Map<Long, Long> map = new LinkedHashMap<>();
        for (Object row : rows) {
            Long roleId = null;
            if (row instanceof SysUser user) {
                roleId = user.getRoleId();
            } else if (row instanceof SysRolePermission relation) {
                roleId = relation.getRoleId();
            }
            if (roleId != null) {
                map.merge(roleId, 1L, Long::sum);
            }
        }
        return map;
    }
}
