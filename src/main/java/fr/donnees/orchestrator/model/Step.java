package fr.donnees.orchestrator.model;



import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;

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
 * Immutable Step type over execution, can be multiple steps executed in // by sequence
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter 
@Setter
@EqualsAndHashCode(exclude= {"stepState","debutExecutionStep","finExecutionStep"} ) 
@ToString
@JsonIgnoreProperties({"batchName","sequenceNumerator"})
public class Step implements   DataSerializable,Serializable {

	


	/**
	 * 
	 */
	private static final long serialVersionUID = -3129310089422657416L;

	@Id
	private String batchName;

	private Integer sequenceNumerator;
	
	private Integer implicitSequenceNumerator;

	private String stepName;
	
	private String stepDescription;

	private StepType stepType;
	
	private String secretName;

	private String targetTable;

	private String stepInit;

	private String stepMain;

	private String stepClosure;
	
	private LocalDateTime  debutExecutionStep;
	private LocalDateTime  finExecutionStep; 

	private State stepState;
	
	//make state modification immutable
  public Step setStepState(State stepState) {		
		return new Step(this.getBatchName(),this.getSequenceNumerator(),this.getImplicitSequenceNumerator(),this.getStepName(),this.getStepDescription(),this.getStepType(),this.getSecretName(),this.getTargetTable(),this.getStepInit(),this.getStepMain(),this.getStepClosure(),this.getDebutExecutionStep(),this.getFinExecutionStep(),stepState);
	}

	@Override
	public void writeData( ObjectDataOutput out ) throws IOException {
		out.writeUTF(batchName);
		out.writeInt(sequenceNumerator.intValue());
		out.writeInt(implicitSequenceNumerator.intValue());
		out.writeUTF(stepName);
		out.writeUTF(stepDescription);
		out.writeUTF(stepType.name());
		out.writeUTF(secretName);
		out.writeUTF(targetTable);
		out.writeUTF(stepInit);
		out.writeUTF(stepMain);
		out.writeUTF(stepClosure);
		out.writeObject(debutExecutionStep);
		out.writeObject(finExecutionStep);
		out.writeUTF(stepState.name());
		
	}

	@Override
	public void readData( ObjectDataInput in ) throws IOException {
		this.batchName = in.readUTF();
		this.sequenceNumerator = (Integer.valueOf(in.readInt()));
		this.implicitSequenceNumerator = (Integer.valueOf(in.readInt()));
		this.stepName = in.readUTF(); 
		this.stepDescription = in.readUTF();
		this.stepType = StepType.valueOf((String) in.readUTF());
		this.secretName = in.readUTF();
		this.targetTable = in.readUTF();
		this.stepInit = in.readUTF();
		this.stepMain = in.readUTF();
		this.stepClosure = in.readUTF();
		this.debutExecutionStep = in.readObject();
		this.finExecutionStep = in.readObject();
		this.stepState = State.valueOf((String) in.readUTF());
	}
	
	
	

}
