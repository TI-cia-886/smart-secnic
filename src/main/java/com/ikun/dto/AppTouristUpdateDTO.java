package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小程序修改个人资料参数
 *
 * <p>实名信息是购票前置条件（下单前校验 realNameStatus），因此证件号必须按身份证规则校验，
 * 否则随便填一个字符串就能标记「已实名」，实名核验形同虚设。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "修改个人资料参数")
public class AppTouristUpdateDTO {

    @Size(max = 30, message = "姓名长度不能超过 30 个字符")
    @Schema(description = "真实姓名")
    private String realName;

    // 18 位身份证：6 位地区码 + 8 位生日 + 3 位顺序码 + 1 位校验位（末位可为 X）
    @Pattern(
            regexp = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "请输入正确的 18 位身份证号"
    )
    @Schema(description = "证件号码，填写后账号将标记为已实名")
    private String idCard;

    @Min(value = 0, message = "性别取值只能是 0/1/2")
    @Max(value = 2, message = "性别取值只能是 0/1/2")
    @Schema(description = "性别：1男 2女 0未知")
    private Integer gender;

    @Size(max = 255, message = "头像地址过长")
    @Schema(description = "头像地址")
    private String avatar;
}
