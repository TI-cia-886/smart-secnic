package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.PageResult;
import com.ikun.entity.ScenicArea;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.service.ScenicAreaService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 景区服务实现
 *
 * @author smart-scenic
 */
@Service
public class ScenicAreaServiceImpl extends ServiceImpl<ScenicAreaMapper, ScenicArea> implements ScenicAreaService {

    @Override
    public PageResult<ScenicArea> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                            String level, String status) {
        LambdaQueryWrapper<ScenicArea> wrapper = Wrappers.<ScenicArea>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(ScenicArea::getScenicName, keyword).or()
                        .like(ScenicArea::getScenicCode, keyword))
                .eq(StringUtils.hasText(level), ScenicArea::getLevel, level)
                .eq(StringUtils.hasText(status), ScenicArea::getStatus, status)
                .orderByAsc(ScenicArea::getScenicCode);
        Page<ScenicArea> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page);
    }

    @Override
    public List<ScenicArea> listOptions() {
        return this.list(Wrappers.<ScenicArea>lambdaQuery()
                .eq(ScenicArea::getStatus, "ENABLE")
                .orderByAsc(ScenicArea::getScenicCode));
    }
}
