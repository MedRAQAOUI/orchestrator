package fr.donnees.orchestrator.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.utility.ListIterate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import fr.donnees.orchestrator.component.BatchRun;
import fr.donnees.orchestrator.exception.OrchestratorException;
import fr.donnees.orchestrator.model.Batch;
import fr.donnees.orchestrator.model.ComputeResult;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;


/**
 * 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Service
@Slf4j
public class RunningService   {
	
	//final Sinks.Many<Batch> sink = Sinks.many().multicast().onBackpressureBuffer();
	@Autowired
	private ApplicationContext applicationContext;
	
	
	
	@Autowired
	@Qualifier("orchextratorCacheService")
	OrchextratorCacheService cacheService;
	

	
	@Autowired
	@Qualifier("OrchestratorForkJoinPool")
	ForkJoinPool orchestratorForkJoinPool;
	

	
	

	
	@Async("threadPoolTaskExecutor")
	public void runBatch(Batch batchToRun) {
		log.info("RunningService:runBatch batchName <<" + batchToRun.getBatchName()+">> run in thread <<"+Thread.currentThread().getName()+">>");
		try {
		
		Map<StepType,String> secretNames = getSecretNameByStepType(batchToRun);
		
		
	      
	      for (Map.Entry<StepType,String> entry : secretNames.entrySet()) {
	    	  
	    	  
	    	 int ret= cacheService.registerBean(entry.getValue(), entry.getKey());
	    	 if(ret>0) {
	    		 batchToRun.setBatchState(State.IN_ERROR);
	    		 cacheService.updateOrSave(batchToRun);
				 batchToRun.setFinExecutionBatch(LocalDateTime.now());
				 cacheService.traceBatch(batchToRun);
	    	 }
	    	  
	        	   
	      }
	     	        
	     
		
		
		
		BatchRun batchRun = new BatchRun(batchToRun,this.applicationContext);
		
    		
		
		ForkJoinTask<ComputeResult<Batch,Integer>> result = orchestratorForkJoinPool.submit(batchRun);

	//	if(result.isDone()) {
			
				ComputeResult<Batch,Integer> compResult =((ComputeResult<Batch,Integer>)result.get()); 
				log.info("RunningService:runBatch batch " + batchToRun.getBatchName()+" : result = "+compResult.getResult());
				batchToRun = ((Batch)compResult.getResultType());
				
			
			
				
			} catch (InterruptedException | ExecutionException |KubernetesClientException e) {
				batchToRun.setBatchState(State.IN_ERROR);
	    		 cacheService.updateOrSave(batchToRun);
				 batchToRun.setFinExecutionBatch(LocalDateTime.now());
				 cacheService.traceBatch(batchToRun);
				throw new OrchestratorException("RunningService:runBatch exception for batch <<"+batchToRun.getBatchName()+">>",e);
			}
	//	} 
		
	}
	
     private Map<StepType,String> getSecretNameByStepType(Batch batchToRun){
		
		List<Step> steps =batchToRun.getSequences().stream().map(seq -> {return seq.getSteps();}).collect(ArrayList::new, List::addAll, List::addAll);
		
		List<Step> distinctSteps = ListIterate.distinct(
				steps, HashingStrategies.fromFunction(Step::getSecretName));
		
		return distinctSteps.stream().collect(Collectors.toMap(Step::getStepType, step -> step.getSecretName()));
		
	}
	
     
   


}
