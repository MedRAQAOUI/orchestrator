package fr.donnees.orchestrator.resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import fr.donnees.orchestrator.model.Batch;
import fr.donnees.orchestrator.model.ParameterRow;
import fr.donnees.orchestrator.service.OrchestratorService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@RestController
@RequestMapping("/batch")
@Slf4j
public class OrchestratorController {
	
	@Autowired
	private OrchestratorService orchestratorService;
	
	@Autowired
	@Qualifier("sinkBatch")
	Sinks.Many<Batch> sinkBatch;
	
	@PostMapping( path = "/orchestrate" , consumes = "application/json", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
	  public Flux<Batch> orchestrate(@RequestBody ParameterRow parameterRow) throws ResponseStatusException {
		  log.info("OrchestratorController:orchestrate ParameterRow BatchName ="+parameterRow.getBatchName());
		  Batch batchToOrchestrate = new Batch(parameterRow.getBatchName(),parameterRow);
		 
		  return this.orchestratorService.orchestrate(batchToOrchestrate);
		  
	  }
	
	@GetMapping( path = "/monitorBatch" , produces = MediaType.TEXT_EVENT_STREAM_VALUE )
	  public Flux<Batch> monitorBatch(@RequestParam String batchName) throws ResponseStatusException {
		  log.info("OrchestratorController:monitorBatch  BatchName ="+batchName);		 
		  return  this.orchestratorService.monitor(batchName) ;
		  
	  }
	
	@GetMapping( path = "/monitorAllBatch" , produces = MediaType.TEXT_EVENT_STREAM_VALUE )
	  public Flux<Batch> monitorAllBatch() throws ResponseStatusException {
		  log.info("OrchestratorController:monitorAllBatch=");		 
		  return sinkBatch.asFlux() ;
		  
	  }
	
	@GetMapping( path = "/logs" )
	  public String testLogging() {
		  log.info("Test de log dans idatha N16");		 
		  return "OK" ;
		  
	  }


}
