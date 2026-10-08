package com.ikun.controller;

import com.ikun.common.Result;
import com.ikun.dto.AppTouristUpdateDTO;
import com.ikun.service.AppTouristService;
import com.ikun.vo.AppProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游客小程序个人中心接口（对应小程序：我的）
 *
 * @author smart-scenic
 */
@Tag(name = "小程序-04-个人中心", description = "个人资料、订单与工单统计、资料修改")
@RestController
@RequestMapping("/app/tourist")
@RequiredArgsConstructor
public class AppTouristController {

    private final AppTouristService appTouristService;

    @Operation(summary = "个人中心数据", description = "基本资料 + 订单/工单统计")
    @GetMapping("/profile")
    public Result<AppProfileVO> profile() {
        return Result.success(appTouristService.profile());
    }

    @Operation(summary = "修改个人资料")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody AppTouristUpdateDTO dto) {
        appTouristService.updateProfile(dto);
        return Result.success("保存成功", null);
    }
}
