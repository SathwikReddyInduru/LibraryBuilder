package com.xius.TariffBuilder.UserService;
 
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
 
import com.xius.TariffBuilder.Dto.UsrPrivilegeDTO;
 
@Repository
public class UserAuthRepository {
 
    private static final Logger logger = LoggerFactory.getLogger(UserAuthRepository.class);
 
@Autowired
@Qualifier("oracleJdbcTemplate")
private JdbcTemplate jdbcTemplate;
 
    /*
     * LOGIN PRIVILEGES
     */
    public List<UsrPrivilegeDTO> getUserPrivileges(
            String networkName,
            String loginId,
            String password) {
 
        logger.info(
                "Fetching user privileges for loginId={} networkName={}",
                loginId,
                networkName);
 
        String sql = """
            SELECT DISTINCT
                   u.NETWORK_ID,
                   n.STATUS_CODE AS NETWORK_STATUS,
                   u.STATUS_CODE AS USER_STATUS,
                   p.PRIVILEGE_ID,
                   p.PRIVILEGE_NAME,
                   p.PRIVILEGE_DESC,
                   p.MODULE_ID
            FROM UMS_MT_USER u
            JOIN GLB_MT_NETWORK n
              ON n.NETWORK_ID = u.NETWORK_ID
            LEFT JOIN UMS_TT_USER_ROLES r
              ON r.LOGIN_ID = u.LOGIN_ID
             AND r.NETWORK_ID = u.NETWORK_ID
            LEFT JOIN UMS_TT_ROLEPROFILE rp
              ON rp.ROLE_ID = r.ROLE_ID
            LEFT JOIN UMS_MT_PRIVILEGE p
              ON p.PRIVILEGE_ID = rp.PRIVILEGE_ID
            WHERE UPPER(n.NETWORK_DISPLAY) = UPPER(?)
              AND u.LOGIN_ID = ?
              AND u.PASSWORD_NAME = ?
            """;
 
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
 
                    UsrPrivilegeDTO dto = new UsrPrivilegeDTO();
 
                    dto.setPrivilegeId(rs.getString("PRIVILEGE_ID"));
                    dto.setPrivilegeName(rs.getString("PRIVILEGE_NAME"));
                    dto.setPrivilegeDesc(rs.getString("PRIVILEGE_DESC"));
                    dto.setModuleId(rs.getString("MODULE_ID"));
 
                    return dto;
                },
                networkName,
                loginId,
                sha1(password));
    }
 
    /*
     * NETWORK ID
     */
    public Long getNetworkId(String networkName) {
 
        logger.info(
                "Fetching networkId for networkName={}",
                networkName);
 
        String sql = """
            SELECT NETWORK_ID
            FROM GLB_MT_NETWORK
            WHERE UPPER(NETWORK_DISPLAY) = UPPER(?)
            """;
 
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                networkName);
    }
 
    /*
     * PASSWORD HASH
     */
    private String sha1(String input) {
 
        try {
 
            MessageDigest md = MessageDigest.getInstance("SHA-1");
 
            byte[] bytes = md.digest(
                    input.getBytes(StandardCharsets.UTF_8));
 
            StringBuilder hex = new StringBuilder();
 
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
 
            return hex.toString();
 
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
 