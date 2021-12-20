package fr.donnees.orchestrator.model;

import java.io.IOException;
import java.io.Serializable;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Generic type for parameters fields
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 * @param <K>
 * @param <V>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter 
@Setter
@EqualsAndHashCode
@ToString
public class Field<K,V> implements DataSerializable,Serializable  {



	private K key;
	
	private V value;


	
	

	


	@Override
	public void writeData( ObjectDataOutput out )  throws IOException {
		out.writeObject(key);
		out.writeObject(value);
	
		
	}

	@Override
	public void readData( ObjectDataInput in )  throws IOException {
		this.key = in.readObject();
		this.value = in.readObject();
		
	
	}
	
	
}
