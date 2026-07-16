 package com.xius.TariffBuilder.UserService;

import java.util.List;

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

        RcAtpRechargeService(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        public void createRcForRcAtps(
                        Long tariffPackageId,
                        String tariffPackageName,
                        Long networkId,
                        List<Long> rcAtpIds) {

                if (rcAtpIds == null || rcAtpIds.isEmpty()) {
                        logger.info("No RCATP ids found. Skipping RC creation.");
                        return;
                }

                Long rcId = generateRcId();

                createRechargeProduct(
                                rcId,
                                tariffPackageName,
                                tariffPackageId,
                                networkId);

                insertBalanceType(
                                rcId,
                                networkId);

                mapRcAtps(
                                rcId,
                                networkId,
                                rcAtpIds);

                insertDefaultSubscriberCategory(
                                rcId,
                                networkId);

                insertDefaultChannel(
                                rcId,
                                networkId);

                logger.info(
                                "RC creation completed. rcId={}, tariffPackageId={}, totalRcAtps={}",
                                rcId,
                                tariffPackageId,
                                rcAtpIds.size());
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
         * Insert into CS_RECHARGE_PRODUCTS
         */
        private void createRechargeProduct(
                        Long rcId,
                        String tariffPackageName,
                        Long tariffPackageId,
                        Long networkId) {

                // String rcCode = "RC" + rcId;

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
                                                                    1,
                                                                    1,
                                                                    NULL,
                                                                    ?,
                                                                    ?,
                                                                    0,
                                                                    'N',
                                                                    ?
                                                                )
                                                                """,
                                rcId,
                                tariffPackageName,
                                tariffPackageId,
                                networkId,
                                tariffPackageName);

                logger.info(
                                "Inserted CS_RECHARGE_PRODUCTS rcId={}, rcCode={}",
                                rcId,
                                tariffPackageName);
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
        private void insertDefaultChannel(
                        Long rcId,
                        Long networkId) {

                jdbcTemplate.update(
                                """
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
                                                    'Portal'
                                                )
                                                """,
                                networkId,
                                rcId);

                logger.info(
                                "Inserted CS_RC_PRODUCT_CHANNEL_MAP rcId={}, channel=Portal",
                                rcId);
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
                                                    10,
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
    // ADD THESE METHODS TO YOUR EXISTING RcAtpRechargeService.java
    // (paste inside the class, e.g. just above the closing brace)
    // =====================================================================

    /**
     * Finds the RC_ID already associated with a Tariff Package.
     * Returns null if no Recharge Product has been created for it yet
     * (e.g. it currently has zero RCATPs).
     */
    public Long findRcIdByTariffPackageId(Long tariffPackageId, Long networkId) {

        return jdbcTemplate.query("""
                SELECT RC_ID
                FROM CS_RECHARGE_PRODUCTS
                WHERE TARIFF_PACKAGE_ID = ?
                  AND NETWORK_ID = ?
                FETCH FIRST 1 ROWS ONLY
                """, rs -> rs.next() ? rs.getLong("RC_ID") : null, tariffPackageId, networkId);
    }

    /**
     * Adds a single new ATP mapping to an EXISTING Recharge Product.
     * Per requirements: "Do not create a new RC. Do not create new Recharge
     * Product records. Insert only a new ATP mapping into CS_RC_PRODUCT_ATP_MAP."
     */
    public void addAtpMapping(Long rcId, Long atpId, Long networkId) {

        jdbcTemplate.update("""
                INSERT INTO CS_RC_PRODUCT_ATP_MAP
                (RC_ID, ATP_ID, ADD_DEL_FLAG, NETWORK_ID)
                VALUES (?, ?, 'A', ?)
                """, rcId, atpId, networkId);

        logger.info("Inserted CS_RC_PRODUCT_ATP_MAP (update-add) rcId={}, atpId={}", rcId, atpId);
    }

    /**
     * Removes a single ATP mapping when an RCATP is removed from a Tariff
     * Package. CS_RECHARGE_PRODUCTS and its other child tables are left
     * untouched — the Recharge Product remains valid for any RCATPs still
     * mapped to it.
     */
    public void removeAtpMapping(Long rcId, Long atpId, Long networkId) {

        jdbcTemplate.update("""
                DELETE FROM CS_RC_PRODUCT_ATP_MAP
                WHERE RC_ID = ?
                  AND ATP_ID = ?
                  AND NETWORK_ID = ?
                """, rcId, atpId, networkId);

        logger.info("Deleted CS_RC_PRODUCT_ATP_MAP rcId={}, atpId={}", rcId, atpId);
    }
}