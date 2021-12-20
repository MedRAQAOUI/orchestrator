package fr.donnees.orchestrator.model;


import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Immutable Sequence type over execution , one sequence execution peer batch execution
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter 
@Setter
@EqualsAndHashCode(exclude= {"sequenceState", "steps","debutExecutionSequence","finExecutionSequence"} )
@ToString
@JsonIgnoreProperties({"batchName"})
public class Sequence implements  DataSerializable,Serializable  {
	

	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3893711370923791451L;

	private String batchName;
	@Id
	private Integer sequenceNumerator;
	
	private Integer implicitSequenceNumerator;
	
	private SequenceType sequenceType;

	private  List<Step> steps;
	
	private LocalDateTime  debutExecutionSequence;
	private LocalDateTime  finExecutionSequence; 

	private State sequenceState;
	

	//make state modification immutable
	public Sequence setSequenceState(State sequenceState) {
		
		return new Sequence(this.getBatchName(),this.getSequenceNumerator(),this.getImplicitSequenceNumerator(),this.getSequenceType(),this.getSteps(),this.getDebutExecutionSequence(),this.getFinExecutionSequence(),sequenceState);
	}

	@Override
	public void writeData( ObjectDataOutput out )  throws IOException {
		out.writeUTF(batchName);
		out.writeInt(sequenceNumerator.intValue());
		out.writeInt(implicitSequenceNumerator.intValue());
		out.writeUTF(sequenceType.name());
		out.writeInt(steps.size());
		steps.forEach(step -> {
			try {
				step.writeData(out);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		out.writeObject(debutExecutionSequence);
		out.writeObject(finExecutionSequence);
		out.writeUTF(sequenceState.name());
		
	}

	@Override
	public void readData( ObjectDataInput in ) throws IOException {
		this.batchName = in.readUTF();
		this.sequenceNumerator = (Integer.valueOf(in.readInt()));
		this.implicitSequenceNumerator=(Integer.valueOf(in.readInt()));
		this.sequenceType = SequenceType.valueOf((String) in.readUTF());
		int size = in.readInt();
		List<Step> steps = new ArrayList<Step>(size);
	    for(int i=0; i<size; i++) {
	    	Step step = new Step();
	    	step.readData(in);
	    	steps.add(step);
	    }
	    this.steps = steps;
	    this.debutExecutionSequence = in.readObject();
	    this.finExecutionSequence = in.readObject();
		this.sequenceState = State.valueOf(in.readUTF());
		
	}
	


}
