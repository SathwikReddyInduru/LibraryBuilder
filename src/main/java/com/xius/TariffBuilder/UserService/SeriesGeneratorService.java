package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import com.xius.TariffBuilder.Entity.SaveConfigDao;
import com.xius.TariffBuilder.util.JsonStorage;

@Service
public class SeriesGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(TariffApprovalService.class);
    private final JdbcTemplate jdbcTemplate;
    SeriesGeneratorService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, SaveConfigDao saveConfigDao, JsonStorage jsonStorage) {
        this.jdbcTemplate = jdbcTemplate;
    }
    

    public int resolveNextTpSuffixNumber() {
		List<String> existing = jdbcTemplate.queryForList("""
				select SERVICE_PACKAGE_DESC
				from CS_RAT_SERVICE_PACKAGE
				where REGEXP_LIKE(SERVICE_PACKAGE_DESC, '_TP[0-9]+$')
				""", String.class);

		int max = 0;
		for (String desc : existing) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("_TP(\\d+)$").matcher(desc);
			if (m.find()) {
				try {
					int n = Integer.parseInt(m.group(1));
					if (n > max)
						max = n;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		logger.info("resolveNextTpSuffixNumber existingMax={} nextNumber={}", max, max + 1);
		return max + 1;
	}


    public int resolveNextAtpSuffixNumber() {
		List<String> existing = jdbcTemplate.queryForList("""
				select SERVICE_PACKAGE_DESC
				from CS_RAT_SERVICE_PACKAGE
				where ADD_PACK_YN = 'Y'
				  and REGEXP_LIKE(SERVICE_PACKAGE_DESC, '_ATP[0-9]+$')
				""", String.class);

		int max = 0;
		for (String desc : existing) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("_ATP(\\d+)$").matcher(desc);
			if (m.find()) {
				try {
					int n = Integer.parseInt(m.group(1));
					if (n > max)
						max = n;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		logger.info("resolveNextAtpSuffixNumber existingMax={} nextNumber={}", max, max + 1);
		return max + 1;
	}


    public int resolveNextRcSuffixNumber() {
		List<String> existing = jdbcTemplate.queryForList("""
				select RC_CODE
				from CS_RECHARGE_PRODUCTS
				where REGEXP_LIKE(RC_CODE, '_RC[0-9]+$')
				""", String.class);

		int max = 0;
		for (String code : existing) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("_RC(\\d+)$").matcher(code);
			if (m.find()) {
				try {
					int n = Integer.parseInt(m.group(1));
					if (n > max)
						max = n;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		logger.info("resolveNextRcSuffixNumber existingMax={} nextNumber={}", max, max + 1);
		return max + 1;
	}
}