package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.entity.Announcement;
import com.ikun.mapper.AnnouncementMapper;
import com.ikun.service.AnnouncementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告资讯服务实现
 *
 * <p>状态流转只有两条合法路径，其余组合一律拒绝：
 * {@code DRAFT/OFFLINE -> PUBLISHED -> OFFLINE}。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement>
        implements AnnouncementService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";

    @Override
    public PageResult<Announcement> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                              Long scenicId, String type, String status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<Announcement>lambdaQuery()
                // 全局公告（scenic_id 为 NULL）对所有景区都可见，因此不能简单 eq
                .and(scopeScenicId != null, w -> w
                        .eq(Announcement::getScenicId, scopeScenicId).or()
                        .isNull(Announcement::getScenicId))
                .like(StringUtils.hasText(keyword), Announcement::getTitle, keyword)
                .eq(StringUtils.hasText(type), Announcement::getType, type)
                .eq(StringUtils.hasText(status), Announcement::getStatus, status)
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getCreateTime);
        return PageResult.of(this.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public Announcement getDetail(Long id) {
        Announcement announcement = getById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        return announcement;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAnnouncement(Announcement announcement) {
        announcement.setId(null);
        announcement.setCreateTime(null);
        announcement.setUpdateTime(null);
        announcement.setDeleted(null);
        // 发布人、发布时间由服务端决定，前端传什么都不认
        announcement.setPublisherId(null);
        announcement.setPublishTime(null);

        announcement.setScenicId(ScenicScope.fillWritable(announcement.getScenicId()));
        if (announcement.getStatus() == null) {
            announcement.setStatus(STATUS_DRAFT);
        }
        if (announcement.getIsTop() == null) {
            announcement.setIsTop(0);
        }
        if (announcement.getAiGenerated() == null) {
            announcement.setAiGenerated(0);
        }
        save(announcement);
        log.info("新增公告成功：id={}，title={}", announcement.getId(), announcement.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Announcement announcement) {
        if (announcement.getId() == null) {
            throw new BusinessException("公告ID不能为空");
        }
        Announcement exist = getDetail(announcement.getId());
        checkWritable(exist);
        // 已发布的公告不允许通过「编辑」直接改回草稿，状态必须走 publish/offline 接口，
        // 否则前台可能出现「已发布但无发布时间」的脏数据
        announcement.setStatus(null);
        announcement.setScenicId(null);
        announcement.setPublisherId(null);
        announcement.setPublishTime(null);
        announcement.setCreateTime(null);
        announcement.setUpdateTime(null);
        announcement.setDeleted(null);
        updateById(announcement);
        log.info("编辑公告成功：id={}", announcement.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAnnouncement(Long id) {
        Announcement exist = getDetail(id);
        checkWritable(exist);
        removeById(id);
        log.info("删除公告成功：id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Announcement exist = getDetail(id);
        checkWritable(exist);
        if (STATUS_PUBLISHED.equals(exist.getStatus())) {
            throw new BusinessException("该公告已是发布状态");
        }
        if (!StringUtils.hasText(exist.getTitle()) || !StringUtils.hasText(exist.getContent())) {
            throw new BusinessException("公告标题与正文不能为空，无法发布");
        }
        Announcement update = new Announcement();
        update.setId(id);
        update.setStatus(STATUS_PUBLISHED);
        update.setPublishTime(LocalDateTime.now());
        update.setPublisherId(UserContext.getUserId());
        updateById(update);
        log.info("公告已发布：id={}，publisherId={}", id, UserContext.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        Announcement exist = getDetail(id);
        checkWritable(exist);
        if (!STATUS_PUBLISHED.equals(exist.getStatus())) {
            throw new BusinessException("只有已发布的公告才能下线");
        }
        Announcement update = new Announcement();
        update.setId(id);
        update.setStatus(STATUS_OFFLINE);
        updateById(update);
        log.info("公告已下线：id={}", id);
    }

    @Override
    public List<Announcement> listPublished(Long scenicId, Integer limit) {
        int size = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        var wrapper = Wrappers.<Announcement>lambdaQuery()
                .eq(Announcement::getStatus, STATUS_PUBLISHED)
                .and(scenicId != null, w -> w
                        .eq(Announcement::getScenicId, scenicId).or()
                        .isNull(Announcement::getScenicId))
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT " + size);
        return this.list(wrapper);
    }

    /** 全局公告（scenicId 为空）由超管维护，受限账号不得改动 */
    private void checkWritable(Announcement announcement) {
        ScenicScope.checkWritable(announcement.getScenicId());
    }
}
