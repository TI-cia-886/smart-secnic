package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.entity.Announcement;

import java.util.List;

/**
 * 公告资讯服务
 *
 * @author smart-scenic
 */
public interface AnnouncementService {

    PageResult<Announcement> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                       Long scenicId, String type, String status);

    Announcement getDetail(Long id);

    void saveAnnouncement(Announcement announcement);

    void updateAnnouncement(Announcement announcement);

    void removeAnnouncement(Long id);

    /** 发布：草稿/已下线 → 已发布，同时记录发布时间与发布人 */
    void publish(Long id);

    /** 下线：已发布 → 已下线 */
    void offline(Long id);

    /** 已发布公告列表，供游客小程序展示 */
    List<Announcement> listPublished(Long scenicId, Integer limit);
}
