package fr.donnees.orchestrator.repository;

import  org.springframework.data.hazelcast.repository.HazelcastRepository;
import org.springframework.stereotype.Repository;

import fr.donnees.orchestrator.model.Batch;

/**
 * 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Repository
public interface  BatchCacheRepository extends HazelcastRepository<Batch, String>  {
	
	

	 
	
	

	
}
