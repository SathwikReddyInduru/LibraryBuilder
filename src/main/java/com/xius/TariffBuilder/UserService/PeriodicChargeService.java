package com.xius.TariffBuilder.UserService;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class PeriodicChargeService {

    private static final Logger logger = LoggerFactory.getLogger(PeriodicChargeService.class);

	@Qualifier("oracleJdbcTemplate")
	private final JdbcTemplate jdbcTemplate;

    String query = """
        SELECT CHARGE_ID, CHARGE_DESC, NETWORK_ID, RENTAL_PERIOD, RENTAL_FEE
        FROM CS_RAT_PERIODIC_CHARGE_INFO
        WHERE NETWORK_ID = ?
    """;

    PeriodicChargeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public List<Map<String, Object>> getPeriodicCharges(Long networkId) {
        logger.info("Fetching periodic charges for networkId={}", networkId);
        List<Map<String, Object>> charges = jdbcTemplate.queryForList(query, networkId);
        if (charges.isEmpty()) {
            logger.warn("No periodic charges found for networkId={}", networkId);
            return List.of();
        }
        logger.info("Fetched {} periodic charges for networkId={}", charges.size(), networkId);
        return charges; // Returning all fetched charges
    }

    
}
