package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.TouristBlacklistDTO;
import com.ikun.dto.TouristImportResultVO;
import com.ikun.entity.Tourist;
import com.ikun.vo.TouristExportVO;
import com.ikun.vo.TouristImportVO;
import com.ikun.vo.TouristStatVO;
import com.ikun.vo.TouristVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 游客信息服务
 *
 * @author smart-scenic
 */
public interface TouristService {

    PageResult<TouristVO> pageQuery(Integer pageNum, Integer pageSize, String keyword, Long scenicId,
                                    String memberLevel, Integer isBlacklist, Integer realNameStatus,
                                    Integer status);

    /** 游客详情，附带累加订单数与工单数 */
    TouristVO getDetail(Long id);

    /** 编辑游客资料：仅允许修改备注、会员等级、状态 */
    void updateTourist(Tourist tourist);

    /** 加入黑名单 */
    void addToBlacklist(TouristBlacklistDTO dto);

    /** 移出黑名单 */
    void removeFromBlacklist(Long id);

    /**
     * 注册统计
     *
     * @param startDate 统计起始日期（含），为空默认近 30 天
     * @param endDate   统计结束日期（含），为空默认今天
     */
    TouristStatVO statistics(Long scenicId, LocalDate startDate, LocalDate endDate);

    /**
     * 按查询条件拉导出列表，最多 maxRows 条（默认 5000）。
     * 返回的对象是 Excel 友好的 VO（手机号脱敏、会员等级中文等）。
     */
    List<TouristExportVO> listForExport(String keyword, Long scenicId, String memberLevel,
                                        Integer isBlacklist, Integer realNameStatus, Integer status,
                                        int maxRows);

    /**
     * 从 Excel 文件批量导入游客。
     *
     * <p>逐行校验，任一行失败都会写入 errorMessages 并继续校验后续行；
     * 合法行会真正入库（同一手机号已存在则跳过）。
     * 不抛异常中断：保证导入人员能一次看到所有问题。</p>
     *
     * @param file 上传的 xlsx 文件
     * @return 导入结果，包含总数、成功 / 失败条数、错误明细
     */
    TouristImportResultVO importFromExcel(MultipartFile file);
}
