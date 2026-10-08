package com.ikun.controller;

import com.ikun.common.Result;
import com.ikun.common.UserContext;
import com.ikun.dto.ChangePasswordDTO;
import com.ikun.dto.LoginDTO;
import com.ikun.dto.RegisterDTO;
import com.ikun.service.AuthService;
import com.ikun.vo.LoginVO;
import com.ikun.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（PC后台 / 管理端移动版通用）
 *
 * @author smart-scenic
 */
@Tag(name = "01-认证管理", description = "登录、退出、获取当前用户信息")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success("登录成功", authService.login(loginDTO));
    }

    @Operation(summary = "新用户注册", description = "初始密码为手机号后六位，默认角色由服务端配置指定")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        authService.register(registerDTO);
        return Result.success("注册成功，初始密码为手机号后六位", null);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.success(authService.getUserInfo(UserContext.getUserId()));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success("退出成功", null);
    }

    @Operation(summary = "修改当前登录用户密码")
    @PutMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(UserContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码修改成功", null);
    }
}
