package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 游客小程序登录结果
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "小程序登录结果")
public class AppLoginVO implements Serializable {

    @Schema(description = "访问令牌，后续请求放入 Authorization 头")
    private String token;

    @Schema(description = "令牌类型前缀", example = "Bearer ")
    private String tokenPrefix;

    @Schema(description = "有效期（秒）")
    private Long expiresIn;

    @Schema(description = "游客ID")
    private Long touristId;

    @Schema(description = "游客编号")
    private String touristNo;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "手机号（已脱敏）")
    private String phone;

    @Schema(description = "会员等级")
    private String memberLevel;

    @Schema(description = "积分")
    private Integer points;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "是否已实名：1是 0否")
    private Integer realNameStatus;
}
