package com.ikun.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.dto.TouristBlacklistDTO;
import com.ikun.dto.TouristImportResultVO;
import com.ikun.entity.Complaint;
import com.ikun.entity.Tourist;
import com.ikun.entity.TicketOrder;
import com.ikun.mapper.ComplaintMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.TouristService;
import com.ikun.util.PasswordUtil;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.StatItemVO;
import com.ikun.vo.TouristExportVO;
import com.ikun.vo.TouristImportVO;
import com.ikun.vo.TouristStatVO;
import com.ikun.vo.TouristVO;
import com.ikun.vo.TrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 游客信息服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouristServiceImpl extends ServiceImpl<TouristMapper, Tourist> implements TouristService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 统计默认时间窗：近 30 天 */
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final TicketOrderMapper ticketOrderMapper;
    private final ComplaintMapper complaintMapper;

    @Override
    public PageResult<TouristVO> pageQuery(Integer pageNum, Integer pageSize, String keyword, Long scenicId,
                                           String memberLevel, Integer isBlacklist, Integer realNameStatus,
                                           Integer status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<Tourist>lambdaQuery()
                // 游客的所属景区是「注册来源景区」，不是 scenicId，容易写错导致隔离失效
                .eq(scopeScenicId != null, Tourist::getRegisterScenicId, scopeScenicId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Tourist::getRealName, keyword).or()
                        .like(Tourist::getTouristNo, keyword))
                .eq(StringUtils.hasText(memberLevel), Tourist::getMemberLevel, memberLevel)
                .eq(isBlacklist != null, Tourist::getIsBlacklist, isBlacklist)
                .eq(realNameStatus != null, Tourist::getRealNameStatus, realNameStatus)
                .eq(status != null, Tourist::getStatus, status)
                .orderByDesc(Tourist::getCreateTime);
        Page<Tourist> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public TouristVO getDetail(Long id) {
        Tourist tourist = getById(id);
        if (tourist == null) {
            throw new BusinessException("游客不存在");
        }
        TouristVO vo = toVO(tourist);
        vo.setOrderCount(ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .eq(TicketOrder::getTouristId, id)));
        vo.setComplaintCount(complaintMapper.selectCount(Wrappers.<Complaint>lambdaQuery()
                .eq(Complaint::getTouristId, id)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTourist(Tourist tourist) {
        if (tourist.getId() == null) {
            throw new BusinessException("游客ID不能为空");
        }
        Tourist exist = getById(tourist.getId());
        if (exist == null) {
            throw new BusinessException("游客不存在");
        }
        checkScenicScope(exist);

        // 白名单式更新：只接受这三个后台确实需要维护的字段。
        // 若直接 updateById(tourist)，前端可以伪造积分、会员等级、实名状态甚至手机号。
        Tourist update = new Tourist();
        update.setId(tourist.getId());
        update.setRemark(tourist.getRemark());
        update.setMemberLevel(tourist.getMemberLevel());
        update.setStatus(tourist.getStatus());
        updateById(update);
        log.info("编辑游客信息成功：id={}", tourist.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToBlacklist(TouristBlacklistDTO dto) {
        Tourist exist = getById(dto.getTouristId());
        if (exist == null) {
            throw new BusinessException("游客不存在");
        }
        checkScenicScope(exist);
        if (exist.getIsBlacklist() != null && exist.getIsBlacklist() == 1) {
            throw new BusinessException("该游客已在黑名单中");
        }
        Tourist update = new Tourist();
        update.setId(dto.getTouristId());
        update.setIsBlacklist(1);
        update.setBlacklistReason(dto.getReason());
        update.setBlacklistTime(LocalDateTime.now());
        update.setBlacklistOperatorId(UserContext.getUserId());
        updateById(update);
        log.info("游客加入黑名单：touristId={}，operatorId={}，原因={}",
                dto.getTouristId(), UserContext.getUserId(), dto.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromBlacklist(Long id) {
        Tourist exist = getById(id);
        if (exist == null) {
            throw new BusinessException("游客不存在");
        }
        checkScenicScope(exist);
        if (exist.getIsBlacklist() == null || exist.getIsBlacklist() == 0) {
            throw new BusinessException("该游客当前不在黑名单中");
        }
        // 移出时保留原因与操作时间会让人误以为仍在黑名单，这里统一清空。
        // updateById 默认忽略 null 字段，因此必须用 UpdateWrapper 显式 set null，
        // 否则「已移出」的记录里还挂着上次的封禁原因。
        this.update(Wrappers.<Tourist>lambdaUpdate()
                .eq(Tourist::getId, id)
                .set(Tourist::getIsBlacklist, 0)
                .set(Tourist::getBlacklistReason, null)
                .set(Tourist::getBlacklistTime, null)
                .set(Tourist::getBlacklistOperatorId, null));
        log.info("游客移出黑名单：touristId={}，operatorId={}", id, UserContext.getUserId());
    }

    @Override
    public TouristStatVO statistics(Long scenicId, LocalDate startDate, LocalDate endDate) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }

        // 只取统计需要的列，一次查询在内存里同时算出趋势和三类分布，
        // 避免每个图表各发一条 SQL（4 张图 = 4 次全表扫描）
        List<Tourist> tourists = this.list(Wrappers.<Tourist>lambdaQuery()
                .select(Tourist::getCreateTime, Tourist::getMemberLevel, Tourist::getSource,
                        Tourist::getGender, Tourist::getRealNameStatus)
                .eq(scopeScenicId != null, Tourist::getRegisterScenicId, scopeScenicId)
                .ge(Tourist::getCreateTime, start.atStartOfDay())
                .le(Tourist::getCreateTime, LocalDateTime.of(end, LocalTime.MAX)));

        TouristStatVO stat = new TouristStatVO();
        stat.setTotalCount((long) tourists.size());

        Map<String, Long> dailyCount = new LinkedHashMap<>();
        Map<String, Long> levelCount = new LinkedHashMap<>();
        Map<String, Long> sourceCount = new LinkedHashMap<>();
        Map<String, Long> genderCount = new LinkedHashMap<>();
        long realNameCount = 0;

        for (Tourist tourist : tourists) {
            if (tourist.getRealNameStatus() != null && tourist.getRealNameStatus() == 1) {
                realNameCount++;
            }
            if (tourist.getCreateTime() != null) {
                dailyCount.merge(tourist.getCreateTime().toLocalDate().format(DATE_FORMAT), 1L, Long::sum);
            }
            levelCount.merge(labelMemberLevel(tourist.getMemberLevel()), 1L, Long::sum);
            sourceCount.merge(labelSource(tourist.getSource()), 1L, Long::sum);
            genderCount.merge(labelGender(tourist.getGender()), 1L, Long::sum);
        }

        stat.setRealNameCount(realNameCount);
        stat.setRealNameRate(rate(realNameCount, tourists.size()));
        // 趋势图必须逐日补零：缺口日期不能让折线直接跨过去，否则视觉上会「跳」
        stat.setTrend(buildTrend(dailyCount, start, end));
        stat.setMemberLevelDist(toStatItems(levelCount));
        stat.setSourceDist(toStatItems(sourceCount));
        stat.setGenderDist(toStatItems(genderCount));

        // 黑名单与新增看的是「当前状态」而非统计窗口，因此单独查
        stat.setBlacklistCount(this.count(Wrappers.<Tourist>lambdaQuery()
                .eq(scopeScenicId != null, Tourist::getRegisterScenicId, scopeScenicId)
                .eq(Tourist::getIsBlacklist, 1)));
        stat.setTodayNewCount(dailyCount.getOrDefault(LocalDate.now().format(DATE_FORMAT), 0L));
        return stat;
    }

    /* ==================== 私有方法 ==================== */

    private void checkScenicScope(Tourist tourist) {
        ScenicScope.checkWritable(tourist.getRegisterScenicId());
    }

    private TouristVO toVO(Tourist tourist) {
        TouristVO vo = new TouristVO();
        vo.setId(tourist.getId());
        vo.setTouristNo(tourist.getTouristNo());
        vo.setRealName(tourist.getRealName());
        vo.setPhone(SensitiveUtil.maskPhone(tourist.getPhone()));
        vo.setIdCard(maskIdCard(tourist.getIdCard()));
        vo.setGender(tourist.getGender());
        vo.setAvatar(tourist.getAvatar());
        vo.setSource(tourist.getSource());
        vo.setMemberLevel(tourist.getMemberLevel());
        vo.setPoints(tourist.getPoints());
        vo.setRealNameStatus(tourist.getRealNameStatus());
        vo.setIsBlacklist(tourist.getIsBlacklist());
        vo.setBlacklistReason(tourist.getBlacklistReason());
        vo.setBlacklistTime(tourist.getBlacklistTime());
        vo.setLastEnterTime(tourist.getLastEnterTime());
        vo.setRegisterScenicId(tourist.getRegisterScenicId());
        vo.setStatus(tourist.getStatus());
        vo.setRemark(tourist.getRemark());
        vo.setCreateTime(tourist.getCreateTime());
        return vo;
    }

    /** 证件号脱敏：保留前 6 位与后 4 位 */
    private String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 11) {
            return StringUtils.hasText(idCard) ? "***" : null;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private List<TrendItemVO> buildTrend(Map<String, Long> dailyCount, LocalDate start, LocalDate end) {
        List<TrendItemVO> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyCount.getOrDefault(key, 0L)));
        }
        return trend;
    }

    private List<StatItemVO> toStatItems(Map<String, Long> countMap) {
        List<StatItemVO> items = new ArrayList<>();
        countMap.forEach((name, value) -> items.add(new StatItemVO(name, value)));
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return items;
    }

    /** 占比（百分比，保留两位小数），分母为 0 时返回 0，避免除零 */
    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private String labelMemberLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "普通会员";
        }
        return switch (level) {
            case "SILVER" -> "白银会员";
            case "GOLD" -> "黄金会员";
            case "DIAMOND" -> "钻石会员";
            default -> "普通会员";
        };
    }

    private String labelSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "未知";
        }
        return switch (source) {
            case "MINI_PROGRAM" -> "小程序";
            case "OTA" -> "OTA平台";
            case "WINDOW" -> "窗口";
            default -> source;
        };
    }

    private String labelGender(Integer gender) {
        if (gender == null) {
            return "未知";
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    /* ============================================================
     *                      导入 / 导出
     * ============================================================ */

    @Override
    public List<TouristExportVO> listForExport(String keyword, Long scenicId, String memberLevel,
                                               Integer isBlacklist, Integer realNameStatus, Integer status,
                                               int maxRows) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<Tourist>lambdaQuery()
                .eq(scopeScenicId != null, Tourist::getRegisterScenicId, scopeScenicId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Tourist::getRealName, keyword).or()
                        .like(Tourist::getTouristNo, keyword))
                .eq(StringUtils.hasText(memberLevel), Tourist::getMemberLevel, memberLevel)
                .eq(isBlacklist != null, Tourist::getIsBlacklist, isBlacklist)
                .eq(realNameStatus != null, Tourist::getRealNameStatus, realNameStatus)
                .eq(status != null, Tourist::getStatus, status)
                .orderByDesc(Tourist::getCreateTime)
                // 导出上限保护：百万级匹配里单次导出不超过 maxRows，避免 OOM
                .last("LIMIT " + Math.max(1, Math.min(maxRows, 50000)));
        List<Tourist> list = this.list(wrapper);
        List<TouristExportVO> vos = new ArrayList<>(list.size());
        for (Tourist t : list) {
            vos.add(toExportVO(t));
        }
        return vos;
    }

    /**
     * Entity → TouristExportVO，并把枚举值翻译为中文标签。
     * 与 {@link #toVO(Tourist)} 的区别是：导出 VO 还要展示性别 / 来源 / 等级 / 状态的中文标签。
     */
    private TouristExportVO toExportVO(Tourist tourist) {
        TouristExportVO vo = new TouristExportVO();
        vo.setTouristNo(tourist.getTouristNo());
        vo.setRealName(tourist.getRealName());
        vo.setPhone(SensitiveUtil.maskPhone(tourist.getPhone()));
        vo.setIdCard(maskIdCard(tourist.getIdCard()));
        vo.setGenderLabel(labelGender(tourist.getGender()));
        vo.setSourceLabel(labelSource(tourist.getSource()));
        vo.setMemberLevelLabel(labelMemberLevel(tourist.getMemberLevel()));
        vo.setPoints(tourist.getPoints());
        vo.setRealNameStatusLabel(tourist.getRealNameStatus() != null && tourist.getRealNameStatus() == 1 ? "已实名" : "未实名");
        vo.setBlacklistLabel(tourist.getIsBlacklist() != null && tourist.getIsBlacklist() == 1 ? "是" : "否");
        vo.setBlacklistReason(tourist.getBlacklistReason());
        vo.setLastEnterTime(tourist.getLastEnterTime() == null ? null
                : tourist.getLastEnterTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        vo.setStatusLabel(tourist.getStatus() != null && tourist.getStatus() == 1 ? "正常" : "禁用");
        vo.setRemark(tourist.getRemark());
        vo.setCreateTime(tourist.getCreateTime());
        return vo;
    }

    @Override
    public TouristImportResultVO importFromExcel(MultipartFile file) {
        TouristImportResultVO result = new TouristImportResultVO();
        List<String> errors = new ArrayList<>();
        int total = 0, success = 0, failed = 0;

        if (file == null || file.isEmpty()) {
            result.setFailedRows(1);
            errors.add("第1行: 上传的文件为空");
            result.setErrorMessages(errors);
            return result;
        }

        try {
            List<TouristImportVO> rows = EasyExcel.read(file.getInputStream())
                    .head(TouristImportVO.class)
                    .sheet()
                    .doReadSync();

            if (rows == null || rows.isEmpty()) {
                result.setFailedRows(1);
                errors.add("第1行: Excel 文件没有可导入的行");
                result.setErrorMessages(errors);
                return result;
            }

            total = rows.size();
            // 当前登录管理员的所属景区作为导入游客的 register_scenic_id（NULL 表示全平台）
            Long defaultScenicId = UserContext.getScenicId();

            for (int i = 0; i < rows.size(); i++) {
                TouristImportVO row = rows.get(i);
                int rowNo = i + 2; // 加上表头占的 1 行
                try {
                    if (row == null || !StringUtils.hasText(row.getRealName())) {
                        throw new BusinessException("姓名不能为空");
                    }
                    String phone = row.getPhone() == null ? "" : row.getPhone().trim();
                    if (!SensitiveUtil.isValidPhone(phone)) {
                        throw new BusinessException("手机号格式不正确");
                    }
                    // 唯一性校验：phoneHash 已存在则跳过
                    String phoneHash = SensitiveUtil.phoneHash(phone);
                    Long dup = this.baseMapper.selectCount(Wrappers.<Tourist>lambdaQuery()
                            .eq(Tourist::getPhoneHash, phoneHash));
                    if (dup != null && dup > 0) {
                        throw new BusinessException("该手机号已存在，跳过");
                    }

                    Tourist t = new Tourist();
                    t.setTouristNo(generateTouristNo());
                    t.setRealName(row.getRealName().trim());
                    t.setPhone(phone);
                    t.setPhoneHash(phoneHash);
                    // 导入创建的游客默认密码 = 手机号后六位（与小程序注册逻辑一致）
                    t.setPassword(PasswordUtil.encrypt(SensitiveUtil.defaultPassword(phone)));
                    t.setIdCard(StringUtils.hasText(row.getIdCard()) ? row.getIdCard().trim() : null);
                    if (StringUtils.hasText(row.getIdCard())) {
                        t.setRealNameStatus(1);
                    } else {
                        t.setRealNameStatus(0);
                    }
                    t.setGender(parseGender(row.getGender()));
                    t.setSource(normalizeSource(row.getSource()));
                    t.setMemberLevel(normalizeMemberLevel(row.getMemberLevel()));
                    t.setPoints(row.getPoints() == null ? 0 : row.getPoints());
                    t.setStatus(normalizeStatus(row.getStatus()));
                    t.setRemark(row.getRemark());
                    t.setIsBlacklist(0);
                    t.setRegisterScenicId(defaultScenicId);

                    this.baseMapper.insert(t);
                    success++;
                } catch (BusinessException e) {
                    failed++;
                    errors.add("第" + rowNo + "行: " + e.getMessage());
                } catch (Exception e) {
                    failed++;
                    errors.add("第" + rowNo + "行: 解析失败 - " + e.getMessage());
                    log.warn("导入游客第{}行解析失败", rowNo, e);
                }
            }
        } catch (IOException e) {
            log.error("解析 Excel 文件失败", e);
            result.setFailedRows(1);
            errors.add("第1行: Excel 文件解析失败 - " + e.getMessage());
            result.setErrorMessages(errors);
            return result;
        }

        result.setTotalRows(total);
        result.setSuccessRows(success);
        result.setFailedRows(failed);
        result.setErrorMessages(errors);
        return result;
    }

    /** 性别字段允许的中文 / 数字输入，统一规范化为 1 / 2 / 0 */
    private Integer parseGender(String raw) {
        if (!StringUtils.hasText(raw)) return 0;
        String s = raw.trim();
        if ("男".equals(s) || "1".equals(s)) return 1;
        if ("女".equals(s) || "2".equals(s)) return 2;
        return 0;
    }

    private String normalizeSource(String raw) {
        if (!StringUtils.hasText(raw)) return "MINI_PROGRAM";
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "小程序", "MINI_PROGRAM" -> "MINI_PROGRAM";
            case "OTA", "OTA平台", "第三方" -> "OTA";
            case "窗口", "WINDOW", "现场" -> "WINDOW";
            default -> "MINI_PROGRAM";
        };
    }

    private String normalizeMemberLevel(String raw) {
        if (!StringUtils.hasText(raw)) return "NORMAL";
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "NORMAL", "普通", "普通会员" -> "NORMAL";
            case "SILVER", "白银", "白银会员" -> "SILVER";
            case "GOLD", "黄金", "黄金会员" -> "GOLD";
            case "DIAMOND", "钻石", "钻石会员" -> "DIAMOND";
            default -> "NORMAL";
        };
    }

    private Integer normalizeStatus(String raw) {
        if (!StringUtils.hasText(raw)) return 1;
        String s = raw.trim();
        if ("正常".equals(s) || "1".equals(s) || "启用".equals(s)) return 1;
        if ("禁用".equals(s) || "0".equals(s) || "停用".equals(s)) return 0;
        return 1;
    }

    private static final Pattern TOURIST_NO_PATTERN = Pattern.compile("T\\d{8}");
    private String generateTouristNo() {
        // 形如 T20250921 + 6 位随机。重复概率极低；如果并发极高可改为 Redis incr。
        return "T" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + (IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
    }
}
