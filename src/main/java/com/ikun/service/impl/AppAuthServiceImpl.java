package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.common.AppUserContext;
import com.ikun.common.BusinessException;
import com.ikun.common.ResultCode;
import com.ikun.dto.AppLoginDTO;
import com.ikun.dto.AppRegisterDTO;
import com.ikun.entity.Tourist;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.AppAuthService;
import com.ikun.service.TokenBlacklistService;
import com.ikun.util.JwtUtil;
import com.ikun.util.PasswordUtil;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.AppLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 游客小程序认证服务实现
 *
 * <p>账号体系与后台账号完全隔离：密码独立存储、Token 类型为 {@code TOURIST}，
 * 即使两端共用同一签名密钥也不会互相串用。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthServiceImpl implements AppAuthService {

    private static final DateTimeFormatter NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TouristMapper touristMapper;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppLoginVO login(AppLoginDTO dto) {
        String phone = dto.getPhone().trim();
        String phoneHash = SensitiveUtil.phoneHash(phone);

        // 优先按摘要匹配；历史数据没有摘要列时退回明文手机号，保证老账号仍能登录
        Tourist tourist = touristMapper.selectOne(Wrappers.<Tourist>lambdaQuery()
                .and(w -> w.eq(Tourist::getPhoneHash, phoneHash).or().eq(Tourist::getPhone, phone))
                .last("LIMIT 1"));
        if (tourist == null) {
            throw new BusinessException("账号不存在，请先注册");
        }
        if (tourist.getStatus() != null && tourist.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        applyPasswordCheck(tourist, phone, dto.getPassword());
        backfillPhoneHash(tourist, phoneHash);

        String token = jwtUtil.createTouristToken(tourist.getId(), tourist.getTouristNo(),
                tourist.getRegisterScenicId());
        log.info("游客登录成功：touristNo={}，phone={}", tourist.getTouristNo(), SensitiveUtil.maskPhone(phone));
        return buildLoginVO(tourist, token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(AppRegisterDTO dto) {
        String phone = dto.getPhone().trim();
        String phoneHash = SensitiveUtil.phoneHash(phone);

        Long exists = touristMapper.selectCount(Wrappers.<Tourist>lambdaQuery()
                .and(w -> w.eq(Tourist::getPhoneHash, phoneHash).or().eq(Tourist::getPhone, phone)));
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }

        boolean hasIdCard = StringUtils.hasText(dto.getIdCard());
        Tourist tourist = new Tourist();
        tourist.setTouristNo(generateTouristNo());
        tourist.setPhone(phone);
        tourist.setPhoneHash(phoneHash);
        tourist.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        tourist.setRealName(StringUtils.hasText(dto.getRealName())
                ? dto.getRealName().trim()
                : "游客" + phone.substring(phone.length() - 4));
        tourist.setIdCard(hasIdCard ? dto.getIdCard().trim() : null);
        tourist.setRealNameStatus(hasIdCard ? 1 : 0);
        tourist.setGender(0);
        tourist.setSource("MINI_PROGRAM");
        tourist.setMemberLevel("NORMAL");
        tourist.setPoints(0);
        tourist.setIsBlacklist(0);
        tourist.setStatus(1);
        tourist.setRegisterScenicId(dto.getScenicId());
        touristMapper.insert(tourist);

        log.info("游客注册成功：touristNo={}，phone={}", tourist.getTouristNo(), SensitiveUtil.maskPhone(phone));
    }

    @Override
    public void logout() {
        String token = AppUserContext.getToken();
        if (!StringUtils.hasText(token)) {
            return;
        }
        // 与后台一致的登出语义：把 Token 的 jti 写入 Redis 黑名单，TTL 与剩余有效期一致
        tokenBlacklistService.blacklist(jwtUtil.getJti(token), jwtUtil.getExpiration(token));
        log.info("游客退出登录：touristNo={}", AppUserContext.getTouristNo());
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 校验密码。
     *
     * <p>种子数据与早期账号可能没有密码列（NULL），此时按「初始密码 = 手机号后六位」
     * 放行并顺手写入 BCrypt 哈希，避免历史账号因缺少密码而永久无法登录。</p>
     */
    private void applyPasswordCheck(Tourist tourist, String phone, String rawPassword) {
        if (!StringUtils.hasText(tourist.getPassword())) {
            if (!SensitiveUtil.defaultPassword(phone).equals(rawPassword)) {
                throw new BusinessException(ResultCode.LOGIN_ERROR);
            }
            Tourist upgrade = new Tourist();
            upgrade.setId(tourist.getId());
            upgrade.setPassword(PasswordUtil.encrypt(rawPassword));
            touristMapper.updateById(upgrade);
            tourist.setPassword(upgrade.getPassword());
            return;
        }
        if (!PasswordUtil.matches(rawPassword, tourist.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }
        // 历史 MD5 密码在登录成功、明文可用的此刻平滑升级为 BCrypt
        if (PasswordUtil.isLegacyMd5(tourist.getPassword())) {
            Tourist upgrade = new Tourist();
            upgrade.setId(tourist.getId());
            upgrade.setPassword(PasswordUtil.encrypt(rawPassword));
            touristMapper.updateById(upgrade);
            tourist.setPassword(upgrade.getPassword());
        }
    }

    /** 老数据没有 phone_hash 时补写一次，让后续登录稳定走摘要检索 */
    private void backfillPhoneHash(Tourist tourist, String phoneHash) {
        if (StringUtils.hasText(tourist.getPhoneHash()) || !StringUtils.hasText(phoneHash)) {
            return;
        }
        Tourist update = new Tourist();
        update.setId(tourist.getId());
        update.setPhoneHash(phoneHash);
        touristMapper.updateById(update);
        tourist.setPhoneHash(phoneHash);
    }

    private AppLoginVO buildLoginVO(Tourist tourist, String token) {
        AppLoginVO vo = new AppLoginVO();
        vo.setToken(token);
        vo.setTokenPrefix(tokenPrefix);
        Date expiration = jwtUtil.getExpiration(token);
        vo.setExpiresIn(expiration == null ? null
                : Math.max((expiration.getTime() - System.currentTimeMillis()) / 1000, 0));
        vo.setTouristId(tourist.getId());
        vo.setTouristNo(tourist.getTouristNo());
        vo.setRealName(tourist.getRealName());
        vo.setPhone(SensitiveUtil.maskPhone(tourist.getPhone()));
        vo.setMemberLevel(tourist.getMemberLevel());
        vo.setPoints(tourist.getPoints());
        vo.setAvatar(tourist.getAvatar());
        vo.setRealNameStatus(tourist.getRealNameStatus());
        return vo;
    }

    /** 游客编号：T + 日期 + 当日序号，如 T202509100001 */
    private String generateTouristNo() {
        Long total = touristMapper.selectCount(null);
        long seq = (total == null ? 0L : total) + 1L;
        return "T" + LocalDate.now().format(NO_DATE_FORMAT) + String.format("%04d", seq);
    }
}
