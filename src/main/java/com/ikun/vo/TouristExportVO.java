package com.ikun.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游客导出 Excel 视图
 *
 * <p>与 {@link TouristVO} 字段一致，但加了 {@code @ExcelProperty} 中文字段名，
 * EasyExcel 才能写出带表头的 xlsx。</p>
 *
 * <p>列顺序与表头一一对应，不要轻易改动。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客导出 Excel 行")
public class TouristExportVO implements Serializable {

    @ExcelProperty("游客编号")
    @ColumnWidth(16)
    private String touristNo;

    @ExcelProperty("姓名")
    @ColumnWidth(14)
    private String realName;

    @ExcelProperty("手机号")
    @ColumnWidth(16)
    /** 已脱敏：138****1234 */
    private String phone;

    @ExcelProperty("证件号码")
    @ColumnWidth(22)
    /** 已脱敏：保留前 6 + 后 4 */
    private String idCard;

    @ExcelProperty("性别")
    @ColumnWidth(8)
    private String genderLabel;

    @ExcelProperty("注册来源")
    @ColumnWidth(14)
    private String sourceLabel;

    @ExcelProperty("会员等级")
    @ColumnWidth(12)
    private String memberLevelLabel;

    @ExcelProperty("积分")
    @ColumnWidth(8)
    private Integer points;

    @ExcelProperty("实名状态")
    @ColumnWidth(10)
    private String realNameStatusLabel;

    @ExcelProperty("是否黑名单")
    @ColumnWidth(10)
    private String blacklistLabel;

    @ExcelProperty("黑名单原因")
    @ColumnWidth(24)
    private String blacklistReason;

    @ExcelProperty("最近入园时间")
    @ColumnWidth(20)
    private String lastEnterTime;

    @ExcelProperty("账号状态")
    @ColumnWidth(10)
    private String statusLabel;

    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;

    @ExcelProperty("注册时间")
    @ColumnWidth(20)
    private LocalDateTime createTime;
}