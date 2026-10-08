package com.ikun.controller;

import com.ikun.common.Result;
import com.ikun.dto.AiChatDTO;
import com.ikun.entity.Announcement;
import com.ikun.entity.ParkingLot;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.ScenicSpot;
import com.ikun.entity.TicketType;
import com.ikun.service.AppScenicService;
import com.ikun.vo.AiChatReplyVO;
import com.ikun.vo.AppHomeVO;
import com.ikun.vo.AppRouteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游客小程序浏览接口（对应小程序：首页、景区详情、景点、购票、公告、停车场、地图、路线、智能问答）
 *
 * <p>这些接口未登录也能访问；已登录时通过可选登录拦截器补齐游客身份（例如首页展示待支付订单数）。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "小程序-02-景区浏览", description = "首页聚合、景区/景点/票种/公告/停车场查询、地图导览、路线推荐、智能问答")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppScenicController {

    private final AppScenicService appScenicService;

    @Operation(summary = "首页聚合数据", description = "未传景区时按游客所在地或默认景区返回")
    @GetMapping("/home")
    public Result<AppHomeVO> home(@RequestParam(required = false) Long scenicId) {
        return Result.success(appScenicService.home(scenicId));
    }

    @Operation(summary = "景区列表", description = "支持按名称、等级、城市筛选")
    @GetMapping("/scenic/list")
    public Result<List<ScenicArea>> scenicList(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(required = false) String city) {
        return Result.success(appScenicService.listScenic(keyword, level, city));
    }

    @Operation(summary = "景区详情")
    @GetMapping("/scenic/{id}")
    public Result<ScenicArea> scenicDetail(@PathVariable Long id) {
        return Result.success(appScenicService.scenicDetail(id));
    }

    @Operation(summary = "景区景点列表")
    @GetMapping("/scenic/{id}/spots")
    public Result<List<ScenicSpot>> spots(@PathVariable Long id,
                                          @RequestParam(required = false) String spotType,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(appScenicService.listSpots(id, spotType, keyword));
    }

    @Operation(summary = "景点详情")
    @GetMapping("/spot/{id}")
    public Result<ScenicSpot> spotDetail(@PathVariable Long id) {
        return Result.success(appScenicService.spotDetail(id));
    }

    @Operation(summary = "景区在售票种")
    @GetMapping("/scenic/{id}/tickets")
    public Result<List<TicketType>> tickets(@PathVariable Long id) {
        return Result.success(appScenicService.listTickets(id));
    }

    @Operation(summary = "票种详情")
    @GetMapping("/ticket/{id}")
    public Result<TicketType> ticketDetail(@PathVariable Long id) {
        return Result.success(appScenicService.ticketDetail(id));
    }

    @Operation(summary = "景区公告列表", description = "含全平台公告，默认 10 条")
    @GetMapping("/scenic/{id}/announcements")
    public Result<List<Announcement>> announcements(@PathVariable Long id,
                                                    @RequestParam(required = false) Integer limit) {
        return Result.success(appScenicService.listAnnouncements(id, limit));
    }

    @Operation(summary = "景区停车场")
    @GetMapping("/scenic/{id}/parking")
    public Result<List<ParkingLot>> parking(@PathVariable Long id) {
        return Result.success(appScenicService.listParking(id));
    }

    @Operation(summary = "地图导览数据", description = "返回带经纬度的景点，用于地图打点")
    @GetMapping("/scenic/{id}/map")
    public Result<List<ScenicSpot>> map(@PathVariable Long id) {
        return Result.success(appScenicService.mapSpots(id));
    }

    @Operation(summary = "推荐游览路线", description = "type：CLASSIC 经典全览 / FAMILY 亲子轻松 / QUICK 半日精华")
    @GetMapping("/scenic/{id}/route")
    public Result<AppRouteVO> route(@PathVariable Long id,
                                    @RequestParam(required = false) String type) {
        return Result.success(appScenicService.recommendRoute(id, type));
    }

    @Operation(summary = "智能问答", description = "优先命中知识库，未命中由 AI 生成兜底")
    @PostMapping("/ai/chat")
    public Result<AiChatReplyVO> chat(@Valid @RequestBody AiChatDTO dto) {
        return Result.success(appScenicService.chat(dto));
    }
}
