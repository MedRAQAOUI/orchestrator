package fr.donnees.orchestrator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.jdbc.JdbcOperationsDependsOnPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.SchemaManagement;
import org.springframework.boot.jdbc.SchemaManagementProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ForkJoinPoolFactoryBean;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.client.util.ClientStateListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import fr.donnees.orchestrator.component.BatchRun;
import fr.donnees.orchestrator.exception.OrchestratorUncaughtExceptionHandler;
import fr.donnees.orchestrator.model.Batch;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;



@Configuration
@Slf4j
public class ConfigOrchestartor {
	
	
	
	@Value("${spring.flyway.placeholders.sidpdml}")
	private String sidpdml;
	@Value("${spring.flyway.placeholders.sidstm}")
	private String sid_stm;
	@Value("${spring.flyway.placeholders.service_name}")
	private String service;
	
	@Value("${KUBERNETES_NAMESPACE:}")
	private String namespace ;
	
	@Value("${ACTIVE_PROFILE:local}")
	private String activeProfile;

	
	@Bean(name = "dsMaster")
	@ConfigurationProperties(prefix = "spring.datasource")
	@Primary
	public DataSource primaryDataSource() {
		return DataSourceBuilder.create().build();
	}
	
	
	@Bean(name = "jdbcTemplateApp") 
	public JdbcTemplate masterJdbcTemplate(@Qualifier("dsMaster") DataSource dsMaster) {
		return new JdbcTemplate(dsMaster);
	}
	
	@Bean(name = "namedParameterJdbcTemplate") 
	public NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Qualifier("dsMaster") DataSource dsMaster) {
		return new NamedParameterJdbcTemplate(dsMaster);
	}
	
	   
	    
	  
	 

	 

		
	/**
	 * hazelcast : In-Memory Data Grid cache service to save batch execution state 
	 * over kubernetes cluster
	 * @author MOHAMED RAQAOUI (MR6E5DFN)
	 *
	 * @return
	 */
	@Bean
	@Profile("!local")
	Config config() {
	   Config config = new Config();
	   
	 
	   
	   log.info("hazelcast-service:config namespace="+namespace);
	   
	   config.getCPSubsystemConfig()
	      .setCPMemberCount(3)
	      .setGroupSize(3)
	      .setSessionTimeToLiveSeconds(300)
	      .setSessionHeartbeatIntervalSeconds(5)
	      .setMissingCPMemberAutoRemovalSeconds(900)
	      .setFailOnIndeterminateOperationState(false);
	   
	   config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
	   
	   config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
	
	   config.getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(true)
	         .setProperty("namespace", namespace)
	         .setProperty("service-name", "pml-orchestrator-"+activeProfile);
	   
	   return config;
	}

	/**
	 * hazelcast : In-Memory Data Grid cache service to save batch execution state 
	 * localhost configuration
	 * @author MOHAMED RAQAOUI (MR6E5DFN)
	 *
	 * @return
	 */
@Bean(name="hazelcastInstance")
@Profile("local")
HazelcastInstance  localconfig() {
	
	Config config = new Config();
	

   
   config.getNetworkConfig().setPort(5701);
   config.getNetworkConfig().setPortAutoIncrement(true);
   config.getCPSubsystemConfig()
      .setCPMemberCount(3)
      .setGroupSize(3)
      .setSessionTimeToLiveSeconds(300)
      .setSessionHeartbeatIntervalSeconds(5)
      .setMissingCPMemberAutoRemovalSeconds(14400)
      .setFailOnIndeterminateOperationState(false);
   

   
   config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
	   //config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
	   TcpIpConfig tcpIpConfig = config.getNetworkConfig().getJoin().getTcpIpConfig();
	    tcpIpConfig.setEnabled(true);
	    tcpIpConfig.setMembers( Collections.singletonList("127.0.0.1"));
	    config.setInstanceName("hazelcastInstance");
	    HazelcastInstance hz1 = Hazelcast.newHazelcastInstance(config);
	    config.setInstanceName("hazelcastInstanceSecond");
	    HazelcastInstance hz2 = Hazelcast.newHazelcastInstance(config);
	    config.setInstanceName("hazelcastInstanceThird");
	    HazelcastInstance hz3 = Hazelcast.newHazelcastInstance(config);
	    return hz1;
}


@Bean(name="OrchestratorForkJoinPoolFactoryBean")
ForkJoinPoolFactoryBean  forkJoinPoolconfig() {
	//int parallelism = Runtime.getRuntime().availableProcessors()*4/5;
	int parallelism = Runtime.getRuntime().availableProcessors();
	log.info("ConfigOrchestartor:forkJoinPoolconfig  parallelism for OrchestratorForkJoinPoolFactoryBean  : " + parallelism);
	 ForkJoinPoolFactoryBean createOrderPoolFactoryBean = new ForkJoinPoolFactoryBean();
	    createOrderPoolFactoryBean.setParallelism(parallelism);
	    createOrderPoolFactoryBean.setAsyncMode(true);
	    createOrderPoolFactoryBean.setUncaughtExceptionHandler(new OrchestratorUncaughtExceptionHandler());
	    createOrderPoolFactoryBean.setThreadFactory(p -> {
	        final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
	        worker.setName("orchestrator-pool-" + worker.getPoolIndex());
	        return worker;
	    });
	    return createOrderPoolFactoryBean;
}
/**
 * ForkJoinPool for batch execution 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @return ForkJoinPool
 */
@Bean(name="OrchestratorForkJoinPool")
ForkJoinPool ForkJoinPoolBean() {
	return forkJoinPoolconfig().getObject();
}

/**
 * Reactif flux monitor batch execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @return Sinks.Many<Batch>
 */
@Bean(name="sinkBatch")
Sinks.Many<Batch> createSinkBatch(){
	
	final Sinks.Many<Batch> sinkBatch = Sinks.many().multicast().onBackpressureBuffer();
	return sinkBatch;
	
}



}



	



