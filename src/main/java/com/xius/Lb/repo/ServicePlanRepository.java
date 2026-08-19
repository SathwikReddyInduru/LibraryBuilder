package com.xius.Lb.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ServicePlanRepository {

	private final JdbcTemplate oracleJdbcTemplate;

	public ServicePlanRepository(JdbcTemplate oracleJdbcTemplate) {

		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

	public boolean existsServicePlan(Long networkId, String servicePlanDesc) {

		String sql = """
				SELECT COUNT(1)
				FROM cs_rat_service_plans
				WHERE network_id = ?
				  AND UPPER(service_plan_desc) =
				      UPPER(?)
				""";

		Integer count = oracleJdbcTemplate.queryForObject(sql, Integer.class, networkId, servicePlanDesc);

		return count != null && count > 0;
	}

	public Long getNextServicePlanId() {

		String sql = """
				SELECT seq_service_plan_id.NEXTVAL
				FROM DUAL
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class);
	}

	public String getRatingApplicableYn(Integer typeOfService) {

		String sql = """
				SELECT rating_applicable_yn
				FROM cs_rat_mt_service_plan_type
				WHERE service_plan_type_id = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, String.class, typeOfService);
	}

	public void insertServicePlan(Long networkId, Long servicePlanId, String servicePlanDesc, String servicePlanType,
			Integer typeOfService, Integer priority, String limitedHoursYn, Integer servicePlanFreqFromHrs,
			Integer servicePlanFreqToHrs, String allowMtc, String allowMoc, String allowNldMo, String allowIldMo,
			String allowData, String ratingType, Long zoneGroupId, Long smsZoneGroupId, Long nsLocalOnnetCalendarId,
			Long nsLocalOffnetCalendarId, Long nsNldCalendarId, Long nsIldCalendarId, String bndlWithSpYn,
			Long bundleId, String createdBy,String status, Long smsCalendarId, Integer groupsAllowed, String allowNtnlRmData,
			String allowIntlRmData, Long mtCalendarId, Long deviceGroupId, Long mmsCalendarId,
			String planConfirmNotification, String planExpNotification, Integer planExpNotifThresholdDays,
			Integer planExpNotifThresholdHrs, String zoneBasedVipPlanFlagYn) {

		String sql = """
				INSERT INTO cs_rat_service_plans
				(
				    network_id,
				    service_plan_id,
				    service_plan_desc,
				    service_plan_type,
				    type_of_service,
				    priority,
				    limited_hours_yn,
				    service_plan_freq_from_hrs,
				    service_plan_freq_to_hrs,
				    allow_mtc,
				    allow_moc,
				    allow_nld_mo,
				    allow_ild_mo,
				    allow_data,
				    rating_type,
				    zone_group_id,
				    sms_zone_group_id,
				    ns_local_onnet_calendar_id,
				    ns_local_offnet_calendar_id,
				    ns_nld_calendar_id,
				    ns_ild_calendar_id,
				    bndl_with_sp_yn,
				    bundle_id,
				    created_date,
				    created_by,
				    status,
				    sms_calendar_id,
				    groups_allowed,
				    allow_ntnl_rm_data,
				    allow_int_rm_data,
				    mt_calender_id,
				    device_group_id,
				    mms_calendar_id,
				    plan_confirm_notification,
				    plan_exp_notification,
				    plan_exp_notif_threshold_days,
				    plan_exp_notif_threshold_hrs,
				    zone_based_vip_plan_flag_yn
				)
				VALUES
				(
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    SYSDATE,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    NVL(?, 0),
				    NVL(?, 0),
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql,

				networkId, servicePlanId, servicePlanDesc, servicePlanType, typeOfService, priority, limitedHoursYn,
				servicePlanFreqFromHrs, servicePlanFreqToHrs, allowMtc, allowMoc, allowNldMo, allowIldMo, allowData,
				ratingType, zoneGroupId, smsZoneGroupId, nsLocalOnnetCalendarId, nsLocalOffnetCalendarId,
				nsNldCalendarId, nsIldCalendarId, bndlWithSpYn, bundleId, createdBy,status, smsCalendarId, groupsAllowed,
				allowNtnlRmData, allowIntlRmData, mtCalendarId, deviceGroupId, mmsCalendarId, planConfirmNotification,
				planExpNotification, planExpNotifThresholdDays, planExpNotifThresholdHrs, zoneBasedVipPlanFlagYn);
	}
	
	public void insertSimpleServicePlan(
	        Long networkId,
	        Long servicePlanId,
	        String servicePlanDesc,
	        String servicePlanType,
	        Integer billingServiceType,
	        String status) {

	    String sql = """
	            INSERT INTO cs_rat_service_plans
	            (
	                network_id,
	                service_plan_id,
	                service_plan_desc,
	                service_plan_type,
	                type_of_service,
	                created_date,
	                status
	            )
	            VALUES
	            (
	                ?,
	                ?,
	                ?,
	                ?,
	                ?,
	                SYSDATE,
	                ?
	            )
	            """;

	    oracleJdbcTemplate.update(
	            sql,
	            networkId,
	            servicePlanId,
	            servicePlanDesc,
	            servicePlanType,
	            billingServiceType,
	            status
	    );
	}

	/**
	 * Field-level update for an existing service plan. Row is never
	 * dropped/recreated on Modify ATP - only re-syncs what changed.
	 */
	public void updateServicePlan(Long servicePlanId, String limitedHoursYn, Integer applicableFromHrs,
			Integer applicableToHrs, String allowMtc, String allowMoc, String allowNldMo, String allowIldMo,
			String ratingType, Long zoneGroupId, String allowNtnlRmData, String allowIntlRmData, Long mtCalendarId,
			String zoneBasedVipPlanFlagYn) {

		String sql = """
				UPDATE cs_rat_service_plans
				SET limited_hours_yn = ?,
				    service_plan_freq_from_hrs = ?,
				    service_plan_freq_to_hrs = ?,
				    allow_mtc = ?,
				    allow_moc = ?,
				    allow_nld_mo = ?,
				    allow_ild_mo = ?,
				    rating_type = ?,
				    zone_group_id = ?,
				    allow_ntnl_rm_data = ?,
				    allow_int_rm_data = ?,
				    mt_calender_id = ?,
				    zone_based_vip_plan_flag_yn = ?
				WHERE service_plan_id = ?
				""";

		oracleJdbcTemplate.update(sql, limitedHoursYn, applicableFromHrs, applicableToHrs, allowMtc, allowMoc,
				allowNldMo, allowIldMo, ratingType, zoneGroupId, allowNtnlRmData, allowIntlRmData, mtCalendarId,
				zoneBasedVipPlanFlagYn, servicePlanId);
	}

	/**
	 * DATA zone mappings have no natural key, so Modify ATP replaces them
	 * wholesale for a DATA service plan: delete then re-insert via
	 * insertDataZoneMappings.
	 */
	public void deleteDataZoneMappings(Long servicePlanId) {

		String sql = """
				DELETE FROM cs_rat_service_data_zone_map
				WHERE service_plan_id = ?
				""";

		oracleJdbcTemplate.update(sql, servicePlanId);
	}

	/**
	 * Procedure:
	 *
	 * FOR i IN 1 .. pi_data_zone_group_id.COUNT
	 *
	 * INSERT INTO cs_rat_service_data_zone_map ( service_plan_id, data_zone_id )
	 * VALUES ( service_plan_id, pi_data_zone_group_id(i) );
	 */
	public void insertDataZoneMappings(Long servicePlanId, List<Long> dataZoneGroupIds) {

		if (dataZoneGroupIds == null || dataZoneGroupIds.isEmpty()) {

			return;
		}

		String sql = """
				INSERT INTO cs_rat_service_data_zone_map
				(
				    service_plan_id,
				    data_zone_id
				)
				VALUES
				(
				    ?,
				    ?
				)
				""";

		for (Long dataZoneId : dataZoneGroupIds) {

			if (dataZoneId == null) {

				continue;
			}

			oracleJdbcTemplate.update(sql, servicePlanId, dataZoneId);
		}
	}

	/**
	 * Procedure:
	 *
	 * INSERT INTO cs_rat_service_plan_charge_map ( service_plan_id, charge_id,
	 * network_id )
	 */
	public void insertChargeMappings(Long servicePlanId, Long networkId, List<String> periodicChargeIds) {

		if (periodicChargeIds == null || periodicChargeIds.isEmpty()) {

			return;
		}

		String sql = """
				INSERT INTO cs_rat_service_plan_charge_map
				(
				    service_plan_id,
				    charge_id,
				    network_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?
				)
				""";

		for (String chargeId : periodicChargeIds) {

			if (chargeId == null || chargeId.isBlank()) {

				continue;
			}

			oracleJdbcTemplate.update(sql, servicePlanId, chargeId, networkId);
		}
	}

	/**
	 * Procedure:
	 *
	 * INSERT INTO cs_roam_net_service_plan_map ( service_plan_id,
	 * visiting_network_id, network_id, voice_zone_group_id, sms_zone_group_id,
	 * voice_mt_calender_id, sms_mt_calender_id )
	 */
	public void insertRoamingMappings(Long servicePlanId, Long networkId, List<String> roamingMappings) {

		if (roamingMappings == null || roamingMappings.isEmpty()) {

			return;
		}

		String sql = """
				INSERT INTO cs_roam_net_service_plan_map
				(
				    service_plan_id,
				    visiting_network_id,
				    network_id,
				    voice_zone_group_id,
				    sms_zone_group_id,
				    voice_mt_calender_id,
				    sms_mt_calender_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		for (String mapping : roamingMappings) {

			if (mapping == null || mapping.isBlank()) {

				continue;
			}

			String[] values = mapping.split("~", -1);

			if (values.length != 5) {

				throw new IllegalArgumentException("Invalid roaming service-plan mapping: " + mapping);
			}

			oracleJdbcTemplate.update(sql, servicePlanId, values[0].trim(), networkId, values[1].trim(),
					values[2].trim(), values[3].trim(), values[4].trim());
		}
	}
}