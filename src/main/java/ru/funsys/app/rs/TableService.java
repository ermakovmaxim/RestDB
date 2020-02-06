/**
 * 
 */
package ru.funsys.app.rs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import ru.funsys.avalanche.Application;
import ru.funsys.avalanche.rs.RestException;
import ru.funsys.avalanche.sql.Adapter;
import ru.funsys.avalanche.sql.ExecuteSet;

/**
 * 
 * �������, ��������� � �������, ��������� ��� ������ rp.structure_type � rp.users  
 * <pre>
 * CREATE TABLE rp.structure_type (
 *   st_id INTEGER NOT NULL,
 *   st_name CHAR(32) NOT NULL,
 *   CONSTRAINT st_pk PRIMARY KEY (st_id)
 * );
 * 
 * CREATE TABLE rp.users (
    us_name CHAR(32) NOT NULL,
    us_last CHAR(32) NOT NULL,
    us_email CHAR(64) NOT NULL,
    CONSTRAINT us_pk PRIMARY KEY (us_name, us_last)
);
 * </pre>
 * 
 * @author ������� ���������
 * 
 */
@Path("data")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
public class TableService extends Application {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3169692529838007775L;

	/**
	 * �������� ������.
	 */
	private Adapter database;
	
	/**
	 * �������� ������.
	 * 
	 * ����, ����������� �������� ��� ������ ������� � ������ {@code get}. �������� {@code false} �������������
	 * ������������ � ����� ����������. �� ��������� - {@code true}    
	 */
	private boolean disableAll = true;
	
	/**
	 * �������� ������.
	 * 
	 * ����, ����������� �������� �������������� � ��������� ������. �������� {@code false} �������������
	 * ������������ � ����� ����������. �� ��������� - {@code true}  
	 */
	private boolean disableInfo = true;
	
	/**
	 * �������� ������.
	 * 
	 * ����� ������, ������������ �� ���������
	 */
	private String defaultSchema;
	
	
	/**
	 * ������ ����� ��������� ������ ��� ������ 
	 */
	private HashMap<String, ArrayList<String>> primary = new HashMap<String, ArrayList<String>>(); 
	
	/**
	 * ������ ������� ������ ������� ����������� SQL �������� 
	 */
	private ArrayList<String> packages = new ArrayList<String>(); 
	
	/**
	 * ������ ������� ������ ������� ����������� SQL �������� 
	 */
	private HashMap<String, Object> generators = new HashMap<String, Object>(); 

	public void setPackages(String packages) {
		String[] list = packages.split(";");
		for (int index = 0; index < list.length; index++) {
			this.packages.add(list[index]);
		}
	}
	
	/**
	 * �������� ���� ��� ����� ������ �������.
	 * 
	 * <p>
	 * ��������� ��������� HTTP �������
	 * 
	 * <ul>
	 * <li>Accept - ��� ������ ������, ����� ��������� �������� application/json ��� application/xml
	 * <li>Content-Language - ���� ������������ ���������, �� ��������� ru
	 * </ul>
	 * <pre>
	 * http://host:port/name_context/map_servlet/data/table/{schema.table}?���������_�������
	 * </pre>
	 *
	 * <p>������ URL ������� c ����������� ��� ��������� ���� �������.
	 * ������� ���������� ����� ����� �������. ��� ����� ������ � ���� �� ���� � ���������� ������� ������
	 * ����� ���� � ���� ���������. ��������, st_id � st_ID ����� ���������� ��� ����� ������ �����.   
	 * <pre>
	 * .../data/table/rp.Structure_type?st_id=42&amp;amp;st_id=43
	 * </pre>
	 * 
	 * <p>
	 * ������ ���� ������ � ������� JSON � ��������, ���������������� ���������� ������� 
	 * 	 * <pre>
	 * [{"st_id":42,"st_name":"Test42"},{"st_id":43,"st_name":"Test43"}]
	 * </pre>
	 * 
	 * ������ ������������� ���������� ������� XML ��� ������ � ����� ���� � ������ ���������� �������.
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?&gt;
	 * &lt;serverError&gt;
	 *   &lt;cause&gt;ru.funsys.avalanche.AvalancheRemote: [16616@likhovskikh-vv] SYS0250E ��� ������ ������ "select" �������� ������.
	 * 	������: "org.postgresql.util.PSQLException - ������: ������� "st_iden" �� ����������
	 *   ���������: ��������, �������������� ������ �� ������� "structure_type.st_id". �������: 54"
	 *   &lt;/cause&gt;
	 *   &lt;code&gt;RST0001E&lt;/code&gt;
	 *   &lt;message&gt;��� ���������� ��������� SELECT �������� ������.&lt;/message&gt;
	 * &lt;/serverError&gt;
	 * </pre>
	 * 
	 * @param bean ��������� �������
	 * @return ��������� ���������� �������
	 * @throws RestException ��������� ������
	 */
	@GET
	@Path("table/{object}")
	public ArrayList<HashMap<String, Object>> get(@BeanParam BeanRequest bean) throws RestException {
		String lang = bean.getLang();
		ArrayList<Object> parameters = new ArrayList<Object>(); 
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT * FROM ").append(getTable(bean.getObject())).append(' ');
		MultivaluedMap<String, String> queryParamas = bean.getQueryParams(); 
		if (queryParamas.size() > 0) {
			builder.append("WHERE ");
			boolean secondary = false;
			for (String name : queryParamas.keySet()) {
            	List<String> values = queryParamas.get(name);
            	if (secondary) {
        			builder.append(" AND ");
            	} else {
            		secondary = true;
            	}
	            if (values.size() > 1) {
        			builder.append(name).append(" IN (");
        			boolean valueSecondary = false;
                	for (Object value : values) {
                    	if (valueSecondary) {
                			builder.append(", ");
                    	} else {
                    		valueSecondary = true;
                    	}
            			builder.append("?");
    	                parameters.add(value);       	
    		        }
        			builder.append(')');
	            } else {
	            	String value = values.get(0);
	            	builder.append(name);
	            	if (value.indexOf('%') < 0) {
	            		builder.append(" =");
	            	} else {
	            		builder.append(" LIKE");
	            	}
	            	builder.append(" ?");
	                parameters.add(values.get(0));       	
	            }
		    }
		} else {
			if (disableAll) {
				throw new RestException("RST0020E", null, lang);
			}
		}
		return query(builder.toString(), parameters, lang);
	}

	/**
	 * �������� ���� ��� ����� ������� � �������. ���� � �������� ������ ����������� ��������� �������,
	 * �� ���� ������� ������������. ��������� ������� ��������� ��������� � ������� ���� ������.
	 * � ���� ������� ����� ���������� ��������� ��������� ������� ��� ������� �� � �������. ��� ������
	 * ����������� � ����� ����������.
	 * 
	 * <p>
	 * ��������� ��������� HTTP �������
	 * 
	 * <ul>
	 * <li>Accept - ��� ������ ������, ����� ��������� �������� application/json ��� application/xml
	 * <li>Content-Language - ���� ������������ ���������, �� ��������� ru
	 * <li>Content-Type - ��� ������ ���� �������, ����� ��������� �������� application/json ��� application/xml
	 * </ul>
	 * <pre>
	 * http://host:port/name_context/map_servlet/data/table/{schema.table}?���������_�������
	 * </pre>
	 *
	 * <p>������ URL ������� c ����������� ��� ������� ����� ������
	 * <pre>
	 * .../data/table/rp.Structure_type?st_id=42&amp;st_name=Test42
	 * </pre>
	 * 
	 * <p>������ URL �������, ��������� ���������� ������� ������� ������� ���� �������
	 * <pre>
	 * ../data/table/rp.Structure_type
	 * </pre>
	 * 
	 * <p>
	 * ������ ���� ������� � ������� JSON ��� ���������� ���� ������� 
	 * 	 * <pre>
	 * [{"st_id":41,"st_name":"Test41"},{"st_id":42,"st_name":"Test42"}]
	 * </pre>
	 * 
	 * <p>
	 * ������ ���� ������� � ������� XML ��� ���������� ���� ������� 
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8" standalone="no" ?&gt;
	 * &lt;rows&gt;
	 *   &lt;row&gt;
	 *     &lt;field name="st_id"&gt;43&lt;/field&gt;
	 *     &lt;field name="st_name"&gt;Test43&lt;/field&gt;
	 *   &lt;/row&gt;
	 *   &lt;row&gt;
	 *     &lt;field name="st_id"&gt;44&lt;/field&gt;
	 *     &lt;field name="st_name"&gt;Test44&lt;/field&gt;
	 *   &lt;/row&gt;
	 * &lt;/rows&gt;
	 * </pre>
	 * 
	 * @param bean ������ �������� ���������� ������� HTTP ��������� {@code (@HeaderParam)}, ���������� � URI {@code (@PathParam)}
	 *             � ���������� ������� {@code (@QueryParam)} 
	 * @param records ������ �������� ���������� ����������� ������� � ���� �������  

	 * @throws RestException ���������� � ������� ������
	 * 
	 * <p>������ ������ � ������� JSON
	 * <pre>
	 * {"message": "��� ���������� ��������� INSERT �������� ������.",
	 *  "code": "RST0003E",
	 *  "cause": "ru.funsys.avalanche.sql.SQLException: [28500@likhovskikh-vv] SQL0021E ��� ���������� ������� ��������� ������. ����� �������: 0, ������: INSERT INTO rp.Structure_type (st_id, st_name) VALUES (?, ?), (?, ?).\r\n\tat ru.funsys.avalanche.sql.Database.execute(Database.java:653)\r\n\tat ru.funsys.avalanche.sql.Database.execute(Database.java:577)\r\n\tat ru.funsys.avalanche.sql.Database.execute(Database.java:565)\r\n\tat ru.transset.app.gui.provider.TableService.add(TableService.java:216)\r\n\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\r\n\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\r\n\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\r\n\tat java.lang.reflect.Method.invoke(Method.java:498)\r\n\tat org.glassfish.jersey.server.model.internal.ResourceMethodInvocationHandlerFactory.lambda$static$0(ResourceMethodInvocationHandlerFactory.java:52)\r\n\tat org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher$1.run(AbstractJavaResourceMethodDispatcher.java:124)\r\n\tat org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher.invoke(AbstractJavaResourceMethodDispatcher.java:167)\r\n\tat org.glassfish.jersey.server.model.internal.JavaResourceMethodDispatcherProvider$VoidOutInvoker.doDispatch(JavaResourceMethodDispatcherProvider.java:159)\r\n\tat org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher.dispatch(AbstractJavaResourceMethodDispatcher.java:79)\r\n\tat org.glassfish.jersey.server.model.ResourceMethodInvoker.invoke(ResourceMethodInvoker.java:469)\r\n\tat org.glassfish.jersey.server.model.ResourceMethodInvoker.apply(ResourceMethodInvoker.java:391)\r\n\tat org.glassfish.jersey.server.model.ResourceMethodInvoker.apply(ResourceMethodInvoker.java:80)\r\n\tat org.glassfish.jersey.server.ServerRuntime$1.run(ServerRuntime.java:253)\r\n\tat org.glassfish.jersey.internal.Errors$1.call(Errors.java:248)\r\n\tat org.glassfish.jersey.internal.Errors$1.call(Errors.java:244)\r\n\tat org.glassfish.jersey.internal.Errors.process(Errors.java:292)\r\n\tat org.glassfish.jersey.internal.Errors.process(Errors.java:274)\r\n\tat org.glassfish.jersey.internal.Errors.process(Errors.java:244)\r\n\tat org.glassfish.jersey.process.internal.RequestScope.runInScope(RequestScope.java:265)\r\n\tat org.glassfish.jersey.server.ServerRuntime.process(ServerRuntime.java:232)\r\n\tat org.glassfish.jersey.server.ApplicationHandler.handle(ApplicationHandler.java:680)\r\n\tat org.glassfish.jersey.servlet.WebComponent.serviceImpl(WebComponent.java:394)\r\n\tat org.glassfish.jersey.servlet.WebComponent.service(WebComponent.java:346)\r\n\tat org.glassfish.jersey.servlet.ServletContainer.service(ServletContainer.java:366)\r\n\tat org.glassfish.jersey.servlet.ServletContainer.service(ServletContainer.java:319)\r\n\tat org.glassfish.jersey.servlet.ServletContainer.service(ServletContainer.java:205)\r\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:231)\r\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\r\n\tat org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:52)\r\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\r\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\r\n\tat org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:198)\r\n\tat org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:96)\r\n\tat org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:493)\r\n\tat org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:140)\r\n\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:81)\r\n\tat org.apache.catalina.valves.AbstractAccessLogValve.invoke(AbstractAccessLogValve.java:650)\r\n\tat org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:87)\r\n\tat org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:342)\r\n\tat org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:800)\r\n\tat org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:66)\r\n\tat org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:806)\r\n\tat org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1498)\r\n\tat org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49)\r\n\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)\r\n\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)\r\n\tat org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)\r\n\tat java.lang.Thread.run(Thread.java:748)\r\nCaused by: org.postgresql.util.PSQLException: ������: ������������� �������� ����� �������� ����������� ������������ \"st_pk\"\n �����������: ���� \"(st_id)=(41)\" ��� ����������.\r\n\tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2505)\r\n\tat org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2241)\r\n\tat org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:310)\r\n\tat org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:447)\r\n\tat org.postgresql.jdbc.PgStatement.execute(PgStatement.java:368)\r\n\tat org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:158)\r\n\tat org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:124)\r\n\tat org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement.executeUpdate(DelegatingPreparedStatement.java:121)\r\n\tat org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement.executeUpdate(DelegatingPreparedStatement.java:121)\r\n\tat ru.funsys.avalanche.sql.Database.execute(Database.java:640)\r\n\t... 51 more\r\n"
	 * }
	 * </pre>
	 * 
	 * <p>������ ������ � ������� XML
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8" standalone="yes" ?&gt;
	 * &lt;serverError&gt;
	 *   &lt;cause&gt;
	 *     ru.funsys.avalanche.sql.SQLException: [28500@likhovskikh-vv] SQL0021E ��� ���������� ������� ��������� ������. ����� �������: 0, ������: INSERT INTO rp.Structure_type (st_id, st_name) VALUES (?, ?), (?, ?).
	 *     Caused by: org.postgresql.util.PSQLException: ������: ������������� �������� ����� �������� ����������� ������������ "st_pk"
	 *                �����������: ���� "(st_id)=(41)" ��� ����������.
	 *   &lt;/cause&gt;
	 *   &lt;code&gt;RST0003E&lt;/code&gt;
	 *   &lt;message&gt;��� ���������� ��������� INSERT �������� ������.&lt;/message&gt;
	 * &lt;/serverError&gt;	 * </pre>
	 */
	@POST
	@Path("table/{object}")
	public Response post(@BeanParam BeanRequest bean, ArrayList<HashMap<String, Object>> records) throws RestException {
		String lang = bean.getLang();
		ArrayList<Object> parameters = new ArrayList<Object>(); 
		StringBuilder builder = new StringBuilder();
		ArrayList<String> insertValues = new ArrayList<String>();;
		builder.append("INSERT INTO ").append(getTable(bean.getObject())).append(" (");
		MultivaluedMap<String, String> queryParamas = bean.getQueryParams(); 
		if (queryParamas.size() > 0) {
			StringBuilder values = new StringBuilder();
			values.append("(");
			boolean secondary = false;
			for (String name : queryParamas.keySet()) {
            	if (secondary) {
        			builder.append(", ");
        			values.append(", ");
            	} else {
            		secondary = true;
            	}
            	List<String> query = queryParamas.get(name);
	            if (query.size() > 1) {
	            	// ��� ���� {0} ���������� ����� ������ �������� {1}
	            	throw new RestException("RST0002E", new Object[] {name, query.toString()}, lang);
	            } else {
        			builder.append(name);
        			values.append("?");
	                parameters.add(query.get(0));       	
	            }
		    }
			values.append(")");
			insertValues.add(values.toString());
		} else {
			if (records == null) {
            	throw new RestException("RST0004E", null, lang);
			} else {
				boolean first = true; // ������������ ������ ����� �� ������ ������
				for (HashMap<String, Object> record : records) {
					StringBuilder values = new StringBuilder();
					values.append("(");
					boolean secondary = false; // false - ������ ����, true - ����������� ���� ������ 
					for (String name : record.keySet()) {
		            	if (secondary) {
		            		if (first) builder.append(", ");
		        			values.append(", ");
		            	} else {
		            		secondary = true;
		            	}
		            	if (first) builder.append(name);
	        			values.append("?");
		                parameters.add(record.get(name));       	
					}
					values.append(")");
					insertValues.add(values.toString());
					first = false;
				}
			}
		}
		builder.append(") VALUES ");
		boolean secondary = false;
    	for (String values : insertValues) {
        	if (secondary) {
    			builder.append(", ");
        	} else {
        		secondary = true;
        	}
			builder.append(values);
    	}
		try {
			return Response.ok(query(builder.toString(), parameters)).build();
		} catch (Exception e) {
			throw new RestException("RST0003E", null, e, lang);
		}
	}

	/**
	 * �������������� ������ �������. � ���������� ������� ����������� ���� ������ �������� ����� ����������
	 * ����� ������� � ����������� ���� �������, ���� ��� �������� ����������� � ������ �������, ��� ����, ����
	 * �������������� ���� ���������� �����, �� ����������� � �������� ������ ��� ��������: ������ - �������
	 * �������� ��������� ����, ������ - ����� ��������.
	 * 
	 * <p>
	 * ������ ������� ����������� ������ � ����������� � �������� ������. �������� 101 ��������� ���� st_id �����
	 * �������� �� �������� 1  
	 * <pre>
	 * .../data/table/rp.structure_type?st_id=101&amp;st_id=1&amp;st_name=Test101
	 * </pre>
	 * 
	 * ������ ������� ����������� ��������� ������ ������������, ��������� us_name � us_last - ���� ����������
	 * ����� ������� rp.users, ��������������� �������� ��������� ������ ���������� � ���� �������   
	 * <pre>
	 * PUT .../data/table/rp.users?us_name=����&amp;us_last=������ HTTP/1.1
	 * Accept: application/xml
	 * Content-Language: ru
	 * Content-Length: 40
	 * Host: localhost:8080
	 * Content-Type: application/json
	 * 
	 * [{"us_email":"I.Iivanov%40transset.ru"}]
	 * </pre>
	 * 
	 * ������ ���������� ��� ������ � ������� JSON, ���� ������� �������� �������� �������� �����.
	 * ��� ������: 404  
	 * <pre>
	 * {
	 *   "message": "������ �� ���������� ��������� ����� �� �������.",
	 *   "code": "RST0019E",
	 *   "cause": null
	 * }
	 * </pre>
	 *  
	 * @param bean ��������� �������
	 * @param records ��������������� ��������� ����� ������
	 * @throws RestException ��� ������������� ������
	 */
	@PUT
	@Path("table/{object}")
	public Response put(@BeanParam BeanRequest bean, ArrayList<HashMap<String, Object>> records) throws RestException {
		String table = getTable(bean.getObject());
		String lang = bean.getLang();
		ArrayList<String> key = primary.get(table);
		if (key == null) {
			// �������� ��������� ���� ������� 
			key = getPrimaryKeys(table, lang);
			primary.put(table, key);
		}
		StringBuilder builder = new StringBuilder();
		StringBuilder where = new StringBuilder();
		ArrayList<Object> parameters = new ArrayList<Object>();
		builder.append("UPDATE ").append(table).append(" SET ");
		MultivaluedMap<String, String> queryParamas = bean.getQueryParams(); 
		boolean notUsedBody = true;
		if (records != null) {
			if (records.size() == 1) {
				notUsedBody = false;
				HashMap<String, Object> record = records.get(0);
				boolean secondary = false;
				for (String name : record.keySet()) {
	            	if (secondary) {
	        			builder.append(", ");
	            	} else {
	            		secondary = true;
	            	}
					builder.append(name).append(" = ?");
                    parameters.add(record.get(name));       	
				}
			} else {
            	// ��� ��������� UPDATE �� ��������� ���������� � ���� ������� ����� ����� ������.
				throw new RestException("RST0010E", null, lang);
			}
		}
		if (queryParamas.size() > 0) {
			int set = 0; // ������� ��������������� ���������� SET
			boolean secondary = false;
			boolean secondaryWhere = false;
			ArrayList<String> keyWhere = new ArrayList<String>(); // ����������� � ������� WHERE �������� ���������� ����� 
			for (String name : queryParamas.keySet()) {
            	String lowerName = name.toLowerCase();
				List<String> query = queryParamas.get(name);
	            switch (query.size()) {
				case 1:
					if (key.contains(lowerName)) {
						if (!keyWhere.contains(lowerName)) {
							if (secondaryWhere) {
			        			where.append(" AND ");
							} else {
								secondaryWhere = true;
							}
							where.append(name).append(" = ?");
							parameters.add(query.get(0));
							keyWhere.add(lowerName);
						} else {
			            	// ����������� ������������ �������� {0} ��������� ���� {1}
							throw new RestException("RST0025E", new Object[] {query.toString(), name}, lang);
						}
					} else {
						if (notUsedBody) {
			            	if (secondary) {
			        			builder.append(", ");
			            	} else {
			            		secondary = true;
			            	}
							builder.append(name).append(" = ?");
			                parameters.add(set, query.get(0));
			                set++;
						} else {
			            	// ��� ���� {0}, �� �������� � ��������� ����, ����������� ���������� �������� {1} ��� ����������� ���� �������
							throw new RestException("RST0012E", new Object[] {name, query.toString()}, lang);
						}
					}
					break;
				case 2:
					if (key.contains(lowerName)) {
						if (!keyWhere.contains(lowerName)) {
							if (secondaryWhere) {
			        			where.append(" AND ");
							} else {
								secondaryWhere = true;
							}
							where.append(name).append(" = ?"); // ������ �������� ���� ����� - ������� ��������
			                parameters.add(query.get(0));
							keyWhere.add(lowerName);
						} else {
			            	// ����������� ������������ �������� {0} ��������� ���� {1}
							throw new RestException("RST0025E", new Object[] {query.toString(), name}, lang);
						}
						if (notUsedBody) {
							// ������ �������� ���� ����� - ��������������� ��������
			            	if (secondary) {
			        			builder.append(", ");
			            	} else {
			            		secondary = true;
			            	}
							builder.append(name).append(" = ?");
			                parameters.add(set, query.get(1));
			                set++;
						} else {
			            	// ��� ���� {0}, �������� � ��������� ����, ������� ����� ������ �������� {1} ��� ����������� ���� �������
			            	throw new RestException("RST0011E", new Object[] {name, query.toString()}, lang);
						}
					} else {
		            	// ��� ���� {0}, �� �������� � ��������� ����, ������� ����� ������ �������� {1}
		            	throw new RestException("RST0009E", new Object[] {name, query.toString()}, lang);
					}
					break;
				default:
	            	// ��� ���� {0} ���������� ������� ����� �������� {1}
	            	throw new RestException("RST0008E", new Object[] {name, query.toString()}, lang);
				}
		    }
			if (key.size() != keyWhere.size()) {
            	// ����� ���������� {0} �� ��������� � ������ ����� ���������� ����� {1} ������� {2} 
				throw new RestException("RST0016E", new Object[] {keyWhere.size(), key.size(), table}, lang);
			}
	    } else {
        	// �������� ����� ���������� ����� �� ����������
	    	throw new RestException("RST0014E", null, lang);
		}
		builder.append(" WHERE ").append(where);
		BeanResponse beanResponse;
		try {
			beanResponse = query(builder.toString(), parameters);
		} catch (Exception e) {
			throw new RestException("RST0013E", null, e, lang);
		}
		if (beanResponse.getResult() == 0) {
			// ������ �� ���������� ��������� ����� �� �������.
			throw new RestException("RST0019E", null, lang, 404);
		}
		return Response.ok(beanResponse).build();
	}
	
	/**
	 * ������� ������ �������. � ���������� ������� ����������� �������� ����� ���������� �����.
	 * 
	 * ������� ������ �� ������� rp.structure_type �� ��������� ���������� ����� 43 ���� st_id 
	 * <pre>
	 * .../data/table/rp.structure_type?st_id=43
	 * </pre>
	 * 
	 * @param bean ��������� �������
	 * 
	 * @throws RestException ������, ��������� ��� ���������� ������� 
	 */
	@DELETE
	@Path("table/{object}")
	public Response delete(@BeanParam BeanRequest bean) throws RestException {
		String table = getTable(bean.getObject());
		String lang = bean.getLang();
		ArrayList<String> key = primary.get(table);
		if (key == null) {
			// �������� ��������� ���� ������� 
			key = getPrimaryKeys(table, bean.getLang());
			primary.put(table, key);
		}
		StringBuilder builder = new StringBuilder();
		ArrayList<Object> parameters = new ArrayList<Object>();
		builder.append("DELETE FROM ").append(table).append(" WHERE ");
		MultivaluedMap<String, String> queryParamas = bean.getQueryParams(); 
		if (queryParamas.size() > 0 ) {
			if (queryParamas.size() == key.size()) {
				boolean secondary = false;
				for (String name : queryParamas.keySet()) {
	            	List<String> query = queryParamas.get(name);
		            if (query.size() == 1) {
    					if (key.contains(name.toLowerCase())) {
    						if (secondary) {
    		        			builder.append(" AND ");
    		            	} else {
    		            		secondary = true;
    		            	}
    						builder.append(name).append(" = ?");
    						parameters.add(query.get(0));       	
    					} else {
    		            	// ���� {0} �� ������ � ��������� ���� ������� {1}
    		            	throw new RestException("RST0015E", new Object[] {name, table}, lang);
    					}	
		            	
		            } else {
		            	// ���������� ����� ������ �������� ��� ���� {0} ���������� ����� 
		            	throw new RestException("RST0017E", new Object[] {name}, lang);
		            }
				}
				
			} else {
            	// ����� ���������� {0} �� ��������� � ������ ����� ���������� ����� {1} ������� {2} 
            	throw new RestException("RST0016E", new Object[] {queryParamas.size(), key.size(), table}, lang);
			}
		} else {
        	// �������� ����� ���������� ����� �� ����������
        	throw new RestException("RST0014E", null, lang);
		}
		BeanResponse beanResponse;
		try {
			beanResponse = query(builder.toString(), parameters);
		} catch (Exception e) {
			throw new RestException("RST0006E", null, e, lang);
		}
		if (beanResponse.getResult() == 0) {
			// ������ �� ���������� ��������� ����� �� �������.
			throw new RestException("RST0019E", null, lang, 404);
		}
		return Response.ok(beanResponse).build();
		
	}
	
	/**
	 * ��������� ������������ ������ SELECT ������ SQL ����������.
	 * 
	 * <p>
	 * ��������� ��������� HTTP �������
	 * 
	 * <ul>
	 * <li>Accept - ��� ������ ������, ����� ��������� �������� application/json ��� application/xml
	 * <li>Content-Language - ���� ������������ ���������, �� ��������� ru
	 * </ul>
	 * <pre>
	 * http://host:port/name_context/map_servlet/data/query/{class.method}?���������_�������
	 * </pre>
	 * ���:<br>
	 * class - ��� ������ ��� ������, ������ ������ ������ ������������ ���������� {@code packages};<br>
	 * method - ��� ������ ����� ������. 
	 * <p>������� URL ������� c ���������� �����������, �������������� � ������.
	 * ������� ���������� ����� �������.   
	 * <pre>
	 * .../data/query/TestQuery.select?us_name=����
	 * </pre>
	 * <pre>
	 * .../data/query/TestQuery.select?page=0
	 * </pre>
	 * <pre>
	 * .../data/query/TestQuery.select?page=0&amp;size=5
	 * </pre>
	 * 
	 * @param bean ��������� �������
	 * @return ��������� ���������� �������
	 * 
	 * @throws RestException ��������� �� ������ ��� �� �������������
	 */
	@GET
	@Path("query/{object}")
	public ArrayList<HashMap<String, Object>> select(@BeanParam BeanRequest bean) throws RestException {
		String[] names = getNames(bean.getObject());
		String lang = bean.getLang();
		if (names.length != 2) {
			throw new RestException("RST0021E", null, lang);
		}
		Object generator = getObject(names[0], lang);
		try {
			ArrayList<Object> parameters = new ArrayList<Object>();
			Method method = generator.getClass().getMethod(names[1], String.class, MultivaluedMap.class, ArrayList.class);
			String sql = (String) method.invoke(generator, lang, bean.getQueryParams(), parameters);
			return query(sql, parameters, lang);
		} catch (InvocationTargetException e) {
			throw new RestException("RST0001E", null, e.getTargetException(), lang);
		} catch (Exception e) {
			throw new RestException("RST0001E", null, e, lang);
		}
	}
	
	@POST
	@Path("query/{object}")
	public Response execute(@BeanParam BeanRequest bean, ArrayList<HashMap<String, Object>> records) throws RestException {
		String[] names = getNames(bean.getObject());
		String lang = bean.getLang();
		if (names.length != 2) {
			throw new RestException("RST0021E", null, lang);
		}
		Object generator = getObject(names[0], lang);
		try {
			ArrayList<Object> parameters = new ArrayList<Object>();
			Method method = generator.getClass().getMethod(names[1], String.class, MultivaluedMap.class, ArrayList.class, ArrayList.class);
			String sql = (String) method.invoke(generator, lang, bean.getQueryParams(), records, parameters);
			return Response.ok(query(sql, parameters)).build();
		} catch (InvocationTargetException e) {
			throw new RestException("RST0007E", null, e.getTargetException(), lang);
		} catch (Exception e) {
			throw new RestException("RST0007E", null, e, lang);
		}
	}
	
	/**
	 * �������� ���������� �� �������� ��. ���� ����� ����� ���� ������� � ����� ���������� ��� ���������
	 * ���������� � ����������� ���������� ��������. � ������������ ������ ������ ����� ������ ����� �����������
	 * ���������� {@code disableInfo}.  
	 * 
	 * <pre>
	 * http://host:port/name_context/map_servlet/data/info/{method}?���������_�������
	 * </pre>
	 * 
	 * <p>
	 * {@code method} ��� ������ ��������� ����������. ��� ������ ������������� � ��������.
	 * 
	 * <p>
	 * �������������� ������:
	 * <p>
	 * <b>getPrimaryKeys</b> - �������� ���� ���������� �����, ��������� ������:
	 * <ul>
	 * <li>catalog</li>
	 * <li>schema</li>
	 * <li>table</li>
	 * </ul>
	 * ������: �������� ���� ���������� ����� ������� {@code rp.users}
	 * <pre>
	 * .../data/info/getgetPrimaryKeys?schema=rp&amp;table=users
	 * </pre>
	 * 
	 * <p>
	 * <b>getColumns</b> - �������� ���� �������, ��������� ������:
	 * <ul>
	 * <li>catalog</li>
	 * <li>schema</li>
	 * <li>table</li>
	 * <li>column</li>
	 * </ul>
	 * ������: �������� ���� ������� {@code rp.users}
	 * <pre>
	 * .../data/info/getColumns?schema=rp&amp;table=users
	 * </pre>
	 * 
	 * <p>
	 * <b>getTables</b> - �������� ������ ������, ��������� ������:
	 * <ul>
	 * <li>catalog</li>
	 * <li>schema</li>
	 * <li>table</li>
	 * <li>type - ��� �������, ��������� ��������</li>
	 * </ul>
	 * ������: �������� ������� ����� {@code TABLE} � {@code VIEW}
	 * <pre>
	 * .../data/info/getTables?type=TABLE&amp;type=VIEW
	 * </pre>
	 * 
	 * @param bean ��������� �������
	 * @return ��������� ���������� �������
	 * @throws RestException ������ ���������� �������
	 */
	@GET
	@Path("info/{object}")
	public ArrayList<HashMap<String, Object>> info(@BeanParam BeanRequest bean) throws RestException {
		String lang = bean.getLang();
		MultivaluedMap<String, String> query = bean.getQueryParams();
		if (disableInfo) {
			throw new RestException("RST0022E", null, lang);
		}
		String method = bean.getObject(); 
		String catalog = query.getFirst("catalog");
		String schema = query.getFirst("schema");
		String table = query.getFirst("table");
		try {
			ResultSet result;
			switch (method) {
			case "getPrimaryKeys":
				result = database.metadata("getPrimaryKeys", catalog, schema, table);
				ArrayList<String> listFields = new ArrayList<String>();
				while (result.next()) {
					listFields.add(result.getString(4)); // ��� ������� COLUMN_NAME
				}
				primary.replace(getTable(bean.getObject()), listFields);
				result.beforeFirst();
				break;
			case "getColumns":
				String column = query.getFirst("column");
				result = database.metadata("getColumns", catalog, schema, table, column);
				break;
			case "getTables":
				List<String> list = query.get("type");
				String[] types;
				if (list != null) {
					types = list.toArray(new String[list.size()]);
				} else {
					types = null;
				}
				result = database.metadata("getTables", catalog, schema, table, new Object[] {types});
				break;
			default:
				throw new RestException("RST0024E", new Object[] {method}, lang);
			}
			return toArrayList(result);
		} catch (Exception e) {
			throw new RestException("RST0023E", new Object[] {method}, e, lang);
		}
		
	}
	
	private ArrayList<String> getPrimaryKeys(String name, String lang) throws RestException {
		ArrayList<String> list = new ArrayList<String>();
		String[] args = name.replace('.', ' ').split(" ");
		String schema;
		String table;
		switch (args.length) {
		case 1:
			schema = null;
			table = args[0];
			break;
		case 2:
			schema = args[0];
			table = args[1];
			break;
		default:
			// ������ ����������� ����� ������� {0}.
        	throw new RestException("RST0018E", new Object[] {name}, lang);
		}
		ResultSet result;	
		try {
			result = database.metadata("getPrimaryKeys", null, schema, table);
			while (result.next()) {
				list.add(result.getString(4)); // ��� ������� COLUMN_NAME
			}
		} catch (Exception e) {
			// ������ ������ �������.
			throw new RestException("RST0007E", null, e, lang);
		}
		return list;
	}
	
	/**
	 * �������� ��������� ������ SQL ����������.
	 * 
	 * @param name ��� ������
	 * @return ��������� ��������� ������ ��� {@code null}, ���� ����� �� ������
	 */
	private Object getObject(String name, String lang) throws RestException {
		Object object = generators.get(name);
		if (object != null) return object;
		for (String packageClass : packages ) {
			try {
				object = Class.forName(packageClass + '.' + name).newInstance();
				generators.put(name, object);
				return object;
			} catch (Exception e) {
				// �������� ����������
			}
		}
		throw new RestException("RST0005E", new Object[] {name}, lang, 404);
	}
	
	/**
	 * ��������� SQL ������ SELECT.
	 * 
	 * @param sql ������
	 * @param parameters ��������� �������
	 * @param lang ���� �����������
	 * @param list ��������� ���������� �������
	 * @return  
	 * @throws RestException ��������� ������
	 */
	private ArrayList<HashMap<String, Object>> query(String sql, ArrayList<Object> parameters, String lang) throws RestException {
		try {
			ResultSet result = database.select(sql, parameters);
			return toArrayList(result);
		} catch (Exception e) {
			throw new RestException("RST0001E", null, e, lang);
		}
	}
	
	/**
	 * ��������� ResultSet � ArrayList.
	 * 
	 * @param result ��������� ���������� SQL �������
	 * @return ��������� ���������� �������
	 * @throws Exception ������ ����������
	 */
	private ArrayList<HashMap<String, Object>> toArrayList(ResultSet result) throws Exception {
		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>(); 
		ResultSetMetaData metadata = result.getMetaData();
		while (result.next()) {
			HashMap<String, Object> record = new HashMap<String, Object>(); 
			for (int column = 0; column < metadata.getColumnCount(); ) {
				column++;
				record.put(metadata.getColumnName(column), result.getObject(column));
			}
			list.add(record);
		}
		return list;
	}
	
	/**
	 * ��������� SQL ������ INSERT, UPDATE ��� DELETE.
	 * 
	 * @param sql ������
	 * @param parameters ��������������� ��������� �������
	 * @return ��������� ���������� �������
	 * @throws RestException ��������� �� ������ ��� �� �������������
	 */
	private BeanResponse query(String sql, ArrayList<Object> parameters) throws Exception {
		String query = sql.substring(0, sql.indexOf(' ')).toUpperCase();
		ExecuteSet set = database.execute(sql, parameters);
		BeanResponse beanResponse;
		if (set.getKeys() == null) {
			beanResponse = new BeanResponse(query, set.getRecords());
		} else {
			BeanResponseKeys beanResponseKeys = new BeanResponseKeys(query, set.getRecords());
			beanResponseKeys.setKeys(toArrayList(set.getKeys()));
			beanResponse = beanResponseKeys;
		}
		beanResponse.setTimer(set.getTimer());
		return beanResponse;
	}
	
	/**
	 * �������� ������ ��� �������
	 * 
	 * @param name ������ ��� �������� (������ ���) ������� 
	 * 
	 * @return ������ ��� �������
	 */
	private String getTable(String name) {
		if (name.indexOf('.') < 1 && defaultSchema != null) return name + '.' + name;
		return name; 
	}
	
	/**
	 * ������������� ��������� ��� ������� � ������ ����.
	 *  
	 * @param name ��������� ��� ������
	 * @return
	 */
	private String[] getNames(String name) {
		String[] result = new String[2]; 
		int pos = name.indexOf('.');
		switch (pos) {
		case -1:
			result[0] = null; // schema
			result[1] = name; // table
			break;
		case 0:
			result[0] = null; // schema
			result[1] = name.substring(pos + 1); // table
			break;
		default:
			result[0] = name.substring(0, pos); // schema
			result[1] = name.substring(pos + 1); // table
			break;
		}
		return result; 
	}
	
}
