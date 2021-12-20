package fr.donnees.orchestrator.component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import fr.donnees.orchestrator.exception.OrchestratorException;
import fr.donnees.orchestrator.model.ComputeResult;
import fr.donnees.orchestrator.model.ParameterRow;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import fr.donnees.orchestrator.service.OrchextratorCacheService;
import lombok.extern.slf4j.Slf4j;

/**
 * ForkJoinPool recursive task holding SQL step execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @param <V>
 */
@Slf4j
public class SqlStepRun<V> extends RecursiveTask<ComputeResult<Step,V>>   {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7940184536933432381L;
	
	private ApplicationContext applicationContext;

		
	
	private OrchextratorCacheService cacheService;
	

	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	private Step stepToRun;
	
	private ParameterRow parameterRow;
	
	private Long executionId;
	
	@SuppressWarnings("unchecked")
	public SqlStepRun(Step stepToRun,ParameterRow parameterRow,ApplicationContext applicationContext,Long executionId) {
		if(!stepToRun.getStepType().equals(StepType.SQL) && !stepToRun.getStepType().equals(StepType.SQLV) && !stepToRun.getStepType().equals(StepType.SQLIMP) )
			throw new OrchestratorException("SqlStepRun can only run SQL or SQLV or SQLIMP  step type  ",null);
		this.parameterRow=parameterRow;
		this.stepToRun = stepToRun;
		this.applicationContext = applicationContext;
		this.cacheService =this.applicationContext.getBean("orchextratorCacheService",OrchextratorCacheService.class); 
		if(!cacheService.isSqlBeanExist(stepToRun.getSecretName()))
			cacheService.registerBean(stepToRun.getSecretName(), stepToRun.getStepType());
		this.namedParameterJdbcTemplate =this.applicationContext.getBean(stepToRun.getSecretName(),NamedParameterJdbcTemplate.class);
		this.executionId=executionId;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected ComputeResult<Step,V> compute() {
		String impText = stepToRun.getImplicitSequenceNumerator().intValue()==0?"":" of implicit sequence <<"+stepToRun.getImplicitSequenceNumerator()+">>";
		log.info("SqlStepRun:ComputeResult compute step <<"+stepToRun.getStepName()+">> "+impText+" in sequence <<"+stepToRun.getSequenceNumerator()+">>  of batch <<" + stepToRun.getBatchName()+">> in thread <<"+Thread.currentThread().getName()+">>");
	
		stepToRun = stepToRun.setStepState(State.IN_PROGRESS);
		stepToRun.setDebutExecutionStep(LocalDateTime.now());
		cacheService.updateOrSave(stepToRun);
		cacheService.traceStep(stepToRun,executionId);
         
		
		 List<String> sqlVentiled = null;
		
		
		
			try {
				
			  if(stepToRun.getStepType().equals(StepType.SQLV) || stepToRun.getStepType().equals(StepType.SQL) ) {
				  if(stepToRun.getStepInit()!=null && !stepToRun.getStepInit().isEmpty()) {
					namedParameterJdbcTemplate.update("set QUERY_BAND = 'APPLICATION=ORCHESTRATOR;BATCH="+stepToRun.getBatchName()+";SEQNUMBER=" + stepToRun.getSequenceNumerator() + ";STEPNAME="+stepToRun.getStepName()+";STEPPART=INIT;' FOR TRANSACTION;",parameterRow.getNamedParameters());			
					 namedParameterJdbcTemplate.batchUpdate(stepToRun.getStepInit(),new SqlParameterSource[] {parameterRow.getNamedParameters()});	
				  }
			  }
			 	
		     
			 if(stepToRun.getStepType().equals(StepType.SQLV)) {
				 
				 if(stepToRun.getStepMain()!=null && !stepToRun.getStepMain().isEmpty()) {
					 namedParameterJdbcTemplate.update("set QUERY_BAND = 'APPLICATION=ORCHESTRATOR;BATCH="+stepToRun.getBatchName()+";SEQNUMBER=" + stepToRun.getSequenceNumerator() + ";STEPNAME="+stepToRun.getStepName()+";STEPPART=MAIN;' FOR TRANSACTION;",parameterRow.getNamedParameters());
		             sqlVentiled = namedParameterJdbcTemplate.query(stepToRun.getStepMain(),parameterRow.getNamedParameters(),getSqlVentiledExtractor());
				 
		             if(stepToRun.getStepClosure()!=null && !stepToRun.getStepClosure().isEmpty())
		                sqlVentiled = sqlVentiled.stream().map(row -> row+"|"+stepToRun.getStepName()+"_stats|"+stepToRun.getStepClosure()+"|"+stepToRun.getTargetTable()).collect(Collectors.toList());
				 }
			 }
			 
			 if(stepToRun.getStepType().equals(StepType.SQL) || stepToRun.getStepType().equals(StepType.SQLIMP) ) {
				 
				 if(stepToRun.getStepMain()!=null && !stepToRun.getStepMain().isEmpty()) {
					 namedParameterJdbcTemplate.update("set QUERY_BAND = 'APPLICATION=ORCHESTRATOR;BATCH="+stepToRun.getBatchName()+";SEQNUMBER=" + stepToRun.getSequenceNumerator() + ";STEPNAME="+stepToRun.getStepName()+";STEPPART=MAIN;' FOR TRANSACTION;",parameterRow.getNamedParameters());
		             namedParameterJdbcTemplate.batchUpdate(stepToRun.getStepMain(),new SqlParameterSource[] {parameterRow.getNamedParameters()});
				 }
			 }
			 
			
	       
	         if(!stepToRun.getStepType().equals(StepType.SQLV) && stepToRun.getTargetTable() != null && tableHasStats(stepToRun.getTargetTable())) {
	            
	            if(stepToRun.getStepClosure()!=null & !stepToRun.getStepClosure().isEmpty()) {
	            	namedParameterJdbcTemplate.update("set QUERY_BAND = 'APPLICATION=ORCHESTRATOR;BATCH="+stepToRun.getBatchName()+";SEQNUMBER=" + stepToRun.getSequenceNumerator() + ";STEPNAME="+stepToRun.getStepName()+";STEPPART=CLOSURE;' FOR TRANSACTION;",parameterRow.getNamedParameters());
	                namedParameterJdbcTemplate.batchUpdate(stepToRun.getStepClosure(),new SqlParameterSource[] {parameterRow.getNamedParameters()});
	            }
	         }
		    
			}catch(DataAccessException e) {
				 
				log.error("SqlStepRun sql error in step <<"+stepToRun.getStepName()+">>  "+impText+" of sequence <<"+stepToRun.getSequenceNumerator()+">> of batch <<"+stepToRun.getBatchName()+">>",e);
				stepToRun = stepToRun.setStepState(State.IN_ERROR);
				stepToRun.setFinExecutionStep(LocalDateTime.now());
				cacheService.updateOrSave(stepToRun);
				cacheService.traceStep(stepToRun,executionId);
				if(stepToRun.getStepType().equals(StepType.SQLV)) {
					sqlVentiled = new ArrayList<String>(){/**
						 * 
						 */
						private static final long serialVersionUID = 4880571131041279153L;

					{add("1");}};
					return (ComputeResult<Step,V>)new ComputeResult<Step,List<String>>(stepToRun,sqlVentiled);
				}
				return (ComputeResult<Step,V>)new ComputeResult<Step,Integer>(stepToRun,new Integer(1));
			}
		
	
		
		
		
			
		stepToRun = stepToRun.setStepState(State.TERMINATED);
		stepToRun.setFinExecutionStep(LocalDateTime.now());
		cacheService.updateOrSave(stepToRun);
		cacheService.traceStep(stepToRun,executionId);
		
		if(stepToRun.getStepType().equals(StepType.SQLV) ) 
			  return (ComputeResult<Step,V>)new ComputeResult<Step,List<String>>(stepToRun,sqlVentiled);
		
		
			return (ComputeResult<Step,V>)new ComputeResult<Step,Integer>(stepToRun,new Integer(0)) ;
		
		
		
		
	}
	
	private ResultSetExtractor<List<String>>  getSqlVentiledExtractor(){
		
		return new ResultSetExtractor<List<String>>(){
			
			public List<String> extractData(ResultSet rs) throws SQLException, DataAccessException {
				List<String> sqlVentiled = new ArrayList<String>();
				String step;
				while(rs.next()) {
					step = rs.getString(1);
					if(step != null && !step.isEmpty() && !step.equalsIgnoreCase("?"))
					    sqlVentiled.add(step);
				}
				return sqlVentiled;
				
			}
			
		};
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
	
	
	


	


	

}
