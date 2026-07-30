package com.xius.TariffBuilder.UserService;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.Dto.AtpRulesDto;


@Service
public class AtpRulesService {

	private static final Logger logger = LoggerFactory.getLogger(ServicePackageService.class);

	@Qualifier("oracleJdbcTemplate")
	private final JdbcTemplate jdbcTemplate;

	AtpRulesService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<AtpRulesDto> getAddOnPackages(Long networkId) {

		logger.info("getAddOnPackages called networkId={}", networkId);

		String sql = """
				SELECT SERVICE_PACKAGE_ID, SERVICE_PACKAGE_DESC
				FROM CS_RAT_SERVICE_PACKAGE
				WHERE NETWORK_ID = ?
				AND ADD_PACK_YN = 'Y'
				AND SERVICE_PACKAGE_ID NOT IN (
				    SELECT PLAN_ID FROM PLAN_SUBSCRIPTION_RULES WHERE NETWORK_ID = ?
				)
				ORDER BY 2
				""";

		List<AtpRulesDto> list = jdbcTemplate.query(sql, new Object[] { networkId, networkId },
				(rs, rowNum) -> new AtpRulesDto(rs.getLong("SERVICE_PACKAGE_ID"),
						rs.getString("SERVICE_PACKAGE_DESC")));

		logger.debug("getAddOnPackages result size={}", list.size());

		return list;
	}

	
	
	
	public List<Map<String, Object>> getServicePackagePlanMapping(Long networkId) {

	    logger.info("getServicePackagePlanMapping called networkId={}", networkId);

	    String sql = """
	            SELECT DISTINCT a.SERVICE_PACKAGE_DESC SERVICE_PACKAGE_DESC, b.PLAN_ID PLAN_ID
	            FROM CS_RAT_SERVICE_PACKAGE a, PLAN_SUBSCRIPTION_RULES b
	            WHERE a.SERVICE_PACKAGE_ID = b.PLAN_ID
	            AND b.NETWORK_ID = ? """;

	    logger.debug("Executing SQL to fetch SERVICE_PACKAGE_DESC and PLAN_ID");

	    List<Map<String, Object>> list = jdbcTemplate.query(sql, new Object[] { networkId },
	            (rs, i) -> {
	                Map<String, Object> row = new HashMap<>();
	                row.put("servicePackageDesc", rs.getString("SERVICE_PACKAGE_DESC"));
	                row.put("planId", rs.getLong("PLAN_ID"));
	                return row;
	            });

	    logger.debug("Query result size={}", list.size());

	    if (list.isEmpty()) {
	        logger.info("No service package/plan mapping found for networkId={}", networkId);
	    }

	    return list;
	}
}

