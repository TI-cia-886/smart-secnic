package com.ikun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ikun.common.PageResult;
import com.ikun.dto.SysUserDTO;
import com.ikun.entity.SysUser;
import com.ikun.vo.SysUserVO;
import com.ikun.vo.UserOptionVO;

import java.util.List;

/**
 * 后台账号服务
 *
 * @author smart-scenic
 */
public interface SysUserService extends IService<SysUser> {

    /** 分页查询账号列表 */
    PageResult<SysUserVO> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                    Long roleId, Long scenicId, Integer status);

    /**
     * 处理人下拉选项（仅启用账号，按景区隔离，只暴露最小字段）
     *
     * <p>供工单转派等业务场景使用：只要有对应业务权限（如 complaint:list）
     * 即可调用，不需要完整的 system:user:list 账号管理权限。</p>
     */
    List<UserOptionVO> handlerOptions();

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
