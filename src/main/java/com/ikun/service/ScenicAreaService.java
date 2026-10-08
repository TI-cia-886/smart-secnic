package com.ikun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ikun.common.PageResult;
import com.ikun.entity.ScenicArea;

import java.util.List;

/**
 * 景区服务
 *
 * @author smart-scenic
 */
public interface ScenicAreaService extends IService<ScenicArea> {

    /** 分页查询景区 */
    PageResult<ScenicArea> pageQuery(Integer pageNum, Integer pageSize, String keyword, String level, String status);

    /** 景区下拉选项（仅启用状态） */
    List<ScenicArea> listOptions();
}
