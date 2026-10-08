package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台用户 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
