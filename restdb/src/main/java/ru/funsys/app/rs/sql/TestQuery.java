/**
 * 
 */
package ru.funsys.app.rs.sql;

import java.util.ArrayList;
import java.util.HashMap;

import javax.ws.rs.core.MultivaluedMap;

import ru.funsys.avalanche.rs.RestException;

/**
 * @author ������� ���������
 * 
 */
public class TestQuery {

	/**
	 * ������ ��������� SQL ������� ������ ������
	 *  
	 * @param lang ���� �������, ���������� �� HTTP-��������� (��. Content-Language)
	 * @param parameters ��������� �������
	 * @param list ����������� � ������ ������ ��������������� ���������� ��� ���������� SQL ������
	 * @return ��������������� SQL ������
	 * @throws Exception ��������� ������ �������� ���������� ����������
	 */
	public String select(String lang, MultivaluedMap<String, String> parameters, ArrayList<Object> list) throws Exception {
		String us_name = parameters.getFirst("us_name");
		int page; // ����� �������� 
		try {
			page = Integer.parseInt(parameters.getFirst("page"));
		} catch (Exception e) {
			page = -1;
		}
		int limit; // ������ ��������
		try {
			limit = Integer.parseInt(parameters.getFirst("size"));
		} catch (Exception e) {
			limit = 5;
		}
		String orderBy = parameters.getFirst("orderBy");
		StringBuilder builder = new StringBuilder("SELECT * FROM rp.users");
		if (us_name != null) {
			list.add(us_name);
			builder.append(" WHERE us_name = ?");
		}
		if (orderBy != null) builder.append(" ORDER BY ").append(orderBy);
		if (page > -1) {
			int offset = page * limit; 
			list.add(limit);
			list.add(offset);
			builder.append(" LIMIT ? OFFSET ?");
		}
		return builder.toString();
	}
	
	public String insert(String lang, MultivaluedMap<String, String> parameters, ArrayList<HashMap<String, Object>> records, ArrayList<Object> list) throws Exception {
		HashMap<String, Object> record = (HashMap<String, Object>) records.get(0); 
		list.add(record.get("us_name"));
		list.add(record.get("us_last"));
		list.add(record.get("us_email"));
		return "INSERT INTO rp.users (us_name, us_last, us_email) VALUES (?, ?, ?)";
	}
	
	public String update(String lang, MultivaluedMap<String, String> parameters, ArrayList<HashMap<String, Object>> records, ArrayList<Object> list) throws Exception {
		String us_name = parameters.getFirst("us_name");
		String us_last = parameters.getFirst("us_last");
		if (us_name == null) throw new RestException("RST0100E", new Object[] {"us_name"}, lang);
		if (us_last == null) throw new RestException("RST0100E", new Object[] {"us_last"}, lang);
		HashMap<String, Object> record = (HashMap<String, Object>) records.get(0); 
		list.add(record.get("us_email"));
		list.add(us_name);
		list.add(us_last);
		return "UPDATE rp.users SET us_email = ? WHERE us_name = ? AND us_last = ?";
	}

	public String delete(String lang, MultivaluedMap<String, String> parameters, ArrayList<HashMap<String, Object>> records, ArrayList<Object> list) throws Exception {
		String us_name = parameters.getFirst("us_name");
		String us_last = parameters.getFirst("us_last");
		if (us_name == null) throw new RestException("RST0100E", new Object[] {"us_name"}, lang);
		if (us_last == null) throw new RestException("RST0100E", new Object[] {"us_last"}, lang);
		list.add(us_name);
		list.add(us_last);
		return "DELETE FROM rp.users WHERE us_name = ? AND us_last = ?";
	}
}
