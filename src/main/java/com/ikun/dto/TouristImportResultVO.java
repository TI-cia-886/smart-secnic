package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 游客 Excel 导入结果
 *
 * <p>后端不会因为一行错误就中断整个导入，而是逐行校验后返回：</p>
 * <ul>
 *   <li>successRows：成功写入数据库的行数</li>
 *   <li>failedRows：失败的行数</li>
 *   <li>errorMessages：每条失败的具体原因（行号 + 错误信息）</li>
 * </ul>
 * 前端拿到结果后展示给用户，便于人工修补后再次导入。
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客 Excel 导入结果")
public class TouristImportResultVO implements Serializable {

    @Schema(description = "总行数（不含表头）")
    private Integer totalRows;

    @Schema(description = "成功导入条数")
    private Integer successRows;

    @Schema(description = "失败条数")
    private Integer failedRows;

    @Schema(description = "失败明细列表，每项形如「第3行: 手机号格式不正确」")
    private java.util.List<String> errorMessages;
}