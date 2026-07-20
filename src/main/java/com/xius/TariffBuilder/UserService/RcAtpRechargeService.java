package com.xius.TariffBuilder.UserService;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RcAtpRechargeService {

        private static final Logger logger = LoggerFactory.getLogger(RcAtpRechargeService.class);

        @Qualifier("oracleJdbcTemplate")
        private final JdbcTemplate jdbcTemplate;

        private final SeriesGeneratorService seriesGeneratorService;

        RcAtpRechargeService(JdbcTemplate jdbcTemplate, SeriesGeneratorService seriesGeneratorService) {
                this.jdbcTemplate = jdbcTemplate;
                this.seriesGeneratorService = seriesGeneratorService;
        }

        /**
         * Creates ONE Recharge Product (RC) PER RCATP, i.e. numberOfRcs ==
         * numberOfRcAtps. Each RC gets its own unique RC_CODE series
         * (tariffPackageName_RC1, _RC2, ...) and its LOW_VALUE / HIGH_VALUE is
         * set from that RCATP's MRP.
         *
         * @param rcAtps each map must contain "atpId" (Long) and "mrp" (Number/String, optional)
         */
        public void createRcForRcAtps(
                        Long tariffPackageId,
                        String tariffPackageName,
                        Long networkId,
                        List<Map<String, Object>> rcAtps) {

                if (rcAtps == null || rcAtps.isEmpty()) {
                        logger.info("No RCATP ids found. Skipping RC creation.");
                        return;
                }

                // Resolve the starting RC series number ONCE, then increment locally so
                // every RC created in this batch gets a unique, sequential RC_CODE.
                int rcSuffixCounter = seriesGeneratorService.resolveNextRcSuffixNumber();

                for (Map<String, Object> rcAtp : rcAtps) {

                        Long atpId = toLong(rcAtp.get("atpId"));
                        Double mrp = toDouble(rcAtp.get("mrp"));

                        if (atpId == null) {
                                logger.warn("Skipping RC creation, atpId missing in entry={}", rcAtp);
                                continue;
                        }

                        createSingleRc(tariffPackageId, tariffPackageName, networkId, atpId, mrp, rcSuffixCounter++);
                }

                logger.info(
                                "RC creation completed. tariffPackageId={}, totalRcsCreated={}",
                                tariffPackageId,
                                rcAtps.size());
        }

        /**
         * Creates exactly ONE Recharge Product for ONE RCATP, resolving a fresh
         * unique RC series number for it. Used when a single new RCATP is added
         * to an existing Tariff Package (update flow).
         */
        public Long createSingleRc(
                        Long tariffPackageId,
                        String tariffPackageName,
                        Long networkId,
                        Long atpId,
                        Double mrp) {

                int rcSuffixNumber = seriesGeneratorService.resolveNextRcSuffixNumber();
                return createSingleRc(tariffPackageId, tariffPackageName, networkId, atpId, mrp, rcSuffixNumber);
        }

        private Long createSingleRc(
                        Long tariffPackageId,
                        String tariffPackageName,
                        Long networkId,
                        Long atpId,
                        Double mrp,
                        int rcSuffixNumber) {

                Long rcId = generateRcId();
                String rcCode = tariffPackageName + "_RC" + rcSuffixNumber;

                createRechargeProduct(
                                rcId,
                                rcCode,
                                tariffPackageName,
                                tariffPackageId,
                                networkId,
                                mrp);

                insertBalanceType(rcId, networkId);

                mapRcAtps(rcId, networkId, List.of(atpId));

                insertDefaultSubscriberCategory(rcId, networkId);

                insertDefaultChannel(rcId, networkId);

                logger.info(
                                "RC created rcId={}, rcCode={}, tariffPackageId={}, atpId={}, mrp={}",
                                rcId,
                                rcCode,
                                tariffPackageId,
                                atpId,
                                mrp);

                return rcId;
        }

        /**
         * Generate next RC ID
         */
        private Long generateRcId() {

                Long rcId = jdbcTemplate.queryForObject(
                                """
                                                SELECT NVL(MAX(TO_NUMBER(RC_ID)),0) + 1
                                                FROM CS_RECHARGE_PRODUCTS
                                                """,
                                Long.class);

                logger.info("Generated RC_ID={}", rcId);

                return rcId;
        }

        /**
         * Insert into CS_RECHARGE_PRODUCTS. LOW_VALUE / HIGH_VALUE are both set to
         * the RCATP's MRP (falls back to 1 if no/invalid MRP was supplied, to
         * preserve prior behaviour).
         */
        private void createRechargeProduct(
                        Long rcId,
                        String rcCode,
                        String tariffPackageName,
                        Long tariffPackageId,
                        Long networkId,
                        Double mrp) {

                double mrpValue = (mrp != null && mrp > 0) ? mrp : 1;

                jdbcTemplate.update(
                                """
                                                INSERT INTO CS_RECHARGE_PRODUCTS
                                                                (
                                                                    RC_ID,
                                                                    RC_CODE,
                                                                    RC_CATEGORY,
                                                                    START_DATE,
                                                                    END_DATE,
                                                                    LOW_VALUE,
                                                                    HIGH_VALUE,
                                                                    RECHARGE_METHOD,
                                                                    TARIFF_PACKAGE_ID,
                                                                    NETWORK_ID,
                                                                    ADDITIONAL_CHARGES,
                                                                    RECHARGE_TYPE,
                                                                    DESCRIPTION
                                                                )
                                                                VALUES
                                                                (
                                                                    ?,
                                                                    ?,
                                                                    2,
                                                                    TRUNC(SYSDATE + 1),
                                                                    ADD_MONTHS(TRUNC(SYSDATE + 1), 24),
                                                                    ?,
                                                                    ?,
                                                                    NULL,
                                                                    ?,
                                                                    ?,
                                                                    0,
                                                                    'N',
                                                                    ?
                                                                )
                                                                """,
                                rcId,
                                rcCode,
                                mrpValue,
                                mrpValue,
                                tariffPackageId,
                                networkId,
                                rcCode);

                logger.info(
                                "Inserted CS_RECHARGE_PRODUCTS rcId={}, rcCode={}, mrp={}",
                                rcId,
                                rcCode,
                                mrpValue);
        }

        /**
         * Insert into CS_RC_PRODUCT_ATP_MAP
         */
        private void mapRcAtps(
                        Long rcId,
                        Long networkId,
                        List<Long> rcAtpIds) {

                for (Long atpId : rcAtpIds) {

                        jdbcTemplate.update(
                                        """
                                                        INSERT INTO CS_RC_PRODUCT_ATP_MAP
                                                        (
                                                            RC_ID,
                                                            ATP_ID,
                                                            ADD_DEL_FLAG,
                                                            NETWORK_ID
                                                        )
                                                        VALUES
                                                        (
                                                            ?,
                                                            ?,
                                                            'A',
                                                            ?
                                                        )
                                                        """,
                                        rcId,
                                        atpId,
                                        networkId);

                        logger.info(
                                        "Inserted CS_RC_PRODUCT_ATP_MAP rcId={}, atpId={}",
                                        rcId,
                                        atpId);
                }
        }

        /**
         * Insert default subscriber category = 1
         */
        private void insertDefaultSubscriberCategory(
                        Long rcId,
                        Long networkId) {

                jdbcTemplate.update(
                                """
                                                INSERT INTO CS_RC_PRODUCT_SUB_CATEGORY_MAP
                                                (
                                                    NETWORK_ID,
                                                    RC_ID,
                                                    SUBS_CATEGORY_ID
                                                )
                                                VALUES
                                                (
                                                    ?,
                                                    ?,
                                                    1
                                                )
                                                """,
                                networkId,
                                rcId);

                logger.info(
                                "Inserted CS_RC_PRODUCT_SUB_CATEGORY_MAP rcId={}",
                                rcId);
        }

        /**
         * Insert default channel = Portal
         */
        private void insertDefaultChannel(Long rcId, Long networkId) {

    List<String> channels = List.of(
        "CIERTO",
        "DEALER",
        "MSPAPIGW",
        "Portal",
        "CORPORATE",
        "IVR",
        "OTHER",
        "USSD"
    );

    String sql = """
        INSERT INTO CS_RC_PRODUCT_CHANNEL_MAP
        (
            NETWORK_ID,
            RC_ID,
            CHANNEL_ID
        )
        VALUES
        (
            ?,
            ?,
            ?
        )
        """;

    for (String channel : channels) {
        jdbcTemplate.update(sql, networkId, rcId, channel);

        logger.info(
            "Inserted CS_RC_PRODUCT_CHANNEL_MAP rcId={}, channel={}",
            rcId,
            channel);
    }
}

        private void insertBalanceType(
                        Long rcId,
                        Long networkId) {

                jdbcTemplate.update(
                                """
                                                INSERT INTO CS_RC_PRODUCT_BALTYPE_MAP
                                                (
                                                    RC_ID,
                                                    BALANCE_ID,
                                                    BALANCE_AMOUNT,
                                                    BALANCE_VALIDITY,
                                                    NETWORK_ID
                                                )
                                                VALUES
                                                (
                                                    ?,
                                                    1,
                                                    NULL,
                                                    0,
                                                    ?
                                                )
                                                """,
                                rcId,
                                networkId);

                logger.info(
                                "Inserted CS_RC_PRODUCT_BALTYPE_MAP rcId={}, balanceId=1",
                                rcId);
        }


        // =====================================================================
        // LOOKUP / UPDATE / DELETE — used by the update (sync) flow, where
        // RC:RCATP is now strictly 1:1.
        // =====================================================================

        /**
         * Finds the RC_ID for one specific RCATP (ATP_ID). Since RC:RCATP is now
         * 1:1, this replaces the old "one shared RC per tariff package" lookup.
         * Returns null if this RCATP has no active RC mapping.
         */
        public Long findRcIdByAtpId(Long atpId, Long networkId) {

                return jdbcTemplate.query("""
                        SELECT RC_ID
                        FROM CS_RC_PRODUCT_ATP_MAP
                        WHERE ATP_ID = ?
                          AND NETWORK_ID = ?
                          AND ADD_DEL_FLAG = 'A'
                        FETCH FIRST 1 ROWS ONLY
                        """, rs -> rs.next() ? rs.getLong("RC_ID") : null, atpId, networkId);
        }

        /**
         * Updates LOW_VALUE / HIGH_VALUE (MRP) on an existing RC. Used when an
         * RCATP is edited in place via the update/modify UI.
         */
        public void updateRcMrp(Long rcId, Double mrp, Long networkId) {

                double mrpValue = (mrp != null && mrp > 0) ? mrp : 1;

                int rows = jdbcTemplate.update("""
                        UPDATE CS_RECHARGE_PRODUCTS
                           SET LOW_VALUE  = ?,
                               HIGH_VALUE = ?
                         WHERE RC_ID = ?
                           AND NETWORK_ID = ?
                        """, mrpValue, mrpValue, rcId, networkId);

                logger.info("Updated CS_RECHARGE_PRODUCTS MRP rcId={} mrp={} rowsAffected={}", rcId, mrpValue, rows);
        }

        /**
         * Deletes an RC and all of its child mappings. Used when the single
         * RCATP that "owns" this RC is removed from the Tariff Package.
         */
        public void deleteRc(Long rcId, Long networkId) {

                jdbcTemplate.update(
                        "DELETE FROM CS_RC_PRODUCT_ATP_MAP WHERE RC_ID = ? AND NETWORK_ID = ?", rcId, networkId);
                jdbcTemplate.update(
                        "DELETE FROM CS_RC_PRODUCT_SUB_CATEGORY_MAP WHERE RC_ID = ? AND NETWORK_ID = ?", rcId,
                        networkId);
                jdbcTemplate.update(
                        "DELETE FROM CS_RC_PRODUCT_CHANNEL_MAP WHERE RC_ID = ? AND NETWORK_ID = ?", rcId, networkId);
                jdbcTemplate.update(
                        "DELETE FROM CS_RC_PRODUCT_BALTYPE_MAP WHERE RC_ID = ? AND NETWORK_ID = ?", rcId, networkId);
                jdbcTemplate.update(
                        "DELETE FROM CS_RECHARGE_PRODUCTS WHERE RC_ID = ? AND NETWORK_ID = ?", rcId, networkId);

                logger.info("Deleted RC and all child mappings rcId={} networkId={}", rcId, networkId);
        }

        // =====================================================================
        // Small parsing helpers
        // =====================================================================

        private Long toLong(Object value) {
                if (value == null)
                        return null;
                if (value instanceof Number n)
                        return n.longValue();
                String s = value.toString().trim();
                if (s.isEmpty())
                        return null;
                try {
                        return Long.valueOf(s);
                } catch (NumberFormatException e) {
                        return null;
                }
        }

        private Double toDouble(Object value) {
                if (value == null)
                        return null;
                if (value instanceof Number n)
                        return n.doubleValue();
                String s = value.toString().trim();
                if (s.isEmpty())
                        return null;
                try {
                        return Double.valueOf(s);
                } catch (NumberFormatException e) {
                        return null;
                }
        }
}
