package com.xius.TariffBuilder.UserService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.xius.TariffBuilder.Dto.BulkRateUpdateRequest;

@Service
public class BulkService {

    private static final Logger logger = LoggerFactory.getLogger(BulkService.class);

    private final JdbcTemplate jdbcTemplate;

    BulkService(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String updateBulkRate(BulkRateUpdateRequest request) {

        // ---- Validations ----
        if (request.getNetworkId() == null || request.getNetworkId() <= 0) {
            throw new IllegalArgumentException("Invalid networkId");
        }

        if (!"UPDATE".equalsIgnoreCase(request.getFlag())) {
            throw new IllegalArgumentException("Flag should be UPDATE");
        }

        if (request.getServiceIds() == null || request.getServiceIds().isEmpty()) {
            throw new IllegalArgumentException("serviceIds must not be null or empty");
        }

        if (request.getNewRates() == null
                || request.getNewRates().size() != request.getServiceIds().size()) {
            throw new IllegalArgumentException("newRates must be provided and match serviceIds size");
        }

        if (request.getMonthYear() == null || request.getMonthYear().isBlank()) {
            throw new IllegalArgumentException("monthYear must not be null or blank");
        }

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }

        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMyyyy"));

        for (int i = 0; i < request.getServiceIds().size(); i++) {

            Long servicePackageId = request.getServiceIds().get(i);
            BigDecimal newRate = request.getNewRates().get(i);

            if (servicePackageId == null || servicePackageId <= 0) {
                throw new IllegalArgumentException("Invalid servicePackageId at index " + i);
            }
            if (newRate == null || newRate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Invalid newRate at index " + i);
            }

            logger.info("Updating Service Package {}", servicePackageId);

            // ---- Step 1: existence check for this specific month ----
            String countSql = """
                    SELECT COUNT(1)
                    FROM CS_RAT_SERVICE_PACKAGE A,
                         CS_RAT_PERD_CHARGE_INFO_ATP_MONTH B,
                         CS_RAT_TARIFF_SERVICE_PACK_MAP C
                    WHERE A.SERVICE_PACKAGE_ID = C.SERVICE_PACKAGE_ID
                      AND B.CHARGE_ID = C.CHARGE_ID
                      AND TARIFF_PLAN_TYPE = 'AATP'
                      AND C.NETWORK_ID = B.NETWORK_ID
                      AND C.NETWORK_ID = ?
                      AND A.SERVICE_PACKAGE_ID = ?
                      AND TO_CHAR(B.START_DATE,'MMYYYY') = ?
                    """;

            Integer count = jdbcTemplate.queryForObject(
                    countSql,
                    new Object[] { request.getNetworkId(), servicePackageId, request.getMonthYear() },
                    Integer.class);

            if (count == null || count == 0) {
                logger.error("No record found for networkId={}, servicePackageId={}, monthYear={}",
                        request.getNetworkId(), servicePackageId, request.getMonthYear());

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GIVEN SERVICE_PACKAGE_ID NOT FOUND");
            }

            // ---- Step 2: fetch charge_id (VARCHAR2, e.g. "DATA_DAILY_1GB_PC") ----
            String chargeSql = """
                    SELECT B.CHARGE_ID
                    FROM CS_RAT_SERVICE_PACKAGE A,
                         CS_RAT_PERIODIC_CHARGE_INFO B,
                         CS_RAT_TARIFF_SERVICE_PACK_MAP C
                    WHERE A.SERVICE_PACKAGE_ID = C.SERVICE_PACKAGE_ID
                      AND B.CHARGE_ID = C.CHARGE_ID
                      AND TARIFF_PLAN_TYPE = 'AATP'
                      AND C.NETWORK_ID = B.NETWORK_ID
                      AND C.NETWORK_ID = ?
                      AND A.SERVICE_PACKAGE_ID = ?
                    """;

            List<String> chargeIds = jdbcTemplate.query(
                    chargeSql,
                    new Object[] { request.getNetworkId(), servicePackageId },
                    (rs, rowNum) -> rs.getString("CHARGE_ID"));

            if (chargeIds.isEmpty()) {
                logger.error("No Charge ID found for networkId={}, servicePackageId={}",
                        request.getNetworkId(), servicePackageId);

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GIVEN SERVICE_PACKAGE_ID NOT FOUND");
            }

            String chargeId = chargeIds.get(0);

            // ---- Step 3: update current-month base table only if monthYear == current month ----
            if (currentMonth.equals(request.getMonthYear())) {

                String updatePeriodic = """
                        UPDATE CS_RAT_PERIODIC_CHARGE_INFO
                           SET ACTIVATION_FEE = ?,
                               SALES_FEE = ?
                         WHERE CHARGE_ID = ?
                           AND NETWORK_ID = ?
                        """;

                jdbcTemplate.update(
                        updatePeriodic,
                        newRate,
                        newRate,
                        chargeId,
                        request.getNetworkId());
            }

            // ---- Step 4: always update the ATP month table ----
            String updateMonth = """
                    UPDATE CS_RAT_PERD_CHARGE_INFO_ATP_MONTH
                       SET ACTIVATION_FEE = ?,
                           SALES_FEE = ?,
                           UPDATED_DATE = SYSDATE,
                           UPDATED_BY = ?
                     WHERE CHARGE_ID = ?
                       AND NETWORK_ID = ?
                       AND TO_CHAR(START_DATE,'MMYYYY') = ?
                    """;

            int rows = jdbcTemplate.update(
                    updateMonth,
                    newRate,
                    newRate,
                    request.getUserId(),
                    chargeId,
                    request.getNetworkId(),
                    request.getMonthYear());

            if (rows == 0) {
                logger.error("Update failed - no matching ATP month row for networkId={}, servicePackageId={}, monthYear={}",
                        request.getNetworkId(), servicePackageId, request.getMonthYear());

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "GIVEN SERVICE_PACKAGE_ID NOT FOUND");
            }

            logger.info("Successfully updated servicePackageId={} chargeId={}", servicePackageId, chargeId);
        }

        logger.info("Bulk rate update completed successfully.");

        return "Bulk rate updated successfully.";
    }

}