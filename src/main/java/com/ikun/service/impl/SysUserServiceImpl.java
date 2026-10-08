package com.ikun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ResultCode;
import com.ikun.common.UserContext;
import com.ikun.dto.SysUserDTO;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.SysRole;
import com.ikun.entity.SysUser;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.SysRoleMapper;
import com.ikun.mapper.SysUserMapper;
import com.ikun.service.SysUserService;
import com.ikun.util.PasswordUtil;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台账号服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleMapper sysRoleMapper;
    private final ScenicAreaMapper scenicAreaMapper;

    @Override
    public PageResult<SysUserVO> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                           Long roleId, Long scenicId, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(roleId != null, SysUser::getRoleId, roleId)
                .eq(scenicId != null, SysUser::getScenicId, scenicId)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);

        if (StringUtils.hasText(keyword)) {
            // 手机号在库中是 AES 密文，无法参与 LIKE 模糊匹配；
            // 若关键字恰好是完整手机号，则改用 SHA-256 摘要做精确匹配
            String phoneHash = SensitiveUtil.isValidPhone(keyword) ? SensitiveUtil.phoneHash(keyword) : null;
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or(phoneHash != null, x -> x.eq(SysUser::getPhoneHash, phoneHash)));
        }

        Page<SysUser> page = this.page(new Page<>(pageNum, pageSize), wrapper);

        // 批量查询角色与景区名称，避免 N+1
        Map<Long, String> roleNameMap = sysRoleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName, (a, b) -> a));
        Map<Long, String> scenicNameMap = scenicAreaMapper.selectList(null).stream()
                .collect(Collectors.toMap(ScenicArea::getId, ScenicArea::getScenicName, (a, b) -> a));

        List<SysUserVO> records = page.getRecords().stream().map(user -> {
            // 实体读取时手机号已由 TypeHandler 自动解密，这里拿到的是明文
            SysUserVO vo = BeanUtil.copyProperties(user, SysUserVO.class);
            vo.setRoleName(roleNameMap.get(user.getRoleId()));
            vo.setScenicName(user.getScenicId() == null ? "全部景区" : scenicNameMap.get(user.getScenicId()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SysUserVO getDetail(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        SysUserVO vo = BeanUtil.copyProperties(user, SysUserVO.class);
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        vo.setRoleName(role == null ? null : role.getRoleName());
        if (user.getScenicId() != null) {
            ScenicArea scenic = scenicAreaMapper.selectById(user.getScenicId());
            vo.setScenicName(scenic == null ? null : scenic.getScenicName());
        }
        return vo;
    }

    @Override
    public void saveUser(SysUserDTO dto) {
        checkUsernameUnique(dto.getUsername(), null);
        checkPhoneUnique(dto.getPhone(), null);

        SysUser user = BeanUtil.copyProperties(dto, SysUser.class);

        // 默认密码 = 手机号后六位；管理员显式填写密码时以填写值为准
        String rawPassword = StringUtils.hasText(dto.getPassword())
                ? dto.getPassword()
                : SensitiveUtil.defaultPassword(dto.getPhone());
        user.setPassword(PasswordUtil.encrypt(rawPassword));

        // 手机号由 TypeHandler 加密落库，同时回填不可逆摘要列，供精确查询使用
        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone().trim() : null;
        user.setPhone(phone);
        user.setPhoneHash(phone == null ? null : SensitiveUtil.phoneHash(phone));

        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        this.save(user);
        log.info("新增后台账号：username={}，phone={}，初始密码{}", user.getUsername(),
                SensitiveUtil.maskPhone(phone),
                StringUtils.hasText(dto.getPassword()) ? "由管理员指定" : "为手机号后六位");
    }

    @Override
    public void updateUser(SysUserDTO dto) {
        SysUser exists = this.getById(dto.getId());
        if (exists == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        checkUsernameUnique(dto.getUsername(), dto.getId());
        checkPhoneUnique(dto.getPhone(), dto.getId());

        SysUser user = BeanUtil.copyProperties(dto, SysUser.class);

        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone().trim() : null;
        user.setPhone(phone);
        user.setPhoneHash(phone == null ? null : SensitiveUtil.phoneHash(phone));

        // 编辑时密码留空表示不修改
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        } else {
            user.setPassword(null);
        }
        this.updateById(user);
    }

    @Override
    public void removeUser(Long id) {
        if (Objects.equals(id, 1L)) {
            throw new BusinessException("内置超级管理员账号不允许删除");
        }
        if (Objects.equals(id, UserContext.getUserId())) {
            throw new BusinessException("不允许删除当前登录账号");
        }
        this.removeById(id);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (Objects.equals(id, UserContext.getUserId())) {
            throw new BusinessException("不允许修改当前登录账号状态");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        this.updateById(user);
    }

    @Override
    public void resetPassword(Long id) {
        SysUser exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        // 重置为手机号后六位，与新增账号的默认密码规则保持一致
        // （getById 读取时手机号已自动解密，这里拿到的是明文）
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(PasswordUtil.encrypt(SensitiveUtil.defaultPassword(exists.getPhone())));
        this.updateById(user);
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        Long count = this.baseMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("登录账号已存在");
        }
    }

    /** 手机号唯一性校验：密文无法比对，使用 SHA-256 摘要列 */
    private void checkPhoneUnique(String phone, Long excludeId) {
        String phoneHash = SensitiveUtil.phoneHash(phone);
        if (phoneHash == null) {
            return;
        }
        Long count = this.baseMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getPhoneHash, phoneHash)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("该手机号已被其他账号使用");
        }
    }
}
