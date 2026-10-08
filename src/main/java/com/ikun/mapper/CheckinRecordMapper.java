package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检票核验记录 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {
}
