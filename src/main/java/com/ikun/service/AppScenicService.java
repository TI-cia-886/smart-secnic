package com.ikun.service;

import com.ikun.dto.AiChatDTO;
import com.ikun.entity.Announcement;
import com.ikun.entity.ParkingLot;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.ScenicSpot;
import com.ikun.entity.TicketType;
import com.ikun.vo.AiChatReplyVO;
import com.ikun.vo.AppHomeVO;
import com.ikun.vo.AppRouteVO;
import com.ikun.vo.TicketAvailabilityVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 游客小程序浏览服务
 *
 * @author smart-scenic
 */
public interface AppScenicService {

    /** 首页聚合数据：景区信息 + 公告 + 客流提示 + 热门景点 + 停车场 */
    AppHomeVO home(Long scenicId);

    /** 景区列表，支持按名称、等级、城市筛选 */
    List<ScenicArea> listScenic(String keyword, String level, String city);

    ScenicArea scenicDetail(Long scenicId);

    List<ScenicSpot> listSpots(Long scenicId, String spotType, String keyword);

    ScenicSpot spotDetail(Long spotId);

    /** 景区在售票种 */
    List<TicketType> listTickets(Long scenicId);

    TicketType ticketDetail(Long ticketTypeId);

    /**
     * 票种在某一天的可预约情况（各时段余票），供购票页实时展示。
     *
     * @param ticketTypeId 票种ID
     * @param playDate     游玩日期，为空时默认今天
     */
    TicketAvailabilityVO ticketAvailability(Long ticketTypeId, LocalDate playDate);

    List<Announcement> listAnnouncements(Long scenicId, Integer limit);

    List<ParkingLot> listParking(Long scenicId);

    /** 地图导览数据：包含经纬度的景点与停车场 */
    List<ScenicSpot> mapSpots(Long scenicId);

    /** 智能问答 */
    AiChatReplyVO chat(AiChatDTO dto);

    /**
     * 推荐游览路线
     *
     * @param type CLASSIC 经典全览 / FAMILY 亲子轻松 / QUICK 半日精华
     */
    AppRouteVO recommendRoute(Long scenicId, String type);
}
