package com.hmdm.persistence;
import com.hmdm.persistence.domain.DeviceState;
import com.hmdm.persistence.mapper.DeviceStateMapper;
import com.hmdm.rest.json.agent.AgentConfigurationSupport;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;
import org.junit.Assume;
import java.net.URI;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.w3c.dom.Element;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import static org.junit.Assert.*;

/** Runs against an owned, empty PostgreSQL14 contract database in CI/local
 * release qualification. It never accepts a provider/production database URL. */
public class ConfigurationSnapshotPersistenceTest {
    private static final String NUMBER="synthetic-snapshot-device";
    private DeviceState state(long time,String caps){DeviceState s=new DeviceState();s.setDeviceNumber(NUMBER);s.setUpdatedAt(time);s.setAgentVersion("synthetic");s.setKioskActive(true);s.setConfigurationCapabilities(AgentConfigurationSupport.serializeCapabilities(caps));return s;}
    private String[] read(Connection c)throws Exception{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select d.agentCapabilities,s.updatedAt,s.configurationCapabilities from devices d join device_state s on s.deviceNumber=d.number where d.number='synthetic-snapshot-device'")){assertTrue(r.next());return new String[]{r.getString(1),r.getString(2),r.getString(3)};}}
    private String snapshotDDL()throws Exception{
        DocumentBuilderFactory xml=DocumentBuilderFactory.newInstance();xml.setNamespaceAware(true);xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);xml.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");xml.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        org.w3c.dom.Document doc=xml.newDocumentBuilder().parse(Paths.get("../server/src/main/resources/liquibase/db.changelog.xml").toFile());org.w3c.dom.NodeList sets=doc.getElementsByTagNameNS("*","changeSet");
        for(int i=0;i<sets.getLength();i++){Element set=(Element)sets.item(i);if(set.getAttribute("id").equals("26.10.21-configuration-capability-snapshot")){
            org.w3c.dom.NodeList statements=set.getElementsByTagNameNS("*","sql");assertEquals(1,statements.getLength());String sql=statements.item(0).getTextContent().trim();assertTrue(sql.startsWith("ALTER TABLE device_state ADD COLUMN configurationCapabilities TEXT"));return sql;
        }}
        throw new AssertionError("Missing actual snapshot migration");
    }
    @Test public void actualMapperKeepsCapabilitiesAndStateAtomicAndInvalidatesIncompleteFrames()throws Exception{
        String url=System.getenv("PROVIDER_TEST_DATABASE_URL");Assume.assumeNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://"));URI target=new URI(url.substring(5));
        assertEquals("127.0.0.1",target.getHost());assertEquals("/mdmesh_configuration_contract",target.getPath());assertNull(target.getUserInfo());assertNull(target.getQuery());assertNull(target.getFragment());
        String schema="configuration_contract_"+UUID.randomUUID().toString().replace("-","");assertTrue(schema.matches("configuration_contract_[a-f0-9]{32}"));
        try(Connection root=DriverManager.getConnection(url,"dmtest",null);Statement ddl=root.createStatement()){
            ddl.execute("create schema "+schema);ddl.execute("set search_path to "+schema);
            try{
                ddl.execute("create table devices(number text primary key,agentCapabilities text)");
                ddl.execute("create table device_state(deviceNumber varchar(255) primary key,battery int,charging boolean,locked boolean,kioskActive boolean,androidRelease varchar(32),lastBootAt bigint,updatedAt bigint not null,agentVersion varchar(64),powerMode varchar(16),telemetry text)");
                ddl.execute(snapshotDDL());
                ddl.execute("insert into devices(number) values('synthetic-snapshot-device')");
                Configuration cfg=new Configuration(new Environment("synthetic",new JdbcTransactionFactory(),new UnpooledDataSource("org.postgresql.Driver",url+"?currentSchema="+schema,"dmtest",null)));cfg.addMapper(DeviceStateMapper.class);SqlSessionFactory factory=new SqlSessionFactoryBuilder().build(cfg);
                String first="{\"policy\":[\"brightness\"]}",next="{\"policy\":[\"screenTimeout\"]}";
                try(SqlSession session=factory.openSession(true)){
                    DeviceStateMapper mapper=session.getMapper(DeviceStateMapper.class);
                    mapper.updateCapabilitiesWithoutState(NUMBER,first);assertNull(mapper.findByDeviceNumber(NUMBER));
                    mapper.upsertWithCapabilities(state(1000,first),first);assertEquals("[\"policy.brightness\"]",mapper.findByDeviceNumber(NUMBER).getConfigurationCapabilities());assertArrayEquals(new String[]{first,"1000","[\"policy.brightness\"]"},read(root));
                    mapper.updateCapabilitiesWithoutState(NUMBER,next);assertArrayEquals(new String[]{next,"1000",null},read(root));assertNull(AgentConfigurationSupport.snapshot(mapper.findByDeviceNumber(NUMBER)).getUpdatedAt());
                    mapper.upsertWithCapabilities(state(2000,null),null);assertArrayEquals(new String[]{next,"2000",null},read(root));
                    mapper.upsertWithCapabilities(state(3000,first),first);mapper.updateCapabilitiesWithoutState(NUMBER,null);assertArrayEquals(new String[]{first,"3000",null},read(root));
                    mapper.upsertWithCapabilities(state(4000,first),first);
                }
                // A second connection cannot see a new legacy advertisement
                // paired with old snapshot metadata before commit; rollback
                // restores both parts, not just the state row.
                try(SqlSession transaction=factory.openSession(false)){
                    DeviceStateMapper mapper=transaction.getMapper(DeviceStateMapper.class);mapper.upsertWithCapabilities(state(5000,next),next);assertArrayEquals(new String[]{first,"4000","[\"policy.brightness\"]"},read(root));transaction.rollback();assertArrayEquals(new String[]{first,"4000","[\"policy.brightness\"]"},read(root));
                    mapper.upsertWithCapabilities(state(5000,next),next);transaction.commit();assertArrayEquals(new String[]{next,"5000","[\"policy.screenTimeout\"]"},read(root));
                }
                try(SqlSession session=factory.openSession(true)){DeviceStateMapper mapper=session.getMapper(DeviceStateMapper.class);mapper.upsert(state(6000,next));assertArrayEquals(new String[]{next,"6000",null},read(root));}
            }finally{ddl.execute("drop schema "+schema+" cascade");}
        }
    }
}
