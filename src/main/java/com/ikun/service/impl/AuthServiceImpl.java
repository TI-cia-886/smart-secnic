package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.common.BusinessException;
import com.ikun.common.ResultCode;
import com.ikun.common.UserContext;
import com.ikun.dto.LoginDTO;
import com.ikun.dto.RegisterDTO;
import com.ikun.entity.*;
import com.ikun.mapper.*;
import com.ikun.service.AuthService;
import com.ikun.service.LoginLogService;
import com.ikun.service.TokenBlacklistService;
import com.ikun.util.JwtUtil;
import com.ikun.util.PasswordUtil;
import com.ikun.util.RequestUtil;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.LoginVO;
import com.ikun.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginLogService loginLogService;

    /** 新注册用户的默认角色编码（服务端指定，避免注册接口被用来创建高权限账号） */
    @Value("${security.register.default-role-code:SERVICE}")
    private String registerDefaultRoleCode;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 来源客户端类型，仅用于登录日志归类：PC 后台 / 管理端移动版
        String loginType = resolveLoginType();

        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, loginDTO.getUsername()));
        if (user == null) {
            // 账号不存在也要落日志：这是撞库攻击最典型的特征，只记成功等于丢掉了线索
            loginLogService.record(null, loginDTO.getUsername(), loginType, false, "账号不存在");
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            loginLogService.record(user.getId(), user.getUsername(), loginType, false, "账号已被禁用");
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        // 密码校验：BCrypt 单向哈希比对（内部兼容历史 MD5 数据）
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            loginLogService.record(user.getId(), user.getUsername(), loginType, false, "密码错误");
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }
        // 历史数据平滑升级：MD5 不可逆推，只能在登录成功、明文可用的此刻重算为 BCrypt
        if (PasswordUtil.isLegacyMd5(user.getPassword())) {
            upgradePassword(user.getId(), loginDTO.getPassword());
        }

        // 更新最近登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);

        // 生成 Token（内含 jti，供退出登录时加入黑名单）
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRoleId(), user.getScenicId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setAvatar(user.getAvatar());
        vo.setRoleId(user.getRoleId());
        vo.setScenicId(user.getScenicId());

        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role != null) {
            vo.setRoleName(role.getRoleName());
        }
        if (user.getScenicId() != null) {
            ScenicArea scenic = scenicAreaMapper.selectById(user.getScenicId());
            vo.setScenicName(scenic == null ? null : scenic.getScenicName());
        }
        loginLogService.record(user.getId(), user.getUsername(), loginType, true, "登录成功");
        return vo;
    }

    /**
     * 解析请求来源类型
     *
     * <p>PC 后台与管理端移动版共用同一登录接口，靠请求头 {@code X-Client-Type} 区分，
     * 便于登录日志区分终端来源。取值非法时按 PC 处理，避免前端漏传导致日志写失败。</p>
     */
    private String resolveLoginType() {
        HttpServletRequest request = RequestUtil.currentRequest();
        if (request == null) {
            return "PC";
        }
        String type = request.getHeader("X-Client-Type");
        if (!StringUtils.hasText(type)) {
            return "PC";
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "MANAGER", "PC" -> upper;
            default -> "PC";
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        // 1. 账号唯一性校验
        Long sameUsername = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, registerDTO.getUsername()));
        if (sameUsername != null && sameUsername > 0) {
            throw new BusinessException("登录账号已存在");
        }

        // 2. 手机号唯一性校验：手机号是 AES 密文，无法直接比对，改用 SHA-256 摘要列
        String phoneHash = SensitiveUtil.phoneHash(registerDTO.getPhone());
        Long samePhone = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getPhoneHash, phoneHash));
        if (samePhone != null && samePhone > 0) {
            throw new BusinessException("该手机号已被注册");
        }

        // 3. 取默认角色（注册接口不接收角色参数，防止越权注册）
        SysRole role = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, registerDefaultRoleCode)
                .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException("系统默认注册角色未配置，请联系管理员");
        }

        // 4. 组装账号：默认密码 = 手机号后六位
        SysUser user = new SysUser();
        user.setUsername(registerDTO.getUsername());
        user.setRealName(registerDTO.getRealName());
        // 手机号写入时由 TypeHandler 自动 AES-256-GCM 加密，数据库不落明文
        user.setPhone(registerDTO.getPhone());
        user.setPhoneHash(phoneHash);
        user.setPassword(PasswordUtil.encrypt(SensitiveUtil.defaultPassword(registerDTO.getPhone())));
        user.setRoleId(role.getId());
        user.setScenicId(registerDTO.getScenicId());
        user.setStatus(1);
        sysUserMapper.insert(user);

        log.info("新用户注册成功：username={}，phone={}，初始密码为手机号后六位",
                user.getUsername(), SensitiveUtil.maskPhone(registerDTO.getPhone()));
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setAvatar(user.getAvatar());
        // 手机号读取时已由 TypeHandler 自动解密
        vo.setPhone(user.getPhone());
        vo.setRoleId(user.getRoleId());
        vo.setScenicId(user.getScenicId());

        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role != null) {
            vo.setRoleName(role.getRoleName());
            vo.setRoleCode(role.getRoleCode());
        }
        if (user.getScenicId() != null) {
            ScenicArea scenic = scenicAreaMapper.selectById(user.getScenicId());
            vo.setScenicName(scenic == null ? null : scenic.getScenicName());
        }

        // 查询角色对应的权限标识
        vo.setPermissions(queryPermissions(user.getRoleId()));
        return vo;
    }

    @Override
    public void logout() {
        String token = UserContext.getToken();
        if (!StringUtils.hasText(token)) {
            return;
        }
        // 把当前 Token 的 jti 写入 Redis 黑名单，TTL 与 Token 剩余有效期一致，
        // 之后携带该 Token 的请求会被 JwtInterceptor 直接拒绝，实现"登出即失效"
        tokenBlacklistService.blacklist(jwtUtil.getJti(token), jwtUtil.getExpiration(token));
        log.info("用户 {} 退出登录，Token 已失效", UserContext.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        // 校验原密码
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_ERROR, "原密码错误");
        }
        // 新密码不能与原密码相同
        if (PasswordUtil.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(PasswordUtil.encrypt(newPassword));
        // 同时刷新 pwd_update_time，便于后续基于该字段做"密码 90 天未换"的安全提醒
        update.setPwdUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
        log.info("用户 {} 修改密码成功", user.getUsername());
    }

    /** 把历史 MD5 密码就地升级为 BCrypt（仅在登录成功、明文可用时执行） */
    private void upgradePassword(Long userId, String rawPassword) {
        SysUser upgrade = new SysUser();
        upgrade.setId(userId);
        upgrade.setPassword(PasswordUtil.encrypt(rawPassword));
        sysUserMapper.updateById(upgrade);
        log.info("账号 id={} 的密码已由 MD5 自动升级为 BCrypt", userId);
    }

    /** 查询角色权限标识集合 */
    private List<String> queryPermissions(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        List<Long> permIds = sysRolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
        if (permIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysPermissionMapper.selectBatchIds(permIds).stream()
                .filter(p -> StringUtils.hasText(p.getPermCode()))
                .map(SysPermission::getPermCode)
                .distinct()
                .collect(Collectors.toList());
    }
}
