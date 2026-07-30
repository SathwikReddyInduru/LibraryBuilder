package com.xius.TariffBuilder.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.xius.TariffBuilder.Dto.ServiceMetaDataResponse;



@Repository
public class ServiceMetaDataRepository {

	@Autowired
	@Qualifier("oracleJdbcTemplate")
	private JdbcTemplate oracleJdbcTemplate;

	public List<ServiceMetaDataResponse> getServices(List<String> serviceNames) {

		StringBuilder sql = new StringBuilder("SELECT ca_service_id, " + "RTRIM(RTRIM(" + "ca_service_name "
				+ "|| NVL2(ca_service_name,'_',NULL) " + "|| DECODE(ca_calltype,0,'MO',1,'MT') "
				+ "|| NVL2(ca_calltype,'_',NULL) " + "|| ca_call_category " + "|| NVL2(ca_call_category,'_',NULL) "
				+ "|| ca_channel_type" + "), '_') " + "|| CASE " + "WHEN UPPER(ca_service_name) = 'VOICE' THEN '~Sec' "
				+ "WHEN UPPER(ca_service_name) = 'SMS' THEN '~Msg' "
				+ "WHEN UPPER(ca_service_name) = 'DATA' THEN '~Kb' " + "ELSE '' " + "END AS service_name "
				+ "FROM ca_service_meta_data " + "WHERE UPPER(ca_service_name) IN (");

		for (int i = 0; i < serviceNames.size(); i++) {
			sql.append("?");
			if (i < serviceNames.size() - 1) {
				sql.append(",");
			}
		}

		sql.append(") ORDER BY ca_service_id");

		Object[] params = serviceNames.stream().map(String::toUpperCase).toArray();

		return oracleJdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> new ServiceMetaDataResponse(rs.getLong("ca_service_id"), rs.getString("service_name")),
				params);
	}
}
