package com.xius.TariffBuilder.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.exception.TariffInsertException;

@Service
public class HlrCodeMappingService {

    private static final Logger logger = LoggerFactory.getLogger(HlrCodeMappingService.class);

    @Qualifier("oracleJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    public HlrCodeMappingService(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertHlrCodeMapping(Long networkId,
                                     Long atpId,
                                     Long hlrCode) {

        try {
            jdbcTemplate.update("""
                    INSERT INTO CS_HLRCODE_ATP_MAP
                    (
                        NETWORK_ID,
                        ATP_ID,
                        HLR_CODE,
                        ATP_CATEGORY_BY_OFFER
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    networkId,
                    atpId,
                    hlrCode,
                    "TM");

            logger.info("HLR code mapping inserted successfully. networkId={}, atpId={}, hlrCode={}",
                    networkId, atpId, hlrCode);

        } catch (Exception ex) {
            logger.error("Failed to insert HLR code mapping. networkId={}, atpId={}, hlrCode={}",
                    networkId, atpId, hlrCode, ex);

            throw new TariffInsertException(
                    "INSERT_HLR_CODE_MAPPING",
                    "CS_HLRCODE_ATP_MAP",
                    ex);
        }
    }

    public void updateHlrCodeMapping(Long networkId,
                                     Long atpId,
                                     Long hlrCode) {

        try {
            int rows = jdbcTemplate.update("""
                    UPDATE CS_HLRCODE_ATP_MAP
                       SET HLR_CODE = ?
                     WHERE NETWORK_ID = ?
                       AND ATP_ID = ?
                    """,
                    hlrCode,
                    networkId,
                    atpId);

            logger.info("HLR code mapping updated. networkId={}, atpId={}, hlrCode={}, rowsAffected={}",
                    networkId, atpId, hlrCode, rows);

        } catch (Exception ex) {
            logger.error("Failed to update HLR code mapping. networkId={}, atpId={}, hlrCode={}",
                    networkId, atpId, hlrCode, ex);

            throw new TariffInsertException(
                    "UPDATE_HLR_CODE_MAPPING",
                    "CS_HLRCODE_ATP_MAP",
                    ex);
        }
    }

    public void deleteHlrCodeMapping(Long networkId,
                                     Long atpId) {

        try {
            int rows = jdbcTemplate.update("""
                    DELETE FROM CS_HLRCODE_ATP_MAP
                     WHERE NETWORK_ID = ?
                       AND ATP_ID = ?
                    """,
                    networkId,
                    atpId);

            logger.info("HLR code mapping deleted. networkId={}, atpId={}, rowsAffected={}",
                    networkId, atpId, rows);

        } catch (Exception ex) {
            logger.error("Failed to delete HLR code mapping. networkId={}, atpId={}",
                    networkId, atpId, ex);

            throw new TariffInsertException(
                    "DELETE_HLR_CODE_MAPPING",
                    "CS_HLRCODE_ATP_MAP",
                    ex);
        }
    }
}