package fr.donnees.orchestrator.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Generic type for holding batch, sequence and step response of execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @param <K>
 * @param <V>
 */
@Getter 
@Setter
public class ComputeResult<K,V> {
	
	K resultType;
	V result;
	
	public ComputeResult(K resultType,V result) {
		this.resultType=resultType;
		this.result=result;
	}

}
