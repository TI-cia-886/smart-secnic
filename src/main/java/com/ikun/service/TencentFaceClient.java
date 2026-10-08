package com.ikun.service;

import com.ikun.common.BusinessException;
import com.ikun.config.TencentFaceProperties;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20200303.IaiClient;
import com.tencentcloudapi.iai.v20200303.models.CompareFaceRequest;
import com.tencentcloudapi.iai.v20200303.models.CompareFaceResponse;
import com.tencentcloudapi.iai.v20200303.models.CreateGroupRequest;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonRequest;
import com.tencentcloudapi.iai.v20200303.models.DeletePersonRequest;
import com.tencentcloudapi.iai.v20200303.models.DetectFaceRequest;
import com.tencentcloudapi.iai.v20200303.models.DetectFaceResponse;
import com.tencentcloudapi.iai.v20200303.models.FaceQualityInfo;
import com.tencentcloudapi.iai.v20200303.models.GetGroupInfoRequest;
import com.tencentcloudapi.iai.v20200303.models.GetGroupInfoResponse;
import com.tencentcloudapi.iai.v20200303.models.GetPersonListRequest;
import com.tencentcloudapi.iai.v20200303.models.GetPersonListResponse;
import com.tencentcloudapi.iai.v20200303.models.SearchFacesRequest;
import com.tencentcloudapi.iai.v20200303.models.SearchFacesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 腾讯云人脸识别（IAI）客户端
 *
 * <p>封装人脸库（Group）管理、人员注册 / 删除、1:N 搜索、
 * 1:1 比对与人脸检测分析。所有方法入参图片均为 base64 字符串
 * （不带 data:image 前缀，SDK 内部会再编码一次）。</p>
 *
 * <p>调用失败统一抛 {@link BusinessException}，由全局异常处理器
 * 收敛为中文提示，避免 SDK 异常直接泄漏到前端。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TencentFaceClient {

    private final TencentFaceProperties props;

    /** 人员库已存在时的 SDK 错误码，可安全忽略 */
    private static final String CODE_GROUP_EXISTS = "FailedOperation.GroupInGroupList";
    private static final String CODE_GROUP_NAME_EXISTS = "FailedOperation.GroupNameInGroupList";
    private static final String CODE_PERSON_EXISTS = "FailedOperation.PersonExist";

    private IaiClient buildClient() {
        if (!props.isConfigured()) {
            throw new BusinessException("腾讯云人脸识别未配置：请在 application-dev.yml 填写 tencent.face.secret-key");
        }
        Credential cred = new Credential(props.getSecretId(), props.getSecretKey());
        HttpProfile http = new HttpProfile();
        http.setEndpoint("iai.tencentcloudapi.com");
        ClientProfile profile = new ClientProfile();
        profile.setHttpProfile(http);
        return new IaiClient(cred, props.getRegion(), profile);
    }

    /** 确保 smart-scenic 人脸库存在，不存在则创建（幂等） */
    public void ensureGroup() {
        IaiClient client = buildClient();
        try {
            CreateGroupRequest req = new CreateGroupRequest();
            req.setGroupId(props.getGroupId());
            req.setGroupName(props.getGroupName());
            client.CreateGroup(req);
            log.info("腾讯云人脸库已创建：{}", props.getGroupId());
        } catch (TencentCloudSDKException e) {
            if (e.getErrorCode() != null && (
                    e.getErrorCode().equals(CODE_GROUP_EXISTS)
                    || e.getErrorCode().equals(CODE_GROUP_NAME_EXISTS)
            )) {
                return;
            }
            if (e.getMessage() != null && e.getMessage().contains("人员库名称已经存在")) {
                return;
            }
            throw wrap(e);
        }
    }

    /**
     * 查询人脸库信息
     *
     * @return groupId / groupName / 人脸模型版本 / 当前人员数量
     */
    public Map<String, Object> groupInfo() {
        IaiClient client = buildClient();
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            GetGroupInfoRequest req = new GetGroupInfoRequest();
            req.setGroupId(props.getGroupId());
            GetGroupInfoResponse resp = client.GetGroupInfo(req);
            out.put("groupId", resp.getGroupId());
            out.put("groupName", resp.getGroupName());
            out.put("faceModelVersion", resp.getFaceModelVersion());
        } catch (TencentCloudSDKException e) {
            throw wrap(e);
        }
        // GetGroupInfo 无人员数，用 GetPersonList(0,1) 取 total
        try {
            GetPersonListRequest req = new GetPersonListRequest();
            req.setGroupId(props.getGroupId());
            req.setOffset(0L);
            req.setLimit(1L);
            GetPersonListResponse resp = client.GetPersonList(req);
            out.put("personCount", resp.getPersonNum());
        } catch (TencentCloudSDKException e) {
            out.put("personCount", 0);
        }
        return out;
    }

    /**
     * 人脸库人员分页列表
     */
    public Map<String, Object> personList(Integer offset, Integer limit) {
        IaiClient client = buildClient();
        try {
            GetPersonListRequest req = new GetPersonListRequest();
            req.setGroupId(props.getGroupId());
            req.setOffset(Long.valueOf(offset));
            req.setLimit(Long.valueOf(limit));
            GetPersonListResponse resp = client.GetPersonList(req);
            List<Map<String, Object>> persons = new ArrayList<>();
            if (resp.getPersonInfos() != null) {
                for (com.tencentcloudapi.iai.v20200303.models.PersonInfo p : resp.getPersonInfos()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("personId", p.getPersonId());
                    item.put("personName", p.getPersonName());
                    item.put("faceCount", p.getFaceIds() == null ? 0 : p.getFaceIds().length);
                    persons.add(item);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", resp.getPersonNum());
            out.put("persons", persons);
            return out;
        } catch (TencentCloudSDKException e) {
            throw wrap(e);
        }
    }

    /**
     * 创建人员（含一张入库人脸）
     *
     * @param personId   人员 ID，本项目使用游客编号 tourist_no
     * @param personName 人员名称，本项目使用游客姓名
     * @param imageBase64 人脸照片 base64
     * @return 人脸图片唯一标识 FaceId
     */
    public String createPerson(String personId, String personName, String imageBase64) {
        if (!StringUtils.hasText(personId) || !StringUtils.hasText(imageBase64)) {
            throw new BusinessException("personId 与人脸照片不能为空");
        }
        IaiClient client = buildClient();
        try {
            CreatePersonRequest req = new CreatePersonRequest();
            req.setGroupId(props.getGroupId());
            req.setPersonId(personId);
            req.setPersonName(personName);
            req.setImage(imageBase64);
            req.setQualityControl(1L);
            com.tencentcloudapi.iai.v20200303.models.CreatePersonResponse resp =
                    client.CreatePerson(req);
            log.info("人脸入库成功：personId={}，faceId={}，faceRect={}",
                    personId, resp.getFaceId(), resp.getFaceRect() != null ? "有" : "无");
            return resp.getFaceId();
        } catch (TencentCloudSDKException e) {
            if (e.getErrorCode() != null && e.getErrorCode().equals(CODE_PERSON_EXISTS)) {
                throw new BusinessException("该游客已注册人脸，请先删除旧人脸后重新上传");
            }
            throw wrap(e);
        }
    }

    /** 从人脸库删除人员 */
    public void deletePerson(String personId) {
        IaiClient client = buildClient();
        try {
            DeletePersonRequest req = new DeletePersonRequest();
            req.setPersonId(personId);
            client.DeletePerson(req);
        } catch (TencentCloudSDKException e) {
            throw wrap(e);
        }
    }

    /**
     * 1:N 人脸搜索：在 smart-scenic 库中查找与上传照片最相似的人员
     *
     * @return 候选列表（personId / personName / confidence 0~100），无命中返回空
     */
    public List<Map<String, Object>> searchFace(String imageBase64) {
        IaiClient client = buildClient();
        try {
            SearchFacesRequest req = new SearchFacesRequest();
            req.setGroupIds(new String[]{props.getGroupId()});
            req.setImage(imageBase64);
            req.setMaxFaceNum(1L);
            req.setMaxPersonNum(5L);
            req.setFaceMatchThreshold(props.getMatchThreshold().floatValue());
            req.setNeedPersonInfo(1L);
            SearchFacesResponse resp = client.SearchFaces(req);
            List<Map<String, Object>> candidates = new ArrayList<>();
            if (resp.getResults() != null && resp.getResults().length > 0
                    && resp.getResults()[0].getCandidates() != null) {
                for (com.tencentcloudapi.iai.v20200303.models.Candidate c
                        : resp.getResults()[0].getCandidates()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("personId", c.getPersonId());
                    item.put("personName", c.getPersonName());
                    item.put("confidence", c.getScore());
                    candidates.add(item);
                }
            }
            return candidates;
        } catch (TencentCloudSDKException e) {
            if (e.getMessage() != null && e.getMessage().contains("SearchFace.NoFace")) {
                throw new BusinessException("照片中未检测到人脸，请更换清晰正脸照片");
            }
            throw wrap(e);
        }
    }

    /**
     * 1:1 人脸比对：计算两张照片的相似度
     *
     * @return 相似度分数（0~100）
     */
    public Double compareFace(String imageBase64A, String imageBase64B) {
        if (!StringUtils.hasText(imageBase64A) || !StringUtils.hasText(imageBase64B)) {
            throw new BusinessException("请上传两张待比对的人脸照片");
        }
        IaiClient client = buildClient();
        try {
            CompareFaceRequest req = new CompareFaceRequest();
            req.setImageA(imageBase64A);
            req.setImageB(imageBase64B);
            CompareFaceResponse resp = client.CompareFace(req);
            return resp.getScore() == null ? 0D : resp.getScore().doubleValue();
        } catch (TencentCloudSDKException e) {
            if (e.getMessage() != null && e.getMessage().contains("CompareFace.NoFace")) {
                throw new BusinessException("任一照片中未检测到人脸，请更换清晰正脸照片");
            }
            throw wrap(e);
        }
    }

    /**
     * 人脸检测分析（人脸测试）：年龄、性别、表情、眼镜、口罩、质量分等
     *
     * @return 检测结果列表，每个元素对应照片中的一张人脸
     */
    public List<Map<String, Object>> detectFace(String imageBase64) {
        IaiClient client = buildClient();
        try {
            DetectFaceRequest req = new DetectFaceRequest();
            req.setImage(imageBase64);
            req.setNeedFaceAttributes(1L);
            req.setNeedQualityDetection(1L);
            DetectFaceResponse resp = client.DetectFace(req);
            List<Map<String, Object>> faces = new ArrayList<>();
            if (resp.getFaceInfos() != null) {
                for (com.tencentcloudapi.iai.v20200303.models.FaceInfo info : resp.getFaceInfos()) {
                    Map<String, Object> face = new LinkedHashMap<>();
                    face.put("gender", info.getFaceAttributesInfo() != null
                            && info.getFaceAttributesInfo().getGender() != null
                            && info.getFaceAttributesInfo().getGender() > 50 ? "男" : "女");
                    if (info.getFaceAttributesInfo() != null) {
                        face.put("age", info.getFaceAttributesInfo().getAge());
                        face.put("expression", info.getFaceAttributesInfo().getExpression());
                        face.put("glass", info.getFaceAttributesInfo().getGlass());
                        face.put("mask", info.getFaceAttributesInfo().getMask());
                        face.put("beauty", info.getFaceAttributesInfo().getBeauty());
                    }
                    FaceQualityInfo quality = info.getFaceQualityInfo();
                    if (quality != null) {
                        face.put("score", quality.getScore());
                        face.put("sharpness", quality.getSharpness());
                        face.put("brightness", quality.getBrightness());
                        if (quality.getCompleteness() != null) {
                            // 各五官完整度（0~100），取最低值作为整体完整度
                            com.tencentcloudapi.iai.v20200303.models.FaceQualityCompleteness c =
                                    quality.getCompleteness();
                            long minCompleteness = Math.min(
                                    Math.min(Math.min(c.getEye() == null ? 0 : c.getEye(),
                                            c.getEyebrow() == null ? 0 : c.getEyebrow()),
                                            Math.min(c.getNose() == null ? 0 : c.getNose(),
                                                    c.getCheek() == null ? 0 : c.getCheek())),
                                    Math.min(c.getMouth() == null ? 0 : c.getMouth(),
                                            c.getChin() == null ? 0 : c.getChin()));
                            face.put("completeness", minCompleteness);
                        }
                    }
                    // 人脸框坐标直接挂在 FaceInfo 上（新版 SDK 结构）
                    Map<String, Object> rect = new LinkedHashMap<>();
                    rect.put("x", info.getX());
                    rect.put("y", info.getY());
                    rect.put("width", info.getWidth());
                    rect.put("height", info.getHeight());
                    face.put("faceRect", rect);
                    faces.add(face);
                }
            }
            return faces;
        } catch (TencentCloudSDKException e) {
            if (e.getMessage() != null && e.getMessage().contains("InvalidParameterValue.FaceNotFound")) {
                throw new BusinessException("照片中未检测到人脸，请更换清晰正脸照片");
            }
            throw wrap(e);
        }
    }

    /** SDK 异常收敛为中文业务异常 */
    private BusinessException wrap(TencentCloudSDKException e) {
        log.error("腾讯云人脸识别调用失败：code={}，msg={}", e.getErrorCode(), e.getMessage());
        String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
        if (msg.contains("AuthFailure.SignatureFailure") || msg.contains("AuthFailure.SecretIdNotFound")) {
            return new BusinessException("腾讯云密钥校验失败，请检查 tencent.face.secret-id / secret-key 是否正确");
        }
        if (msg.contains("UnauthorizedOperation")) {
            return new BusinessException("该密钥未开通人脸识别（IAI）服务，请在腾讯云控制台开通");
        }
        if (msg.contains("ResourceUnavailable.Freeze") || msg.contains("BalanceInsufficient")) {
            return new BusinessException("腾讯云账户欠费或资源被冻结，请检查账户状态");
        }
        return new BusinessException("人脸识别服务调用失败：" + msg);
    }
}
