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
