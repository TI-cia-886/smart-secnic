package com.ikun.service;

import com.ikun.dto.LoginDTO;
import com.ikun.dto.RegisterDTO;
import com.ikun.vo.LoginVO;
import com.ikun.vo.UserInfoVO;

/**
 * 认证服务
 *
 * @author smart-scenic
 */
public interface AuthService {

    /** 后台登录 */
    LoginVO login(LoginDTO loginDTO);

    /** 新用户注册（初始密码为手机号后六位） */
    void register(RegisterDTO registerDTO);

    /** 获取当前登录用户信息（含角色与权限） */
    UserInfoVO getUserInfo(Long userId);

    /** 退出登录 */
    void logout();

    /** 修改当前登录用户密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
