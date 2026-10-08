package com.ikun.runner;

import com.ikun.entity.SysUser;
import com.ikun.mapper.SysUserMapper;
import com.ikun.util.SensitiveUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史数据加密迁移器
 *
 * <p>加密功能上线前，数据库中已有大量明文手机号。本迁移器在应用启动时
 * 扫描 {@code sys_user} 表，把仍是明文的手机号加密为 AES-256-GCM 密文，
 * 并回填 SHA-256 摘要列 {@code phone_hash}。</p>
 *
 * <p>幂等设计：只有符合手机号格式的记录才会被处理，已加密的记录
 * （读取时返回 Base64 密文，不满足手机号格式）会被自动跳过，
 * 因此重复启动不会产生数据变更。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataEncryptionMigrationRunner implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 实体配置了 autoResultMap，读取时已自动解密；历史明文会原样返回
            List<SysUser> users = sysUserMapper.selectList(null);
            int migrated = 0;

            for (SysUser user : users) {
                String phone = user.getPhone();
                if (!SensitiveUtil.isValidPhone(phone)) {
                    // 空值 / 密文 / 非法数据，跳过
                    continue;
                }

                SysUser update = new SysUser();
                update.setId(user.getId());
                // 写入时由 TypeHandler 自动加密
                update.setPhone(phone.trim());
                update.setPhoneHash(SensitiveUtil.phoneHash(phone));
                sysUserMapper.updateById(update);
                migrated++;
            }

            if (migrated > 0) {
                log.info("敏感数据加密迁移完成：共加密 {} 条明文手机号，并回填 SHA-256 摘要", migrated);
            }
        } catch (Exception e) {
            // 迁移失败不阻断应用启动，避免影响正常业务
            log.error("敏感数据加密迁移失败，请检查数据库与加密配置", e);
        }
    }
}
