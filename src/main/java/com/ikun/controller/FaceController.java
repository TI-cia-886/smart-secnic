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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 人脸管理接口
 *
 * <p>基于腾讯云人脸识别（IAI）服务，人脸库 GroupId 为 {@code smart-scenic}：
 * <ul>
 *   <li>人脸注册：工作人员（后台账号，PersonId=U{userId}）与游客（PersonId=tourist_no）照片入库</li>
 *   <li>人员管理：人脸库人员分页查询、按对象类型分类展示、删除人员</li>
 * </ul>
 * 注册 / 删除操作都会落一条 face_record 记录，便于审计。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Tag(name = "14-人脸管理", description = "腾讯云人脸库管理：工作人员 / 游客人脸注册与删除")
@RestController
@RequestMapping("/face")
@RequiredArgsConstructor
public class FaceController {

    /** 人脸照片允许的扩展名：腾讯云 IAI 只接受常见图片格式，其他文件会在服务端报错 */
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png");

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

    @Operation(summary = "从人脸库删除人员", description = "PersonId 为游客编号 tourist_no（工作人员为 U{userId}）；删除后该游客照片不可再被搜索命中，并同步本地人脸状态")
    @RequirePerm("face:enroll")
    @OperLog(title = "人脸管理", businessType = "DELETE")
    @DeleteMapping("/person/{personId}")
    public Result<Void> deletePerson(@PathVariable String personId) {
        faceClient.deletePerson(personId);
        // 游客人员（非 U 开头）删除后，本地 face_status 同步归零，保证游客列表/档案状态一致
        if (personId != null && !personId.startsWith("U")) {
            Tourist tourist = touristMapper.selectOne(
                    Wrappers.<Tourist>lambdaQuery().eq(Tourist::getTouristNo, personId));
            if (tourist != null) {
                Tourist upd = new Tourist();
                upd.setId(tourist.getId());
                upd.setFaceStatus(0);
                touristMapper.updateById(upd);
            }
            FaceRecord rec = new FaceRecord();
            rec.setRecordType("DELETE");
            rec.setTouristNo(personId);
            rec.setDetail("人脸删除，personId=" + personId);
            rec.setOperatorId(UserContext.getUserId());
            faceRecordMapper.insert(rec);
        }
        return Result.success("已从人脸库删除", null);
    }

    /* ==================== 人脸注册 ==================== */

    @Operation(summary = "游客人脸注册", description = "上传游客照片加入人脸库；PersonId 使用游客编号，同一游客重复注册会被拒绝")
    @RequirePerm("face:enroll")
    @OperLog(title = "人脸管理", businessType = "INSERT")
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
    @OperLog(title = "人脸管理", businessType = "INSERT")
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

    /* ==================== 操作记录 ==================== */

    @Operation(summary = "人脸操作记录分页", description = "本地 face_record 表，含注册 / 删除等全部历史")
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

    /** 读取上传图片为 base64（不带 data URI 前缀），限制 5MB 且必须是图片格式 */
    private String readImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传人脸照片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("照片过大（限 5MB），请压缩后重试");
        }
        String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename()
                        .substring(file.getOriginalFilename().lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg / jpeg / png 格式的人脸照片");
        }
        return Base64.getEncoder().encodeToString(file.getBytes());
    }
}
