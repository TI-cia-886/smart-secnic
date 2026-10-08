package com.ikun.common;

import lombok.Getter;

/**
 * 统一返回状态码
 *
 * @author smart-scenic
 */
@Getter
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 操作失败 */
    ERROR(500, "操作失败"),

    /** 参数校验失败 */
    PARAM_ERROR(400, "参数校验失败"),
    /** 未登录或登录已过期 */
    UNAUTHORIZED(401, "未登录或登录已过期"),
    /** 无访问权限 */
    FORBIDDEN(403, "无访问权限"),
    /** 资源不存在 */
    NOT_FOUND(404, "请求资源不存在"),

    /** 账号或密码错误 */
    LOGIN_ERROR(1001, "账号或密码错误"),
    /** 账号已被禁用 */
    ACCOUNT_DISABLED(1002, "账号已被禁用"),
    /** 账号不存在 */
    ACCOUNT_NOT_FOUND(1003, "账号不存在"),

    /** 景区不存在 */
    SCENIC_NOT_FOUND(2001, "景区不存在"),
    /** 票种库存不足 */
    TICKET_STOCK_NOT_ENOUGH(2002, "票种库存不足"),
    /** 订单状态不允许该操作 */
    ORDER_STATUS_ERROR(2003, "订单状态不允许该操作"),

    /** 业务异常 */
    BUSINESS_ERROR(9000, "业务处理异常");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
