package com.ikun.controller;

import com.ikun.common.Result;
import com.ikun.dto.AppLoginDTO;
import com.ikun.dto.AppRegisterDTO;
import com.ikun.service.AppAuthService;
import com.ikun.vo.AppLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游客小程序认证接口
 *
 * <p>登录/注册不需要 Token；退出登录需要，否则无法得知要拉黑哪个 Token。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "小程序-01-认证", description = "游客注册、登录、退出登录")
@RestController
@RequestMapping("/app/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;

    @Operation(summary = "游客登录")
    @PostMapping("/login")
    public Result<AppLoginVO> login(@Valid @RequestBody AppLoginDTO dto) {
        return Result.success("登录成功", appAuthService.login(dto));
    }

    @Operation(summary = "游客注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AppRegisterDTO dto) {
        appAuthService.register(dto);
        return Result.success("注册成功", null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        appAuthService.logout();
        return Result.success("已退出登录", null);
    }
}
