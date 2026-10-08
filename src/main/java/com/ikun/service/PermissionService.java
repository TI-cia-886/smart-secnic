package com.ikun.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.entity.SysPermission;
import com.ikun.entity.SysRole;
import com.ikun.entity.SysRolePermission;
import com.ikun.mapper.SysPermissionMapper;
import com.ikun.mapper.SysRoleMapper;
import com.ikun.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验服务（带 Redis 缓存）
 *
 * <p>鉴权拦截器在每个请求上都要判断权限，若每次都查两张表会产生大量无谓查询，
 * 因此把「角色 → 权限标识集合」缓存进 Redis。角色权限调整后由
 * {@link #evict(Long)} 主动失效，避免缓存与库不一致。</p>
 *
 * <p>缓存不可用时不阻断业务：直接回落到数据库查询，只是慢一点，
 * 而不是让所有人都被拦在门外。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 权限缓存 key 前缀 */
    private static final String PERM_KEY = "perm:role:";

    /** 超级管理员角色编码：拥有全部权限，不受权限表配置影响 */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    /** 权限缓存有效期：30 分钟，兜底防止角色权限变更后缓存长期不一致 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 判断角色是否持有指定权限
     *
     * @param roleId   角色ID
     * @param permCode 权限标识
     */
    public boolean hasPermission(Long roleId, String permCode) {
        if (roleId == null) {
            return false;
        }
        // 超级管理员短路放行：权限表后续新增权限时不必再逐条补授权，
        // 否则超管反而会因为「没授权」被自己的系统拦掉
        if (isSuperAdmin(roleId)) {
            return true;
        }
        if (!StringUtils.hasText(permCode)) {
            return false;
        }
        return getPermissions(roleId).contains(permCode);
    }

    /** 判断角色是否持有任意一个权限 */
    public boolean hasAnyPermission(Long roleId, String... permCodes) {
        if (roleId == null) {
            return false;
        }
        if (isSuperAdmin(roleId)) {
            return true;
        }
        Set<String> owned = getPermissions(roleId);
        return Arrays.stream(permCodes).anyMatch(owned::contains);
    }

    /**
     * 查询角色持有的全部权限标识
     *
     * <p>注意返回的是不可变集合，调用方不应修改。</p>
     */
    public Set<String> getPermissions(Long roleId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(PERM_KEY + roleId);
            if (cached != null) {
                return parse(cached);
            }
        } catch (Exception e) {
            log.warn("权限缓存读取失败，回落数据库：{}", e.getMessage());
            return loadFromDb(roleId);
        }

        Set<String> permissions = loadFromDb(roleId);
        try {
            stringRedisTemplate.opsForValue()
                    .set(PERM_KEY + roleId, String.join(",", permissions), CACHE_TTL);
        } catch (Exception e) {
            log.warn("权限缓存写入失败（不影响本次校验）：{}", e.getMessage());
        }
        return permissions;
    }

    /** 清除某角色的权限缓存：角色权限调整后必须调用 */
    public void evict(Long roleId) {
        if (roleId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(PERM_KEY + roleId);
        } catch (Exception e) {
            log.warn("权限缓存清除失败，将在 {} 分钟后自动过期：{}", CACHE_TTL.toMinutes(), e.getMessage());
        }
    }

    /** 判断是否为超级管理员（结果同样缓存，避免每请求查角色表） */
    public boolean isSuperAdmin(Long roleId) {
        if (roleId == null) {
            return false;
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        return role != null && SUPER_ADMIN_CODE.equals(role.getRoleCode());
    }

    /** 从数据库加载角色权限标识集合 */
    private Set<String> loadFromDb(Long roleId) {
        List<Long> permIds = sysRolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
        if (permIds.isEmpty()) {
            return Collections.emptySet();
        }
        return sysPermissionMapper.selectBatchIds(permIds).stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == 1)
                .filter(p -> StringUtils.hasText(p.getPermCode()))
                .map(SysPermission::getPermCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 缓存字符串反序列化为集合（LinkedHashSet 保持顺序，便于调试） */
    private Set<String> parse(String cached) {
        if (!StringUtils.hasText(cached)) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String item : cached.split(",")) {
            if (StringUtils.hasText(item)) {
                set.add(item.trim());
            }
        }
        return set;
    }
}
