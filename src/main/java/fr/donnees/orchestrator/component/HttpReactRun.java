package fr.donnees.orchestrator.component;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.teradata.jdbc.encode.CharSetNames;

import fr.donnees.orchestrator.exception.OrchestratorException;
import fr.donnees.orchestrator.model.ComputeResult;
import fr.donnees.orchestrator.model.ParameterRow;
import fr.donnees.orchestrator.model.RestConnection;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import fr.donnees.orchestrator.service.OrchextratorCacheService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * ForkJoinPool recursive task holding reactif Http  step execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @param <V>
 */
@Slf4j
public class HttpReactRun extends RecursiveTask<ComputeResult<Step,Integer>>   {
	

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3585662605359243240L;



	private ApplicationContext applicationContext;

		
	
	private OrchextratorCacheService cacheService;
	


	private Step stepToRun;
	
	private ParameterRow parameterRow;
	
	private Long executionId;
	
	private RestConnection restConnection;
	
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@SuppressWarnings("unchecked")
	public HttpReactRun(Step stepToRun,ParameterRow parameterRow,ApplicationContext applicationContext,Long executionId) {
		if(!stepToRun.getStepType().equals(StepType.HTTP) && !stepToRun.getStepType().equals(StepType.HTTPIMP) )
			throw new OrchestratorException("HttpReactRun can only run HTTP or HTTPIMP  step type  ",null);
		this.parameterRow=parameterRow;
		this.stepToRun = stepToRun;
		this.applicationContext = applicationContext;
		this.cacheService =this.applicationContext.getBean("orchextratorCacheService",OrchextratorCacheService.class); 		
		this.executionId=executionId;
		if(stepToRun.getStepType().equals(StepType.HTTPIMP) && !cacheService.isHttpBeanExist(stepToRun.getSecretName())) {
			cacheService.registerBean(stepToRun.getSecretName(), stepToRun.getStepType());
		}
		 String httpSecret = stepToRun.getSecretName().split("#")[0]; 
	     String dbSecret = stepToRun.getSecretName().split("#")[1]; 
		this.restConnection=this.applicationContext.getBean(httpSecret,RestConnection.class);
		this.namedParameterJdbcTemplate =this.applicationContext.getBean(dbSecret,NamedParameterJdbcTemplate.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected ComputeResult<Step,Integer> compute() {
		String impText = stepToRun.getImplicitSequenceNumerator().intValue()==0?"":" of implicit sequence <<"+stepToRun.getImplicitSequenceNumerator()+">>";
		log.info("HttpReactRun:ComputeResult compute step <<"+stepToRun.getStepName()+">> "+impText+" in sequence <<"+stepToRun.getSequenceNumerator()+">>  of batch <<" + stepToRun.getBatchName()+">> in thread <<"+Thread.currentThread().getName()+">>");
		
		stepToRun = stepToRun.setStepState(State.IN_PROGRESS);
		stepToRun.setDebutExecutionStep(LocalDateTime.now());
		cacheService.updateOrSave(stepToRun);
		cacheService.traceStep(stepToRun,executionId);
		
		
		
		try {
			 if(stepToRun.getStepType().equals(StepType.HTTP) ) {
			
				 
				  if(stepToRun.getStepInit()!=null && !stepToRun.getStepInit().isEmpty()) {
						namedParameterJdbcTemplate.update("set QUERY_BAND = 'APPLICATION=ORCHESTRATOR;BATCH="+stepToRun.getBatchName()+";SEQNUMBER=" + stepToRun.getSequenceNumerator() + ";STEPNAME="+stepToRun.getStepName()+";STEPPART=INIT;' FOR TRANSACTION;",parameterRow.getNamedParameters());			
						 namedParameterJdbcTemplate.batchUpdate(stepToRun.getStepInit(),new SqlParameterSource[] {parameterRow.getNamedParameters()});	
					  }
				 
		     }
			}catch(DataAccessException e) {
				 
				log.error("HttpReactRun sql error in step <<"+stepToRun.getStepName()+">>  "+impText+" of sequence <<"+stepToRun.getSequenceNumerator()+">> of batch <<"+stepToRun.getBatchName()+">>",e);
				stepToRun = stepToRun.setStepState(State.IN_ERROR);
				stepToRun.setFinExecutionStep(LocalDateTime.now());
				cacheService.updateOrSave(stepToRun);
				cacheService.traceStep(stepToRun,executionId);
				return new ComputeResult<Step,Integer>(stepToRun,new Integer(1)) ;
			}
		
		if(stepToRun.getStepMain()==null || stepToRun.getStepMain().isEmpty()) {
			log.error("HttpReactRun StepMain is empty in  step <<"+stepToRun.getStepName()+">>  "+impText+" of sequence <<"+stepToRun.getSequenceNumerator()+">> of batch <<"+stepToRun.getBatchName()+">>");
			stepToRun = stepToRun.setStepState(State.IN_ERROR);
			stepToRun.setDebutExecutionStep(LocalDateTime.now());
			cacheService.updateOrSave(stepToRun);
			cacheService.traceStep(stepToRun,executionId);
			
			return new ComputeResult<Step,Integer>(stepToRun,new Integer(1)) ;
		}
        
		
		
			
			
		
				
				
			
			URI uri;
			try {
				//uri = new URI(URLDecoder.decode(, StandardCharsets.UTF_8.name()));
				uri =UriComponentsBuilder.fromHttpUrl(URLDecoder.decode(restConnection.getUrlBase()+stepToRun.getStepMain(), StandardCharsets.UTF_8.name())).build().toUri();
				
				
			} catch ( UnsupportedEncodingException e) {
				log.error("HttpReactRun url not valid   for  step <<"+stepToRun.getStepName()+">>  "+impText+" of sequence <<"+stepToRun.getSequenceNumerator()+">> of batch <<"+stepToRun.getBatchName()+">>",e);
				stepToRun = stepToRun.setStepState(State.IN_ERROR);
				stepToRun.setDebutExecutionStep(LocalDateTime.now());
				cacheService.updateOrSave(stepToRun);
				cacheService.traceStep(stepToRun,executionId);
				
				return new ComputeResult<Step,Integer>(stepToRun,new Integer(1)) ;
			}
			
		String key =stepToRun.getStepMain().split("=")[1]; 
		if(stepToRun.getStepMain().contains("&"))
		  key = StringUtils.chop(Arrays.asList(stepToRun.getStepMain().split("&")).stream().map(param -> {return param.split("=")[1];}).reduce("", (subtotal, element) -> subtotal + element + "_"));
		
		
	
		List<String> fluxstate = new ArrayList<String>();
		
		 Flux<Map> httpFlux = WebClient.create()
			      .post()
			      .uri(uri)
			      .headers(headers -> headers.setBasicAuth(restConnection.getUsername(), restConnection.getPassword()))
			      .retrieve()
			      .bodyToFlux(Map.class);
		
		 final String finalKey =key; 
		 httpFlux.log().doOnNext(map -> fluxstate.add((String)map.get(finalKey)))
	        .then()
	        .block();
		 
		 log.info("HttpReactRun:ComputeResult compute step <<"+stepToRun.getStepName()+">> "+impText+" in sequence <<"+stepToRun.getSequenceNumerator()+">>  of batch <<" + stepToRun.getBatchName()+">> fluxstate <<"+fluxstate+">>");
		
		if(fluxstate.contains(State.IN_ERROR.name())) {
			log.error("HttpReactRun http error in step <<"+stepToRun.getStepName()+">>  "+impText+" of sequence <<"+stepToRun.getSequenceNumerator()+">> of batch <<"+stepToRun.getBatchName()+">>");
			stepToRun = stepToRun.setStepState(State.IN_ERROR);
			stepToRun.setDebutExecutionStep(LocalDateTime.now());
			cacheService.updateOrSave(stepToRun);
			cacheService.traceStep(stepToRun,executionId);
			
			return new ComputeResult<Step,Integer>(stepToRun,new Integer(1)) ;
		}
		
		try {
		 if(stepToRun.getStepType().equals(StepType.HTTP) && stepToRun.getTargetTable() != null && tableHasStats(stepToRun.getTargetTable())) {
			 if(stepToRun.getStepClosure()!=null && !stepToRun.getStepClosure().isEmpty()) {
	            namedParameterJdbcTemplate.update("set QUERY_BAND = 'APPLICATION=ORCHESTRATOR;BATCH="+stepToRun.getBatchName()+";SEQNUMBER=" + stepToRun.getSequenceNumerator() + ";STEPNAME="+stepToRun.getStepName()+";STEPPART=CLOSURE;' FOR TRANSACTION;",parameterRow.getNamedParameters());	         
	            namedParameterJdbcTemplate.batchUpdate(stepToRun.getStepClosure(),new SqlParameterSource[] {parameterRow.getNamedParameters()});
			 }
	     }
		}catch(DataAccessException e) {
			 
			log.error("HttpReactRun sql error in step <<"+stepToRun.getStepName()+">>  "+impText+" of sequence <<"+stepToRun.getSequenceNumerator()+">> of batch <<"+stepToRun.getBatchName()+">>",e);
			stepToRun = stepToRun.setStepState(State.IN_ERROR);
			stepToRun.setFinExecutionStep(LocalDateTime.now());
			cacheService.updateOrSave(stepToRun);
			cacheService.traceStep(stepToRun,executionId);
			return new ComputeResult<Step,Integer>(stepToRun,new Integer(1)) ;
		}
			
		stepToRun = stepToRun.setStepState(State.TERMINATED);
		stepToRun.setFinExecutionStep(LocalDateTime.now());
		cacheService.updateOrSave(stepToRun);
		cacheService.traceStep(stepToRun,executionId);
		
	
		
		
			return new ComputeResult<Step,Integer>(stepToRun,new Integer(0)) ;
		
		
			
		
	}
	
	
	private boolean tableHasStats(String tableName) {
		try {
			
			namedParameterJdbcTemplate.queryForRowSet("help stats "+tableName+";",parameterRow.getNamedParameters());
			return true;
	   }catch(DataAccessException e) {
		    log.error("tableHasStats: table  <<"+tableName+">> has no stats.",e);
		    return false;		 
			}
	}
	
  private boolean isEncodedIn(String path,String encodeName) {
	  try {
			
			String pathencode =new String(path.getBytes(encodeName),encodeName); 
			return true;
		} catch (UnsupportedEncodingException e) {
			
			
			return false;
		}	
  }
	
  boolean isValidURL(String url) {
	  try {
	    new URI(url).parseServerAuthority();
	    return true;
	  } catch ( URISyntaxException e) {
	    return false;
	  }
	}
	


	


	

}
