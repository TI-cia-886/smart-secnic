package com.ikun.handler;

import com.ikun.util.AesUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段自动加解密 TypeHandler（AES-256-GCM）
 *
 * <p>用法：实体字段上标注 {@code @TableField(typeHandler = MybatisEncryptTypeHandler.class)}，
 * 并确保实体类 {@code @TableName(autoResultMap = true)}，即可实现：</p>
 * <ul>
 *   <li>写入数据库时自动加密；</li>
 *   <li>查询结果自动解密。</li>
 * </ul>
 *
 * <p>这样业务代码完全感知不到加密过程，避免在 Service 里散落加解密调用。</p>
 *
 * @author smart-scenic
 */
public class MybatisEncryptTypeHandler extends BaseTypeHandler<String> {

    /** 写入：明文 → 密文 */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, AesUtil.encrypt(parameter));
    }

    /** 读取：密文 → 明文 */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return AesUtil.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return AesUtil.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return AesUtil.decrypt(cs.getString(columnIndex));
    }
}
