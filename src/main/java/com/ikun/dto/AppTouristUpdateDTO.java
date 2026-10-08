package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小程序修改个人资料参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "修改个人资料参数")
public class AppTouristUpdateDTO {

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "证件号码，填写后账号将标记为已实名")
    private String idCard;

    @Schema(description = "性别：1男 2女 0未知")
    private Integer gender;

    @Schema(description = "头像地址")
    private String avatar;
}
