package fr.donnees.orchestrator.component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationContext;

import fr.donnees.orchestrator.model.Batch;
import fr.donnees.orchestrator.model.ComputeResult;
import fr.donnees.orchestrator.model.Sequence;
import fr.donnees.orchestrator.model.SequenceType;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import fr.donnees.orchestrator.service.OrchextratorCacheService;
import lombok.extern.slf4j.Slf4j;

/**
 * ForkJoinPool recursive task holding barch execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Slf4j
public class BatchRun extends RecursiveTask<ComputeResult<Batch,Integer>>   {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7940184536933432381L;
	
	private ApplicationContext applicationContext;

	
	private ForkJoinPool forkJoinPool;
	
	
	private OrchextratorCacheService cacheService;
	
	private Batch batchToRun;
	
	
	
	@SuppressWarnings("unchecked")
	public BatchRun(Batch batchToRun,ApplicationContext applicationContext) {
		this.batchToRun = batchToRun;
		this.applicationContext = applicationContext;
		this.forkJoinPool=this.applicationContext.getBean("OrchestratorForkJoinPool",ForkJoinPool.class); 
		this.cacheService =this.applicationContext.getBean("orchextratorCacheService",OrchextratorCacheService.class); 

	}

	@Override
	protected ComputeResult<Batch,Integer> compute() {
		log.info("BatchRun:ComputeResult compute batch : " + batchToRun.getBatchName()+" in thread : "+Thread.currentThread().getName());
		
		
	
		cacheService.updateOrSave(batchToRun);
		cacheService.traceBatch(batchToRun);
		
		
		 
		List<Sequence> seqs = batchToRun.getSequences().stream().collect(Collectors.toList());
		
		for(Sequence seq:seqs) {
			
			ComputeResult<Sequence,Integer> ret = computeSequence(seq);
			int index = batchToRun.getSequences().indexOf(ret.getResultType());				
	    	batchToRun.getSequences().set(index, ret.getResultType());
			if(ret.getResult().intValue()==1) {
		    	batchToRun.setBatchState(State.IN_ERROR);		    	
				cacheService.updateOrSave(batchToRun);
				batchToRun.setFinExecutionBatch(LocalDateTime.now());
				cacheService.traceBatch(batchToRun);
				 
				return  new ComputeResult<Batch,Integer>(batchToRun,new Integer(1));
		    }
			
		} 
		
		batchToRun.setBatchState(State.TERMINATED);
		cacheService.updateOrSave(batchToRun);
		batchToRun.setFinExecutionBatch(LocalDateTime.now());
		cacheService.traceBatch(batchToRun);
		
		return  new ComputeResult<Batch,Integer>(batchToRun,new Integer(0));
	}

	
	private ComputeResult<Sequence,Integer> computeSequence(Sequence sequence) {	
		
	try {	
		if(sequence.getSequenceType().equals(SequenceType.ORDINARY)) {
			SequenceRun<Integer> sequenceRun = new SequenceRun<Integer>(sequence,batchToRun.getParameterRow(),this.applicationContext,batchToRun.getExecutionId());
			ForkJoinTask<ComputeResult<Sequence,Integer>> result = forkJoinPool.submit(sequenceRun);
			
			
			return result.get();			
		}else {
			
			SequenceRun<List<String>> sequenceRun = new SequenceRun<List<String>>(sequence,batchToRun.getParameterRow(),this.applicationContext,batchToRun.getExecutionId());
			ForkJoinTask<ComputeResult<Sequence,List<String>>> result = forkJoinPool.submit(sequenceRun);
			ComputeResult<Sequence,List<String>> compResult =((ComputeResult<Sequence,List<String>>)result.get());
			
			if(!compResult.getResult().isEmpty() && compResult.getResult().size() == 1 && "1".equalsIgnoreCase(compResult.getResult().get(0)))
				return new ComputeResult<Sequence,Integer>(compResult.getResultType(),Integer.valueOf(compResult.getResult().get(0)));
			
			if(compResult.getResult() == null || compResult.getResult().isEmpty())
				return new ComputeResult<Sequence,Integer>(compResult.getResultType(),Integer.valueOf(0));
			
		
			
			 List<Step> steps = new ArrayList<Step>();
			 
			 compResult.getResult().forEach(step ->{
				 log.info("BatchRun:ComputeResult  batch : " + batchToRun.getBatchName()+" imp step <<"+step+">>");
				 steps.add(new Step(sequence.getBatchName(),sequence.getSequenceNumerator(),1,step.split("\\|")[0],"",StepType.valueOf(step.split("\\|")[1]),step.split("\\|")[2],null,null,step.split("\\|")[3]/* stepMain */,null,null,null,State.IN_CONSTRUCT));
			 });
			
			 Sequence implicitSeq = null;
			if(!steps.isEmpty())
			  implicitSeq = new Sequence(sequence.getBatchName(),sequence.getSequenceNumerator(),1,SequenceType.ORDINARY,steps,null,null,State.IN_CONSTRUCT);
			
			batchToRun.getSequences().add(implicitSeq);
			cacheService.updateOrSave(batchToRun);
			
			
			
			List<String> stepsForStatsBrut = new ArrayList<String>();
			
			compResult.getResult().forEach(step ->{
				 log.info("BatchRun:ComputeResult  batch : " + batchToRun.getBatchName()+" imp step stats <<"+step+">>");
				 if(StepType.SQLIMP.toString().equals(step.split("\\|")[1]) || StepType.HTTPIMP.toString().equals(step.split("\\|")[1])) {
					 stepsForStatsBrut.add(step.split("\\|")[2]+"|"+step.split("\\|")[4]+"|"+step.split("\\|")[5]+"|"+step.split("\\|")[6]+"|"+step.split("\\|")[1]);
				 }			 
				 
			 });
			
			List<String> stepsForStatsDistinct = stepsForStatsBrut.stream().distinct().collect(Collectors.toList());
			
			stepsForStatsDistinct.stream().forEach(step -> {
				 log.info("BatchRun:ComputeResult  batch : " + batchToRun.getBatchName()+"  stepsForStatsDistinct : <<"+step+">>");
			 });
			
			List<Step> stepsForStats = new ArrayList<Step>();
			 
			stepsForStatsDistinct.forEach(step ->{
				 log.info("BatchRun:ComputeResult  batch : " + batchToRun.getBatchName()+" imp step stats <<"+step+">>");	
				 String secretName =step.split("\\|")[0];
				 if(StepType.HTTPIMP.name().contentEquals(step.split("\\|")[4]))
					 secretName = secretName.split("#")[1];
					 stepsForStats.add(new Step(sequence.getBatchName(),sequence.getSequenceNumerator(),2,step.split("\\|")[1],"",StepType.SQLIMP,secretName,step.split("\\|")[3],null,null/* stepMain */,step.split("\\|")[2],null,null,State.IN_CONSTRUCT));
			
			 });
			 
			
			 
			
			Sequence implicitSeqStats = null;
			 
			 if(!stepsForStats.isEmpty())
			  implicitSeqStats = new Sequence(sequence.getBatchName(),sequence.getSequenceNumerator(),2,SequenceType.ORDINARY,stepsForStats,null,null,State.IN_CONSTRUCT);
			 
			 batchToRun.getSequences().add(implicitSeqStats);
				cacheService.updateOrSave(batchToRun);
				
			//compute imp sequence	 implicitSeq
				ComputeResult<Sequence,Integer> ret = null;
				int index = 0;
			if(implicitSeq!=null) {
				ret = computeSequence(implicitSeq);
				
				 index = batchToRun.getSequences().indexOf(ret.getResultType());				
		    	batchToRun.getSequences().set(index, ret.getResultType());
				
				if(ret.getResult().intValue()==1)
					return new ComputeResult<Sequence,Integer>(sequence,ret.getResult());
			}
		    //compute imp sequence	implicitSeqStats
			if(implicitSeqStats!=null) {
			 ret = computeSequence(implicitSeqStats);
			 
			index = batchToRun.getSequences().indexOf(ret.getResultType());				
		    batchToRun.getSequences().set(index, ret.getResultType());
		    return new ComputeResult<Sequence,Integer>(sequence,ret.getResult());
			}
			
			return new ComputeResult<Sequence,Integer>(sequence,Integer.valueOf(0));
			
		}
		
		
	} catch (InterruptedException | ExecutionException e) {
		log.error("BatchRun:computeSequence exception for batch <<"+sequence.getBatchName()+">> sequence numero <<"+sequence.getSequenceNumerator()+">>",e);
		return new ComputeResult<Sequence,Integer>(sequence,new Integer(1)) ;
	}		
		
	}
	


	

}
