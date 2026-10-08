package com.ikun.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 游客 Excel 导入视图
 *
 * <p>用于 EasyExcel 读取 xlsx，每行一条游客记录。
 * 与 TouristVO 不同的是：导入视图不装加密状态，允许读取原始手机号与证件号；
 * 但入库前仍会计算 phoneHash 并调用 SensitiveUtil.maskPhone 写到正式表。</p>
 *
 * <p>列顺序与 Excel 表头一一对应，<strong>不要随便改动</strong>，
 * 否则已分发的导入模板会与接口对不上。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客 Excel 导入行")
public class TouristImportVO implements Serializable {

    @ExcelProperty(value = "姓名*", index = 0)
    @ColumnWidth(14)
    @NotBlank(message = "姓名不能为空")
    private String realName;

    @ExcelProperty(value = "手机号*", index = 1)
    @ColumnWidth(16)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @ExcelProperty(value = "证件号码", index = 2)
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty(value = "性别", index = 3)
    @ColumnWidth(8)
    /** 允许写入：男 / 女 / 1 / 2 ；导入时统一规范化为 1 / 2 / 0 */
    private String gender;

    @ExcelProperty(value = "注册来源", index = 4)
    @ColumnWidth(14)
    /** MINI_PROGRAM / OTA / WINDOW */
    private String source;

    @ExcelProperty(value = "会员等级", index = 5)
    @ColumnWidth(12)
    /** NORMAL / SILVER / GOLD / DIAMOND */
    private String memberLevel;

    @ExcelProperty(value = "积分", index = 6)
    @ColumnWidth(8)
    private Integer points;

    @ExcelProperty(value = "状态", index = 7)
    @ColumnWidth(8)
    /** 正常 / 禁用 */
    private String status;

    @ExcelProperty(value = "备注", index = 8)
    @ColumnWidth(24)
    private String remark;
}