package fr.donnees.orchestrator.service;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Base64Utils;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import fr.donnees.orchestrator.exception.OrchestratorException;
import fr.donnees.orchestrator.model.Batch;
import fr.donnees.orchestrator.model.RestConnection;
import fr.donnees.orchestrator.model.Sequence;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import fr.donnees.orchestrator.repository.BatchCacheRepository;
import fr.donnees.orchestrator.repository.MetadataRepository;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;


/**
 * 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Service
@Slf4j
public class OrchextratorCacheService  implements BeanFactoryAware {
	
	
	
	@Autowired
	@Qualifier("sinkBatch")
	Sinks.Many<Batch> sinkBatch;
	

	
	@Autowired
	private BatchCacheRepository batchCacheRepository;
	
	@Autowired
	private MetadataRepository metadataRepository;
	
	
	private BeanFactory beanFactory;
	
	@Autowired
	private ApplicationContext applicationContext;
	
	
	 @Override
	 public void setBeanFactory(BeanFactory beanFactory) {
	        this.beanFactory = beanFactory;
	  }
	 
	 
	 public boolean isHttpBeanExist(String secretName) {
		 String httpSecret = secretName.split("#")[0]; 
   	     String dbSecret = secretName.split("#")[1]; 
   	     
   	    RestConnection rc;
     	NamedParameterJdbcTemplate npjt;
     	
     	 try {
  		   npjt= this.applicationContext.getBean(dbSecret,NamedParameterJdbcTemplate.class);
  		   rc=this.applicationContext.getBean(httpSecret,RestConnection.class);
  		   return npjt!=null && rc!=null;
  	     }catch(NoSuchBeanDefinitionException e) {
  		  return false;
  	     }     
   	     
	 }
	 
	 public boolean isSqlBeanExist(String secretName) {
	
     	NamedParameterJdbcTemplate npjt;
     	
     	 try {
  		   npjt= this.applicationContext.getBean(secretName,NamedParameterJdbcTemplate.class);
  		
  		   return npjt!=null;
  	     }catch(NoSuchBeanDefinitionException e) {
  		  return false;
  	     } 
	 }
	 
	 public int registerBean(String secretName, StepType stepType) {
		 log.info("OrchextratorCacheService:registerBean try to register bean of secret  <<"+secretName+">> for StepType <<"+stepType.name()+">>");
		 final ConfigBuilder configBuilder = new ConfigBuilder();
	        
	      KubernetesClient client = new DefaultKubernetesClient(configBuilder.build());
	        
	        
	      final String namespace =client.getNamespace();   
	      
	      log.info("OrchextratorCacheService:registerBean namespace <<"+namespace+">>   ");
	        
	      
	      ConfigurableBeanFactory configurableBeanFactory = (ConfigurableBeanFactory) beanFactory;
	      
	    
		  
    	
    	  
          if(stepType.name().equalsIgnoreCase(StepType.SQL.name()) || stepType.name().equalsIgnoreCase(StepType.SQLV.name()) || stepType.name().equalsIgnoreCase(StepType.SQLIMP.name()) ) {
        	   
        	  Secret secretSql =   client
   		  	        .secrets()
   		  	        .inNamespace(namespace)
   		  	        .withName(secretName).get();
  	      
  		  if(secretSql == null) {
  	  			log.error("OrchextratorCacheService:registerBean exception  secret with name <<"+secretName+">> not found ");
  	  			return 1;		
  	  		}
        	  
        	  
        	  if( !isSqlBeanExist(secretName)) {
        		  NamedParameterJdbcTemplate  npjt=setNamedParameterJdbcTemplateBean(secretSql);
        	  
        	    if(npjt == null) {
        			log.error("OrchextratorCacheService:registerBean exception cannot create connection to database with secret   <<"+secretName+">>");
        			return 2;	
        	     }
        	     try {
        	        configurableBeanFactory.registerSingleton(secretName, npjt);
        	     }catch(IllegalStateException e) {
        	    //	 log.error("OrchextratorCacheService:registerBean exception cannot create connection to database with secret   <<"+secretName+">>",e);
        	    	 return 5;
        	     }
        	  }
        	  return 0;
          }
          
          if(stepType.name().equalsIgnoreCase(StepType.HTTP.name()) || stepType.name().equalsIgnoreCase(StepType.HTTPIMP.name()) ) {
        	  
        	  
        	  if( !isHttpBeanExist(secretName)) {
        	      String httpSecret = secretName.split("#")[0]; 
        	      String dbSecret = secretName.split("#")[1]; 
        	      
        	      

            	  Secret secretHttp =   client
       		  	        .secrets()
       		  	        .inNamespace(namespace)
       		  	        .withName(httpSecret).get();
      	      
      		      if(secretHttp == null) {
      	  			 log.error("OrchextratorCacheService:registerBean exception  secret with name <<"+httpSecret+">> not found ");
      	  			 return 1;		
      	  		   }
      		      
      		    Secret secretdb =   client
       		  	        .secrets()
       		  	        .inNamespace(namespace)
       		  	        .withName(dbSecret).get();
      	      
      		      if(secretdb == null) {
      	  			 log.error("OrchextratorCacheService:registerBean exception  secret with name <<"+dbSecret+">> not found ");
      	  			 return 1;		
      	  		   }
        	  
        	      RestConnection rc=setRestConnectionBean(secretHttp);
        	       if(rc == null) {
        		      log.error("OrchextratorCacheService:runBatch exception  cannot create connection to rest api with secret   <<"+httpSecret+">>");
        		      return 3;	
        	       }
        	        try {
        	             configurableBeanFactory.registerSingleton(httpSecret, rc);
        	         }catch(IllegalStateException e) {
       	    	      // log.error("OrchextratorCacheService:registerBean exception cannot create connection to database with secret   <<"+httpSecret+">>",e);
       	    	        return 5;
       	             }
        	  
        	       NamedParameterJdbcTemplate npjt=setNamedParameterJdbcTemplateBean(secretdb);
        	  
        	       if(npjt == null) {
        			log.error("OrchextratorCacheService:registerBean exception cannot create connection to database with secret   <<"+dbSecret+">>");
        			return 2;	
        	       }
        	       try {
        	        configurableBeanFactory.registerSingleton(dbSecret, npjt);
        	       }catch(IllegalStateException e) {
          	    	// log.error("OrchextratorCacheService:registerBean exception cannot create connection to database with secret   <<"+dbSecret+">>",e);
          	    	 return 5;
          	     }
        	        return 0;
        	  
        	}  
          }
          return 4;
	 }
	 
		@Async("threadPoolTaskExecutor")
		public void monitorBatch(Batch batch) {
			log.info("RunningService:monitorBatch batchName <<" + batch.getBatchName());
			try{
			    Thread.sleep(500);
			    emitBatchState(batch);
			}catch(InterruptedException e){
				log.error("OrchextratorCacheService:monitorBatch exception :" ,e);
			    
			}
			
		}


	private void emitBatchState(Batch batch) {
		
		EmitResult result = sinkBatch.tryEmitNext(batch);

        if (result.isFailure()) {
        	log.error("OrchextratorCacheService:emitBatchState emission failed  for batch : " + batch.getBatchName());
        }
        
        
	}
	
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public Batch findById(String batchName) {
		if(batchName == null)
			  throw new OrchestratorException("OrchextratorCacheService:findById batchName must be not null  ",null);
		
		return batchCacheRepository.findById(batchName).orElse(null);
	}


	@Transactional(isolation = Isolation.SERIALIZABLE)
	public   Batch updateOrSave(Batch batch) {
		
		  if(batch == null)
			  throw new OrchestratorException("OrchextratorCacheService:updateOrSave: batch is null  ",null);
		
		      log.info("OrchextratorCacheService:updateOrSave batchName <<" + batch.getBatchName()+">>  by thread <<"+Thread.currentThread().getName()+">>");
		
		      Batch batchFromCache = batchCacheRepository.findById(batch.getBatchName()).orElse(null);
		 
		
		 
		      if(batchFromCache!=null) {
			     batchFromCache.setParameterRow(batch.getParameterRow());
			     batchFromCache.setSequences(batch.getSequences());
			     batchFromCache.setBatchState(batch.getBatchState());
			     batch = batchCacheRepository.save(batchFromCache); 			 
		      }else {
			    batch = batchCacheRepository.save(batch); 	
		      }
		       emitBatchState(batch);
		 
		      if(batch.getBatchState().equals(State.TERMINATED) ||  batch.getBatchState().equals(State.IN_ERROR))
			    batchCacheRepository.delete(batch);
		      
		      
		
		 return batch;
	 }
	 

public Sequence updateOrSave(Sequence sequence) {
		
			if(sequence == null)
				throw new OrchestratorException("OrchextratorCacheService:updateOrSave:sequence sequence is null  ",null);
			
			String impText = sequence.getImplicitSequenceNumerator().intValue()==0?"":"  implicit sequence <<"+sequence.getImplicitSequenceNumerator()+">> in";
			log.info("OrchextratorCacheService:updateOrSave "+impText+" sequence  <<" + sequence.getSequenceNumerator()+">> in batch <<"+sequence.getBatchName() +">>  by thread <<"+Thread.currentThread().getName()+">>");
			
			Batch batchFromCache = findById(sequence.getBatchName());
			
			if(batchFromCache == null)
				throw new OrchestratorException("sequence:no batch in cache for updateOrSave sequence ",null);
			
			int index = batchFromCache.getSequences().indexOf(sequence);
			
			batchFromCache.getSequences().set(index, sequence);
			 
			batchFromCache=updateOrSave(batchFromCache);
		
			 return batchFromCache.getSequences().get(batchFromCache.getSequences().indexOf(sequence));
		
	}
		 
		 
  public Step updateOrSave(Step step) {
				
				if(step == null)
					throw new OrchestratorException("OrchextratorCacheService:updateOrSave:step step is null  ",null);
		        
				String impText = step.getImplicitSequenceNumerator().intValue()==0?"":"  implicit sequence <<"+step.getImplicitSequenceNumerator()+">> in";
			    log.info("OrchextratorCacheService:updateOrSave step  <<" + step.getStepName()+">> in "+impText+" sequence <<"+step.getSequenceNumerator() +">>  in batch <<"+step.getBatchName()+">> by thread <<"+Thread.currentThread().getName()+">>");
				
				
				Batch batchFromCache = findById(step.getBatchName());
				
				if(batchFromCache == null)
					throw new OrchestratorException("step:no batch in cache for updateOrSave step ",null);
				
			   Sequence sequence=batchFromCache.getSequences().stream()
				      .filter(seq -> seq.getSequenceNumerator().equals(step.getSequenceNumerator())
				    		        && seq.getImplicitSequenceNumerator().equals(step.getImplicitSequenceNumerator()))
				      .findFirst().orElse(null);
			   
			   if(sequence == null) {
				    impText = step.getImplicitSequenceNumerator().intValue()==0?"":"  implicit sequence <<"+step.getImplicitSequenceNumerator()+">> in";
				     throw new OrchestratorException("step:no "+impText+" sequence  <<"+step.getSequenceNumerator()+">> in batch <<"+step.getBatchName()+">>",null);
			   }
					
			   
			   sequence.getSteps().set(sequence.getSteps().indexOf(step),step);
			   batchFromCache.getSequences().set(batchFromCache.getSequences().indexOf(sequence), sequence);
				 
				batchFromCache=updateOrSave(batchFromCache);
				
				sequence=batchFromCache.getSequences().get(batchFromCache.getSequences().indexOf(sequence));
		
				 
				 return sequence.getSteps().get(sequence.getSteps().indexOf(step));
			 }
	 
public int traceBatch(Batch batch) {
	  if(batch == null)
		  throw new OrchestratorException("OrchextratorCacheService:traceBatch: batch must be not null  ",null);
	
	      
	return  metadataRepository.traceBatch(batch);
}

public int traceSequence(Sequence sequence,Long executionId) {
	if(sequence == null )
		  throw new OrchestratorException("OrchextratorCacheService:traceSequence sequence must be not  null  ",null);
	
	if(executionId == null)
		  throw new OrchestratorException("OrchextratorCacheService:traceSequence  executionId  must be not null  ",null);
	
	 
	 return  metadataRepository.traceSequence(sequence,executionId);
}
 

public int traceStep(Step step,Long executionId) {
	if(step == null )
		  throw new OrchestratorException("OrchextratorCacheService:traceStep step  must be not null  ",null);
	
	if(executionId == null)
		  throw new OrchestratorException("OrchextratorCacheService:traceStep  executionId must be not null  ",null);
	
	 
	 return  metadataRepository.traceStep(step,executionId);
}


private RestConnection setRestConnectionBean(Secret secretForHttp) {
	 
	 log.info("OrchextratorCacheService:setRestConnectionBean get connection members from  <<"+secretForHttp.getMetadata().getName()+">> ");
	
			return  new RestConnection(getFieldFromSecret(secretForHttp,"urlbase"),getFieldFromSecret(secretForHttp,"username"), getFieldFromSecret(secretForHttp,"password"));
		 
}


private NamedParameterJdbcTemplate setNamedParameterJdbcTemplateBean(Secret secretForSql) {
	 log.info("OrchextratorCacheService:setNamedParameterJdbcTemplateBean get connection members from  <<"+secretForSql.getMetadata().getName()+">> ");
	 DataSource ds= DataSourceBuilder.create().driverClassName("com.teradata.jdbc.TeraDriver")
			 .url("jdbc:teradata://"+getFieldFromSecret(secretForSql,"appliance")+"/TMODE=TERA") 
			 .username(getFieldFromSecret(secretForSql,"username"))
			 .password(getFieldFromSecret(secretForSql,"password"))
			 .build();
	
	 return new NamedParameterJdbcTemplate(ds);

}

private  String getFieldFromSecret(Secret secret,String field) {	
		
	try {
		return new String(Base64Utils.decodeFromString(secret.getData().get(field)),"UTF-8").trim();
	} catch (UnsupportedEncodingException e) {
		log.error("OrchextratorCacheService:getFieldFromSecret exception  UnsupportedEncodingException   ",e);
        return null;
	}
	

    	
}


}
