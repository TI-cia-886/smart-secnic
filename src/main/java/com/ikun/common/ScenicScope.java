package com.ikun.common;

/**
 * 景区数据隔离工具
 *
 * <p>需求里「景区管理员只能管自己的景区、超级管理员可管全部」这条规则，
 * 必须在每个查询与写入上真正落到 SQL 条件里，否则只要手动改一下
 * {@code scenicId} 参数就能读到别的景区数据。</p>
 *
 * <p>两条使用原则：</p>
 * <ul>
 *   <li>查询：用 {@link #resolve(Long)} 得到最终过滤值，传 null 表示不限制；</li>
 *   <li>写入：用 {@link #checkWritable(Long)} 校验目标景区，越权直接拒绝。</li>
 * </ul>
 *
 * @author smart-scenic
 */
public final class ScenicScope {

    private ScenicScope() {
    }

    /** 当前登录用户是否受景区限制（超管 scenicId 为空，不受限） */
    public static boolean isRestricted() {
        return UserContext.getScenicId() != null;
    }

    /**
     * 解析查询应使用的景区过滤条件
     *
     * <p>受限用户忽略请求参数，强制用自己所属景区；
     * 不受限用户按请求参数过滤（传 null 即查全部）。</p>
     *
     * @param requested 前端传入的景区ID
     * @return 实际生效的景区ID，null 表示不限制
     */
    public static Long resolve(Long requested) {
        Long own = UserContext.getScenicId();
        return own != null ? own : requested;
    }

    /**
     * 写入前校验目标景区是否在权限范围内
     *
     * @param targetScenicId 数据将要归属的景区ID
     * @throws BusinessException 越权时抛出
     */
    public static void checkWritable(Long targetScenicId) {
        Long own = UserContext.getScenicId();
        if (own == null) {
            return;
        }
        if (targetScenicId == null) {
            throw new BusinessException("您只能操作本景区数据，请指定所属景区");
        }
        if (!own.equals(targetScenicId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他景区的数据");
        }
    }

    /**
     * 写入前校验，并回填默认景区
     *
     * <p>受限用户未指定景区时自动归属本景区；超管未指定时保持 null（表示全局）。</p>
     */
    public static Long fillWritable(Long targetScenicId) {
        Long own = UserContext.getScenicId();
        if (own == null) {
            return targetScenicId;
        }
        if (targetScenicId != null && !own.equals(targetScenicId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他景区的数据");
        }
        return own;
    }
}
