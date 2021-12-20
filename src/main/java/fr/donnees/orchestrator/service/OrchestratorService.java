package fr.donnees.orchestrator.service;

import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fr.donnees.orchestrator.model.Batch;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.repository.MetadataRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;


/**
 * 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Service
@Slf4j
public class OrchestratorService {
	
	//final Sinks.Many<Batch> sink = Sinks.many().multicast().onBackpressureBuffer();
	
	@Autowired
	@Qualifier("sinkBatch")
	Sinks.Many<Batch> sinkBatch;
	
	@Autowired
	private MetadataRepository metadataRepository;
	
	
	@Autowired
	private OrchextratorCacheService cacheService;
	
	@Autowired
	@Qualifier("OrchestratorForkJoinPool")
	ForkJoinPool orchestratorForkJoinPool;
	
	@Autowired
	private RunningService runningService;
	

	
	
	public Batch getBatchMetadataByName(Batch batch) {
		log.info("OrchestratorService:getBatchMetadataByName batchName " + batch.getBatchName());
		return this.metadataRepository.getBatchMetadataByName(batch);
	}
	
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public Flux<Batch> orchestrate(Batch batch) throws ResponseStatusException {
		log.info("OrchestratorService:orchestrate batchName <<" + batch.getBatchName()+">>  thread <<"+Thread.currentThread().getName()+">>");
		Batch batchInCacheOrInDb = this.cacheService.findById(batch.getBatchName());
		
		     if(batchInCacheOrInDb != null )
		    	 throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Orchestrator-Content: <<"+batch.getBatchName()+">> batch is in running state, you can't launch it");
				
		     if(batchInCacheOrInDb == null) 
			     batchInCacheOrInDb = this.metadataRepository.getBatchMetadataByName(batch);
		      
		     if(batchInCacheOrInDb == null) 
				    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orchestrator-Content: Batch with name <<"+batch.getBatchName()+">> not Found");
			 
		
			if(batchInCacheOrInDb != null) {
				batchInCacheOrInDb.setBatchState(State.IN_PROGRESS);
				batchInCacheOrInDb.setDebutExecutionBatch(LocalDateTime.now());
				runningService.runBatch(batchInCacheOrInDb);
			}
					      
			
			String batchName= batchInCacheOrInDb.getBatchName();
			Long  executionId= batchInCacheOrInDb.getExecutionId();
		return sinkBatch.asFlux().filter(b -> b.getBatchName().equalsIgnoreCase(batchName) && b.getExecutionId().equals(executionId)) ;
	}
	
	
	public Flux<Batch> monitor(String batchName ) throws ResponseStatusException {
		
		log.info("OrchestratorService:monitor batchName <<" + batchName+">>  ");
		
		Batch batchInCacheOrInDb = this.metadataRepository.getBatchMetadataByName(new Batch(batchName));
	      
	     if(batchInCacheOrInDb == null) 
			    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orchestrator-Content: Batch with name <<"+batchName+">> not Found");
		
		
		 batchInCacheOrInDb = this.cacheService.findById(batchName);
		
		if(batchInCacheOrInDb == null )
	    	 throw new ResponseStatusException(HttpStatus.NO_CONTENT, "Orchestrator-Content:  <<"+batchName+">> batch not in running state ");
		
		this.cacheService.monitorBatch(batchInCacheOrInDb);
		
		Long  executionId= batchInCacheOrInDb.getExecutionId();
	return sinkBatch.asFlux().filter(b -> b.getBatchName().equalsIgnoreCase(batchName) && b.getExecutionId().equals(executionId)) ;
		
		
	}
	

	


	 




}
