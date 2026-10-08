package com.ikun.service;

import com.ikun.dto.AppLoginDTO;
import com.ikun.dto.AppRegisterDTO;
import com.ikun.vo.AppLoginVO;

/**
 * 游客小程序认证服务
 *
 * @author smart-scenic
 */
public interface AppAuthService {

    /** 游客登录，成功返回游客专属 Token（type=TOURIST，无法用于后台接口） */
    AppLoginVO login(AppLoginDTO dto);

    /** 游客注册 */
    void register(AppRegisterDTO dto);

    /** 退出登录：把当前 Token 写入黑名单 */
    void logout();
}
