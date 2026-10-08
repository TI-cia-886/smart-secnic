package com.ikun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ikun.common.PageResult;
import com.ikun.dto.SysUserDTO;
import com.ikun.entity.SysUser;
import com.ikun.vo.SysUserVO;

/**
 * 后台账号服务
 *
 * @author smart-scenic
 */
public interface SysUserService extends IService<SysUser> {

    /** 分页查询账号列表 */
    PageResult<SysUserVO> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                    Long roleId, Long scenicId, Integer status);

    /** 查询账号详情 */
    SysUserVO getDetail(Long id);

    /** 新增账号 */
    void saveUser(SysUserDTO dto);

    /** 编辑账号 */
    void updateUser(SysUserDTO dto);

    /** 删除账号 */
    void removeUser(Long id);

    /** 启用/禁用账号 */
    void changeStatus(Long id, Integer status);

    /** 重置密码为手机号后六位 */
    void resetPassword(Long id);
}
