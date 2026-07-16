package com.xius.TariffBuilder.UserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.Dto.DatpBenefitDto;
import com.xius.TariffBuilder.Dto.TariffPackageDetailsDto;

@Service
public class TariffPackageService {

	@Qualifier("oracleJdbcTemplate")
	private final JdbcTemplate jdbcTemplate;

	TariffPackageService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<TariffPackageDetailsDto> getTariffPackageDetails(Integer networkId) {

		String tariffSql = """
										SELECT a.tariff_package_id,
				       a.tariff_package_desc,
				       a.package_type,
					   a.is_corporate_yn,

				       MIN(c.charge_id) AS charge_id,

				       MIN(
				           (SELECT g.activation_fee
				              FROM cs_rat_periodic_charge_info g
				             WHERE g.charge_id = a.charge_id)
				       ) AS activation_fee,

				       MIN(
				           DECODE(
				               (SELECT g.rental_type
				                  FROM cs_rat_periodic_charge_info g
				                 WHERE g.charge_id = c.charge_id),
				               'M','Monthly',
				               'O','Others',
				               'D','Daily',
				               'W','Weekly',
				               'F','Fixed',
				               'U','Unlimited',
				               'Y','Yearly'
				           )
				       ) AS rental_type,

				       MAX(
				           (SELECT g.rental_period
				              FROM cs_rat_periodic_charge_info g
				             WHERE g.charge_id = c.charge_id)
				       ) AS rental_period,

				       /* DATA BENEFIT */
				       CASE
				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'DATA'
				                        THEN f.total_bucket
				                    END
				                ) IS NULL
				           THEN NULL

				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'DATA'
				                        THEN f.total_bucket
				                    END
				                ) >= 999999999
				           THEN 'UNLIMITED'

				           ELSE fn_data_unit_converter(
				                    MAX(
				                        CASE
				                            WHEN f.balance_category = 'DATA'
				                            THEN f.total_bucket
				                        END
				                    )
				                )
				       END AS data_benefit,

				       /* SMS BENEFIT */
				       CASE
				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'SMS'
				                        THEN f.total_bucket
				                    END
				                ) IS NULL
				           THEN NULL

				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'SMS'
				                        THEN f.total_bucket
				                    END
				                ) >= 999999999
				           THEN 'UNLIMITED'

				           ELSE TO_CHAR(
				                    MAX(
				                        CASE
				                            WHEN f.balance_category = 'SMS'
				                            THEN f.total_bucket
				                        END
				                    )
				                ) || ' SMS'
				       END AS sms_benefit,

				       /* VOICE BENEFIT */
				       CASE
				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'VOICE'
				                        THEN f.total_bucket
				                    END
				                ) IS NULL
				           THEN NULL

				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'VOICE'
				                        THEN f.total_bucket
				                    END
				                ) >= 999999999
				           THEN 'UNLIMITED'

				           ELSE TO_CHAR(
				                    MAX(
				                        CASE
				                            WHEN f.balance_category = 'VOICE'
				                            THEN f.total_bucket
				                        END
				                    )
				                ) || ' Sec'
				       END AS voice_benefit

				FROM cs_rat_tariff_package a

				LEFT JOIN cs_rat_tariff_service_pack_map c
				       ON a.tariff_package_id = c.tariff_package_id
				      AND c.tariff_plan_type = 'DATP'

				LEFT JOIN cs_atp_accumu_bon_disc_map d
				       ON c.service_package_id = d.atp_id

				LEFT JOIN cs_bndl_mt_bndl_bucket_map e
				       ON d.bundle_or_discount_id = e.bundle_id

				LEFT JOIN (
				      SELECT e2.bundle_id,
				             f2.balance_category,
				             SUM(f2.bucket_unit_value) AS total_bucket
				      FROM cs_bndl_mt_bndl_bucket_map e2
				      JOIN bndl_mt_buckets f2
				        ON e2.bucket_id = f2.bucket_id
				      GROUP BY e2.bundle_id,
				               f2.balance_category
				) f
				       ON e.bundle_id = f.bundle_id

				WHERE a.network_id = ?

				GROUP BY a.tariff_package_id,
				         a.tariff_package_desc,
				         a.package_type,
						 a.is_corporate_yn

				ORDER BY a.tariff_package_id

				""";

		String datpBenefitsSql = """
												SELECT
				       t.tariff_package_id,
				       sp.service_package_id AS datp_id,
				       sp.service_package_desc AS datp_name,

				       /* VOICE BENEFIT */
				       CASE
				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'VOICE'
				                        THEN f.total_bucket
				                    END
				                ) IS NULL
				           THEN NULL

				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'VOICE'
				                        THEN f.total_bucket
				                    END
				                ) >= 999999999
				           THEN 'UNLIMITED'

				           ELSE TO_CHAR(
				                    MAX(
				                        CASE
				                            WHEN f.balance_category = 'VOICE'
				                            THEN f.total_bucket
				                        END
				                    )
				                ) || ' Sec'
				       END AS voice_benefit,

				       /* SMS BENEFIT */
				       CASE
				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'SMS'
				                        THEN f.total_bucket
				                    END
				                ) IS NULL
				           THEN NULL

				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'SMS'
				                        THEN f.total_bucket
				                    END
				                ) >= 999999999
				           THEN 'UNLIMITED'

				           ELSE TO_CHAR(
				                    MAX(
				                        CASE
				                            WHEN f.balance_category = 'SMS'
				                            THEN f.total_bucket
				                        END
				                    )
				                ) || ' SMS'
				       END AS sms_benefit,

				       /* DATA BENEFIT */
				       CASE
				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'DATA'
				                        THEN f.total_bucket
				                    END
				                ) IS NULL
				           THEN NULL

				           WHEN MAX(
				                    CASE
				                        WHEN f.balance_category = 'DATA'
				                        THEN f.total_bucket
				                    END
				                ) >= 999999999
				           THEN 'UNLIMITED'

				           ELSE fn_data_unit_converter(
				                    MAX(
				                        CASE
				                            WHEN f.balance_category = 'DATA'
				                            THEN f.total_bucket
				                        END
				                    )
				                )
				       END AS data_benefit

				FROM cs_rat_tariff_package t

				JOIN cs_rat_tariff_service_pack_map m
				     ON t.tariff_package_id = m.tariff_package_id
				    AND m.tariff_plan_type = 'DATP'

				JOIN cs_rat_service_package sp
				     ON sp.service_package_id = m.service_package_id

				LEFT JOIN cs_atp_accumu_bon_disc_map d
				     ON sp.service_package_id = d.atp_id

				LEFT JOIN cs_bndl_mt_bndl_bucket_map e
				     ON d.bundle_or_discount_id = e.bundle_id

				LEFT JOIN (
				      SELECT
				             e2.bundle_id,
				             f2.balance_category,
				             SUM(f2.bucket_unit_value) AS total_bucket
				      FROM cs_bndl_mt_bndl_bucket_map e2
				      JOIN bndl_mt_buckets f2
				        ON e2.bucket_id = f2.bucket_id
				      GROUP BY
				             e2.bundle_id,
				             f2.balance_category
				) f
				ON e.bundle_id = f.bundle_id

				WHERE t.network_id = ?

				GROUP BY
				       t.tariff_package_id,
				       sp.service_package_id,
				       sp.service_package_desc

				ORDER BY
				       t.tariff_package_id,
				       sp.service_package_desc
												""";
		String rateGroupSql = """
				SELECT UNIQUE
				    f.tariff_package_id,
				    f.tariff_package_desc,
				    a.rate_group_name

				FROM cs_rate_group_data a,
				     cs_dre_rating_group_details b,
				     cs_rat_service_data_zone_map c,
				     cs_rat_service_plan_package d,
				     cs_rat_tariff_service_pack_map e,
				     cs_rat_tariff_package f

				WHERE a.rate_group_id = b.rating_group_id
				AND b.zone_group_id = c.data_zone_id
				AND c.service_plan_id = d.service_plan_id
				AND d.service_package_id = e.service_package_id
				AND e.tariff_package_id = f.tariff_package_id
				AND e.network_id = ?
				""";

		// Main Query Result
		List<TariffPackageDetailsDto> tariffList = jdbcTemplate.query(
				tariffSql,
				(rs, rowNum) -> {

					TariffPackageDetailsDto dto = new TariffPackageDetailsDto();

					dto.setTariff_package_id(
							rs.getLong("tariff_package_id"));

					dto.setTariffPackageDesc(
							rs.getString("tariff_package_desc"));

					dto.setActivationFee(
							rs.getDouble("activation_fee"));

					dto.setRentalType(
							rs.getString("rental_type"));

					dto.setRentalPeriod(
							rs.getLong("rental_period"));

					dto.setDataBenefit(
							rs.getString("data_benefit"));

					dto.setSmsBenefit(
							rs.getString("sms_benefit"));

					dto.setVoiceBenefit(
							rs.getString("voice_benefit"));

					dto.setPackageType(rs.getString("package_type"));
					dto.setIsCorporateYn(rs.getString("is_corporate_yn"));

					dto.setRateGroupNames(
							new ArrayList<>());

					return dto;

				},
				networkId);

		// Rate Group Query Result
		List<Map<String, Object>> rateGroupList = jdbcTemplate.queryForList(
				rateGroupSql,
				networkId);
		List<Map<String, Object>> datpRows = jdbcTemplate.queryForList(
				datpBenefitsSql,
				networkId);

		// Group Rate Groups by Tariff Package ID
		Map<Long, List<String>> rateGroupMap = new HashMap<>();

		for (Map<String, Object> row : rateGroupList) {

			Long tariffPackageId = ((Number) row.get("TARIFF_PACKAGE_ID"))
					.longValue();

			String rateGroupName = (String) row.get("RATE_GROUP_NAME");

			rateGroupMap
					.computeIfAbsent(
							tariffPackageId,
							k -> new ArrayList<>())
					.add(rateGroupName);
		}

		Map<Long, List<DatpBenefitDto>> datpBenefitsMap = new HashMap<>();

		for (Map<String, Object> row : datpRows) {

			Long tariffPackageId = ((Number) row.get("TARIFF_PACKAGE_ID"))
					.longValue();

			DatpBenefitDto datp = new DatpBenefitDto();

			datp.setDatpId(
					((Number) row.get("DATP_ID"))
							.longValue());

			datp.setDatpName(
					(String) row.get("DATP_NAME"));

			datp.setVoiceBenefit(
					(String) row.get("VOICE_BENEFIT"));

			datp.setSmsBenefit(
					(String) row.get("SMS_BENEFIT"));

			datp.setDataBenefit(
					(String) row.get("DATA_BENEFIT"));

			datpBenefitsMap
					.computeIfAbsent(
							tariffPackageId,
							k -> new ArrayList<>())
					.add(datp);
		}
		// Map Rate Groups to Main Response
		for (TariffPackageDetailsDto dto : tariffList) {

			dto.setRateGroupNames(
					rateGroupMap.getOrDefault(
							dto.getTariff_package_id(),
							new ArrayList<>()));

			dto.setDatpBenefits(
					datpBenefitsMap.getOrDefault(
							dto.getTariff_package_id(),
							new ArrayList<>()));
		}

		return tariffList;
	}
}