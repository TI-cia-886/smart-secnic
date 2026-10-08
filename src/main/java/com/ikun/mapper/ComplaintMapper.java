package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.Complaint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投诉工单 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {
}
