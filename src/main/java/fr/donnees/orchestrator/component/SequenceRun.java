package fr.donnees.orchestrator.component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationContext;

import fr.donnees.orchestrator.model.ComputeResult;
import fr.donnees.orchestrator.model.ParameterRow;
import fr.donnees.orchestrator.model.Sequence;
import fr.donnees.orchestrator.model.SequenceType;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import fr.donnees.orchestrator.service.OrchextratorCacheService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ForkJoinPool recursive task holding sequence execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @param <V>
 */
@Slf4j
public class SequenceRun<V> extends RecursiveTask<ComputeResult<Sequence,V>>   {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7940184536933432381L;
	
	private ApplicationContext applicationContext;

	
	private ForkJoinPool forkJoinPool;
	
	
	private OrchextratorCacheService cacheService;
		
	private Sequence sequenceToRun;
	
	private ParameterRow parameterRow;
	
	List<String> cumuleStepsForImplicitSequence ;
	
	private Long executionId;
	
	@SuppressWarnings("unchecked")
	public SequenceRun(Sequence sequenceToRun,ParameterRow parameterRow,ApplicationContext applicationContext,Long executionId) {
		this.sequenceToRun = sequenceToRun;
		this.parameterRow=parameterRow;
		this.applicationContext = applicationContext;
		this.forkJoinPool=this.applicationContext.getBean("OrchestratorForkJoinPool",ForkJoinPool.class);
		this.cacheService =this.applicationContext.getBean("orchextratorCacheService",OrchextratorCacheService.class); 
		this.cumuleStepsForImplicitSequence = Collections.synchronizedList(new ArrayList<String>());
		this.executionId=executionId;

	}

	@SuppressWarnings("unchecked")
	@Override
	protected ComputeResult<Sequence,V> compute() {
	   String impText = sequenceToRun.getImplicitSequenceNumerator().intValue()==0?"":" implicit sequence <<"+sequenceToRun.getImplicitSequenceNumerator()+">>";
	   log.info("SequenceRun:ComputeResult compute "+impText+" of sequence  <<"+sequenceToRun.getSequenceNumerator()+">>   of batch <<" + sequenceToRun.getBatchName()+">> in thread <<"+Thread.currentThread().getName()+">>");
	
		
		sequenceToRun = sequenceToRun.setSequenceState(State.IN_PROGRESS);
		sequenceToRun.setDebutExecutionSequence(LocalDateTime.now());
		cacheService.updateOrSave(sequenceToRun);
		cacheService.traceSequence(sequenceToRun,executionId);
		
		Boolean isSequenceInError = computeSteps(sequenceToRun.getSteps());
		
	   
		
		if(isSequenceInError) {
			sequenceToRun = sequenceToRun.setSequenceState(State.IN_ERROR);	
			sequenceToRun.setFinExecutionSequence(LocalDateTime.now());
			cacheService.updateOrSave(sequenceToRun);
			cacheService.traceSequence(sequenceToRun,executionId);
			if(sequenceToRun.getSequenceType().equals(SequenceType.IMPLICIT)) {
				List<String> errorInList = new ArrayList<String>();
				  errorInList.add("1");
				return (ComputeResult<Sequence,V>)new ComputeResult<Sequence,List<String>>(sequenceToRun,errorInList);
			}
			
			return   (ComputeResult<Sequence,V>)new ComputeResult<Sequence,Integer>(sequenceToRun,new Integer(1));
	    }
		
		
			
		sequenceToRun = sequenceToRun.setSequenceState(State.TERMINATED);
		sequenceToRun.setFinExecutionSequence(LocalDateTime.now());
		cacheService.updateOrSave(sequenceToRun);
		cacheService.traceSequence(sequenceToRun,executionId);
		
		if(sequenceToRun.getSequenceType().equals(SequenceType.IMPLICIT))
			return (ComputeResult<Sequence,V>)new ComputeResult<Sequence,List<String>>(sequenceToRun,cumuleStepsForImplicitSequence);
			
		return (ComputeResult<Sequence,V>)new ComputeResult<Sequence,Integer>(sequenceToRun,new Integer(0)) ;
	}
	
	private boolean computeSteps(List<Step> steps) {
		List<ForkJoinTask<ComputeResult<Step,Integer>>> sqlhttpTasks= Collections.synchronizedList(new ArrayList<ForkJoinTask<ComputeResult<Step,Integer>>>());
		List<ForkJoinTask<ComputeResult<Step,List<String>>>> sqlVTasks= Collections.synchronizedList(new ArrayList<ForkJoinTask<ComputeResult<Step,List<String>>>>());
		//try {
		
		steps.parallelStream().map(step ->{
	
		  if(step.getStepType().equals(StepType.SQL) || step.getStepType().equals(StepType.SQLIMP) ) {
			  SqlStepRun<Integer> sqlStepRun = new SqlStepRun<Integer>(step,parameterRow,this.applicationContext,this.executionId);
				ForkJoinTask<ComputeResult<Step,Integer>> result = forkJoinPool.submit(sqlStepRun);
				return sqlhttpTasks.add(result);
				
			  }
			  
			 if(step.getStepType().equals(StepType.SQLV)) {
				 SqlStepRun<List<String>> sqlStepRun = new SqlStepRun<List<String>>(step,parameterRow,this.applicationContext,this.executionId);
					ForkJoinTask<ComputeResult<Step,List<String>>> result = forkJoinPool.submit(sqlStepRun);
					return sqlVTasks.add(result);
			 }	
			 
			 
			  if(step.getStepType().equals(StepType.HTTP) || step.getStepType().equals(StepType.HTTPIMP) ) {
				  HttpReactRun httpStepRun = new HttpReactRun(step,parameterRow,this.applicationContext,this.executionId);
					ForkJoinTask<ComputeResult<Step,Integer>> result = forkJoinPool.submit(httpStepRun);
					return sqlhttpTasks.add(result);
					
				  }
			 
			 
			 return true;
	
		}).allMatch(l -> l==true);
		
		boolean isSequenceDone = false;
		boolean isAtLeastOneInError = false;
		
		while(!isSequenceDone && !isAtLeastOneInError) {
			
			Map<Boolean, List<ForkJoinTask<ComputeResult<Step,Integer>>>> sqlTasksDone = sqlhttpTasks.parallelStream()
		            .collect(Collectors.partitioningBy(task -> task.isDone()));
			
			Map<Boolean, List<ForkJoinTask<ComputeResult<Step,List<String>>>>> sqlVTasksDone = sqlVTasks.parallelStream()
		            .collect(Collectors.partitioningBy(task -> task.isDone()));

			
			if(!sqlTasksDone.get(true).isEmpty()) {			
				
				isAtLeastOneInError=Flux.fromStream(sqlhttpTasks.parallelStream()).filter(task -> task.isDone()).any(task -> {
					ComputeResult<Step,Integer> ret = wrapExceptionInt(task);				
					
					return task!=null&&ret!=null?ret.getResult().intValue()==1:true;
						}).block();
				
			}	
			
			if(!sqlVTasksDone.get(true).isEmpty()) {
				isAtLeastOneInError=Flux.fromStream(sqlVTasks.parallelStream()).filter(task -> task.isDone()).any(task ->  task!=null&&wrapExceptionList(task)!=null&&!wrapExceptionList(task).getResult().isEmpty()?"1".equalsIgnoreCase(wrapExceptionList(task).getResult().get(0)):false).block();
			}
			
			isSequenceDone = sqlTasksDone.get(false).isEmpty()&& sqlVTasksDone.get(false).isEmpty();
			
		}
		
		
		cumuleStepsForImplicitSequence.addAll(sqlVTasks.stream().map(task -> wrapExceptionList(task).getResult()).reduce(new ArrayList<String>(),(accum,element)-> {accum.addAll(element); return accum;} ));
		
		
	       List<Step> terminatedSteps = sqlhttpTasks.parallelStream().map(task -> wrapExceptionInt(task).getResultType()).collect(Collectors.toList());
	       
	       terminatedSteps.addAll(sqlVTasks.parallelStream().map(task -> wrapExceptionList(task).getResultType()).collect(Collectors.toList()));
	       
	       sequenceToRun.setSteps(terminatedSteps);
		
		
		if(isAtLeastOneInError) {
			sqlhttpTasks.parallelStream().filter(task -> !task.isDone()).forEach(task -> task.cancel(true));
			sqlVTasks.parallelStream().filter(task -> !task.isDone()).forEach(task -> task.cancel(true));
			return isAtLeastOneInError;
		}
		
	
		
       
       
		
		
		return false;
	
	
	}
	
	private ComputeResult<Step,Integer> wrapExceptionInt(ForkJoinTask<ComputeResult<Step,Integer>> task) {
	    try {
	    	if(task == null)
	    		log.error("SequenceRun:computeSteps:wrapExceptionInt task is null");
	        return  task.get();
	    } catch ( InterruptedException |ExecutionException|NullPointerException   e) {
	    	String impText = sequenceToRun.getImplicitSequenceNumerator().intValue()==0?"":"in implicit sequence <<"+sequenceToRun.getImplicitSequenceNumerator()+">>";
	    	log.error("SequenceRun:computeSteps:wrapExceptionInt exception for batch <<"+sequenceToRun.getBatchName()+">> "+impText+" of sequence  <<"+sequenceToRun.getSequenceNumerator()+">> ",e);
	    	return null;
	    }
	    
	    
	}
	
	private ComputeResult<Step,List<String>> wrapExceptionList(ForkJoinTask<ComputeResult<Step,List<String>>> task) {
	    try {
	    	if(task == null)
	    		log.error("SequenceRun:computeSteps:wrapExceptionList task is null");
	    	return task.get();	    	
	    		
	    } catch (InterruptedException |ExecutionException|NullPointerException  e) {
	    	String impText = sequenceToRun.getImplicitSequenceNumerator().intValue()==0?"":"in implicit sequence <<"+sequenceToRun.getImplicitSequenceNumerator()+">>";
	    	log.error("SequenceRun:computeSteps:wrapExceptionList exception for batch <<"+sequenceToRun.getBatchName()+">> "+impText+" of sequence  <<"+sequenceToRun.getSequenceNumerator()+">> ",e);
	    	return null;
	    }
	}
	


	
	
	

}
