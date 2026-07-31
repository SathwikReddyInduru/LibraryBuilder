package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.xius.TariffBuilder.Dto.ServicePackageDetailDto;
import com.xius.TariffBuilder.Dto.ServicePackageDto;
import com.xius.TariffBuilder.Dto.ServicePackageDto1;

@Service
public class ServicePackage1 {

	private static final Logger logger = LoggerFactory.getLogger(ServicePackage1.class);
	

	@Qualifier("oracleJdbcTemplate")
	private final JdbcTemplate jdbcTemplate;

	ServicePackage1(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<ServicePackageDto> getServicePackages(Long networkId) {

		if (networkId == null || networkId <= 0) {
			throw new IllegalArgumentException("Invalid networkId");
		}

		String sql = """
				SELECT
				    SERVICE_PACKAGE_ID,
				    SERVICE_PACKAGE_DESC
				FROM CS_RAT_SERVICE_PACKAGE
				WHERE NETWORK_ID = ?
				  AND ADD_PACK_YN = 'Y'
				""";

		try {
			logger.info("Fetching service packages for networkId={}", networkId);

			List<ServicePackageDto> servicePackages = jdbcTemplate.query(sql, new Object[] { networkId },
					(rs, rowNum) -> {
						ServicePackageDto dto = new ServicePackageDto();
						dto.setServicePackageId(rs.getString("SERVICE_PACKAGE_ID"));
						dto.setServicePackageName(rs.getString("SERVICE_PACKAGE_DESC"));
						return dto;
					});

			logger.info("Fetched {} service package(s) for networkId={}", servicePackages.size(), networkId);

			return servicePackages;

		} catch (DataAccessException ex) {
			logger.error("Error fetching service packages for networkId={}", networkId, ex);
			throw ex;
		}
     }
	
	

	public List<ServicePackageDto1> getServicePackages1(Long networkId) {

	    if (networkId == null || networkId <= 0) {
	        throw new IllegalArgumentException("Invalid networkId");
	    }

	    String sql = """
	            SELECT UNIQUE
	                   B.ACTIVATION_FEE,
	                   B.CHARGE_ID,
	                   A.SERVICE_PACKAGE_ID,
	                   A.SERVICE_PACKAGE_DESC
	            FROM CS_RAT_SERVICE_PACKAGE A,
	                 CS_RAT_PERIODIC_CHARGE_INFO B,
	                 CS_RAT_TARIFF_SERVICE_PACK_MAP C,
	                 CS_RAT_TARIFF_PACKAGE D
	            WHERE A.SERVICE_PACKAGE_ID = C.SERVICE_PACKAGE_ID
	              AND B.CHARGE_ID = C.CHARGE_ID
	              AND C.TARIFF_PACKAGE_ID = D.TARIFF_PACKAGE_ID
	              AND TARIFF_PLAN_TYPE = 'AATP'
	              AND C.NETWORK_ID = D.NETWORK_ID
	              AND D.NETWORK_ID = B.NETWORK_ID
	              AND C.NETWORK_ID = ?
	            ORDER BY A.SERVICE_PACKAGE_ID
	            """;

	    try {

	        logger.info("Fetching service packages for networkId={}", networkId);

	        List<ServicePackageDto1> servicePackages = jdbcTemplate.query(
	                sql,
	                new Object[]{networkId},
	                (rs, rowNum) -> {

	                    ServicePackageDto1 dto = new ServicePackageDto1();

	                    dto.setActivationFee(rs.getDouble("ACTIVATION_FEE"));

	                    // change this
	                    dto.setChargeId(rs.getString("CHARGE_ID"));

	                    dto.setServicePackageId(rs.getString("SERVICE_PACKAGE_ID"));
	                    dto.setServicePackageName(rs.getString("SERVICE_PACKAGE_DESC"));

	                    return dto;
	                });

	        logger.info("Fetched {} service package(s) for networkId={}",
	                servicePackages.size(), networkId);

	        return servicePackages;

	    } catch (DataAccessException ex) {

	        logger.error("Error fetching service packages for networkId={}",
	                networkId, ex);

	        throw ex;
	    }
	}

	 public ServicePackageDetailDto getServicePackageDetail(Long networkId, Long servicePackageId, String monthYear) {

	        if (networkId == null || networkId <= 0) {
	            throw new IllegalArgumentException("Invalid networkId");
	        }
	        if (servicePackageId == null || servicePackageId <= 0) {
	            throw new IllegalArgumentException("Invalid servicePackageId");
	        }
	        if (monthYear == null || monthYear.isBlank()) {
	            throw new IllegalArgumentException("Invalid monthYear");
	        }

	        String sql = """
	                SELECT
	                    A.SERVICE_PACKAGE_DESC,
	                    B.ACTIVATION_FEE
	                FROM CS_RAT_SERVICE_PACKAGE A,
	                     CS_RAT_PERIODIC_CHARGE_INFO B,
	                     CS_RAT_PERD_CHARGE_INFO_ATP_MONTH C
	                WHERE A.SERVICE_PACKAGE_ID = C.ATP_ID
	                  AND B.CHARGE_ID = C.CHARGE_ID
	                  AND A.NETWORK_ID = B.NETWORK_ID
	                  AND A.NETWORK_ID = ?
	                  AND A.SERVICE_PACKAGE_ID = ?
	                  AND TO_CHAR(C.START_DATE, 'MMYYYY') = ?
	                """;

	        try {
	            logger.info("Fetching service package detail for networkId={}, servicePackageId={}, monthYear={}",
	                    networkId, servicePackageId, monthYear);

	            ServicePackageDetailDto result = jdbcTemplate.queryForObject(sql,
	                    new Object[] { networkId, servicePackageId, monthYear },
	                    (rs, rowNum) -> {
	                        ServicePackageDetailDto dto = new ServicePackageDetailDto();
	                        dto.setServicePackageDesc(rs.getString("SERVICE_PACKAGE_DESC"));
	                        dto.setActivationFee(rs.getBigDecimal("ACTIVATION_FEE"));
	                        return dto;
	                    });

	            logger.info("Fetched service package detail for networkId={}, servicePackageId={}", networkId, servicePackageId);

	            return result;

	        } catch (EmptyResultDataAccessException ex) {
	            logger.warn("No data found for networkId={}, servicePackageId={}, monthYear={}",
	                    networkId, servicePackageId, monthYear);
	            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NO DATA FOUND");

	        } catch (DataAccessException ex) {
	            logger.error("Error fetching service package detail for networkId={}, servicePackageId={}",
	                    networkId, servicePackageId, ex);
	            throw ex;
	        }
	    }
}