package com.ikun.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.common.UserContext;
import com.ikun.entity.FaceRecord;
import com.ikun.entity.SysUser;
import com.ikun.entity.Tourist;
import com.ikun.mapper.FaceRecordMapper;
import com.ikun.mapper.SysUserMapper;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.TencentFaceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人脸识别管理接口
 *
 * <p>基于腾讯云人脸识别（IAI）服务，人脸库 GroupId 为 {@code smart-scenic}：
 * <ul>
 *   <li>人脸注册：游客档案照片入库，PersonId 使用游客编号 tourist_no</li>
 *   <li>人脸搜索：1:N 识别，上传照片在人脸库中查找最相似的游客</li>
 *   <li>人脸比对：1:1 验证，计算两张照片的相似度</li>
 *   <li>人脸测试：人脸检测分析，输出年龄 / 性别 / 表情 / 口罩 / 质量分等</li>
 * </ul>
 * 每次调用都会落一条 face_record 识别记录，便于审计。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Tag(name = "14-人脸识别", description = "腾讯云人脸库管理、人脸注册 / 搜索 / 比对 / 检测")
@RestController
@RequestMapping("/face")
@RequiredArgsConstructor
public class FaceController {

    private final TencentFaceClient faceClient;
    private final FaceRecordMapper faceRecordMapper;
    private final TouristMapper touristMapper;
    private final SysUserMapper sysUserMapper;

    /* ==================== 人脸库 ==================== */

    @Operation(summary = "查询人脸库信息", description = "返回库 ID、名称、人员数量；库不存在时自动创建")
    @RequirePerm("face:list")
    @GetMapping("/group")
    public Result<Map<String, Object>> groupInfo() {
        faceClient.ensureGroup();
        return Result.success(faceClient.groupInfo());
    }

    @Operation(summary = "人脸库人员分页", description = "腾讯云人脸库 smart-scenic 中已注册的人员列表")
    @RequirePerm("face:list")
    @GetMapping("/persons")
    public Result<Map<String, Object>> persons(@RequestParam(defaultValue = "0") Integer offset,
                                               @RequestParam(defaultValue = "20") Integer limit) {
        faceClient.ensureGroup();
        return Result.success(faceClient.personList(offset, limit));
    }

    @Operation(summary = "从人脸库删除人员", description = "PersonId 为游客编号 tourist_no；删除后该游客照片不可再被搜索命中")
    @RequirePerm("face:enroll")
    @OperLog(title = "人脸识别", businessType = "DELETE")
    @DeleteMapping("/person/{personId}")
    public Result<Void> deletePerson(@PathVariable String personId) {
        faceClient.deletePerson(personId);
        return Result.success("已从人脸库删除", null);
    }

    /* ==================== 人脸注册 ==================== */

    @Operation(summary = "游客人脸注册", description = "上传游客照片加入人脸库；PersonId 使用游客编号，同一游客重复注册会被拒绝")
    @RequirePerm("face:enroll")
    @OperLog(title = "人脸识别", businessType = "INSERT")
    @PostMapping("/enroll")
    public Result<Map<String, Object>> enroll(@RequestParam Long touristId,
                                              @RequestPart("file") MultipartFile file) throws IOException {
        Tourist tourist = touristMapper.selectById(touristId);
        if (tourist == null) {
            throw new BusinessException("游客不存在");
        }
        String image = readImage(file);
        faceClient.ensureGroup();
        String faceId = faceClient.createPerson(tourist.getTouristNo(), tourist.getRealName(), image);

        FaceRecord record = new FaceRecord();
        record.setRecordType("ENROLL");
        record.setTouristId(tourist.getId());
        record.setTouristNo(tourist.getTouristNo());
        record.setTouristName(tourist.getRealName());
        record.setDetail("人脸注册成功，faceId=" + faceId);
        record.setOperatorId(UserContext.getUserId());
        faceRecordMapper.insert(record);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("faceId", faceId);
        out.put("personId", tourist.getTouristNo());
        out.put("personName", tourist.getRealName());
        return Result.success("人脸注册成功", out);
    }

    @Operation(summary = "账号人脸注册", description = "上传后台账号照片加入人脸库；PersonId 为 U{userId}，用于工作人员人脸登录/识别")
    @RequirePerm("face:enroll")
    @OperLog(title = "人脸识别", businessType = "INSERT")
    @PostMapping("/enroll/account")
    public Result<Map<String, Object>> enrollAccount(@RequestParam Long userId,
                                                     @RequestPart("file") MultipartFile file) throws IOException {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        String personId = "U" + userId;
        String personName = user.getRealName() != null && !user.getRealName().isBlank()
                ? user.getRealName() : user.getUsername();
        String image = readImage(file);
        faceClient.ensureGroup();
        String faceId = faceClient.createPerson(personId, personName, image);

        FaceRecord record = new FaceRecord();
        record.setRecordType("ENROLL");
        record.setTouristName(personName);
        record.setDetail("账号人脸注册成功，personId=" + personId + "，faceId=" + faceId);
        record.setOperatorId(UserContext.getUserId());
        faceRecordMapper.insert(record);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("faceId", faceId);
        out.put("personId", personId);
        out.put("personName", personName);
        return Result.success("账号人脸注册成功", out);
    }

    /* ==================== 人脸搜索（1:N） ==================== */

    @Operation(summary = "人脸搜索", description = "上传照片在 smart-scenic 人脸库中 1:N 检索，返回最相似的候选游客（含档案信息）")
    @RequirePerm("face:search")
    @OperLog(title = "人脸识别", businessType = "SELECT")
    @PostMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestPart("file") MultipartFile file) throws IOException {
        String image = readImage(file);
        faceClient.ensureGroup();
        List<Map<String, Object>> candidates = faceClient.searchFace(image);

        // 合并游客档案信息（黑名单标记等），并把命中最高的记录落库
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Map<String, Object> item = new LinkedHashMap<>(c);
            String personId = (String) c.get("personId");
            Tourist tourist = touristMapper.selectOne(Wrappers.<Tourist>lambdaQuery()
                    .eq(Tourist::getTouristNo, personId).last("LIMIT 1"));
            if (tourist != null) {
                item.put("touristId", tourist.getId());
                item.put("phone", maskPhone(tourist.getPhone()));
                item.put("memberLevel", tourist.getMemberLevel());
                item.put("isBlacklist", tourist.getIsBlacklist());
                item.put("blacklistReason", tourist.getBlacklistReason());
                item.put("status", tourist.getStatus());
            }
            merged.add(item);
        }

        if (!merged.isEmpty()) {
            Map<String, Object> best = merged.get(0);
            FaceRecord record = new FaceRecord();
            record.setRecordType("SEARCH");
            record.setTouristId(best.get("touristId") == null ? null : Long.valueOf(best.get("touristId").toString()));
            record.setTouristNo((String) best.get("personId"));
            record.setTouristName((String) best.get("personName"));
            record.setConfidence(toDecimal(best.get("confidence")));
            record.setDetail("命中 " + merged.size() + " 个候选，最高相似度 " + best.get("confidence"));
            record.setOperatorId(UserContext.getUserId());
            faceRecordMapper.insert(record);
        }
        return Result.success(merged);
    }

    /* ==================== 人脸比对（1:1） ==================== */

    @Operation(summary = "人脸比对", description = "上传两张照片计算 1:1 相似度（0~100），常用于人证核验")
    @RequirePerm("face:search")
    @OperLog(title = "人脸识别", businessType = "SELECT")
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestPart("fileA") MultipartFile fileA,
                                               @RequestPart("fileB") MultipartFile fileB) throws IOException {
        Double score = faceClient.compareFace(readImage(fileA), readImage(fileB));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("conclusion", score >= 80 ? "高度相似，判定为同一人"
                : score >= 60 ? "中度相似，建议人工复核" : "相似度较低，判定为不同人");

        FaceRecord record = new FaceRecord();
        record.setRecordType("COMPARE");
        record.setConfidence(BigDecimal.valueOf(score).setScale(2, java.math.RoundingMode.HALF_UP));
        record.setDetail("1:1 比对相似度 " + score);
        record.setOperatorId(UserContext.getUserId());
        faceRecordMapper.insert(record);
        return Result.success(out);
    }

    /* ==================== 人脸测试（检测分析） ==================== */

    @Operation(summary = "人脸检测测试", description = "检测照片中的人脸并分析属性：性别 / 年龄 / 表情 / 眼镜 / 口罩 / 质量分")
    @RequirePerm("face:detect")
    @OperLog(title = "人脸识别", businessType = "SELECT")
    @PostMapping("/detect")
    public Result<List<Map<String, Object>>> detect(@RequestPart("file") MultipartFile file) throws IOException {
        List<Map<String, Object>> faces = faceClient.detectFace(readImage(file));

        FaceRecord record = new FaceRecord();
        record.setRecordType("DETECT");
        record.setFaceCount(faces.size());
        if (!faces.isEmpty()) {
            Object first = faces.get(0);
            record.setDetail("首张人脸：" + faces.get(0).entrySet().stream()
                    .filter(e -> !"faceRect".equals(e.getKey()))
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));
        } else {
            record.setDetail("未检测到人脸");
        }
        record.setOperatorId(UserContext.getUserId());
        faceRecordMapper.insert(record);
        return Result.success(faces);
    }

    /* ==================== 识别记录 ==================== */

    @Operation(summary = "识别记录分页", description = "本地 face_record 表，含注册 / 搜索 / 比对 / 检测全部历史")
    @RequirePerm("face:list")
    @GetMapping("/records")
    public Result<PageResult<FaceRecord>> records(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) String recordType,
                                                  @RequestParam(required = false) String keyword) {
        var wrapper = Wrappers.<FaceRecord>lambdaQuery()
                .eq(recordType != null && !recordType.isBlank(), FaceRecord::getRecordType, recordType)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(FaceRecord::getTouristName, keyword).or()
                        .like(FaceRecord::getTouristNo, keyword))
                .orderByDesc(FaceRecord::getId);
        return Result.success(PageResult.of(faceRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper)));
    }

    /* ==================== 私有方法 ==================== */

    /** 读取上传图片为 base64（不带 data URI 前缀），限制 5MB */
    private String readImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传人脸照片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("照片过大（限 5MB），请压缩后重试");
        }
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    /** 手机号脱敏：138****1234 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private BigDecimal toDecimal(Object v) {
        return v == null ? null : BigDecimal.valueOf(Double.parseDouble(v.toString()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
