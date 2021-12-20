package fr.donnees.orchestrator.model;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

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
 *  batch type representative
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter 
@Setter
@EqualsAndHashCode(exclude= {"batchState", "sequences"} )
@ToString
//@JsonIgnoreProperties({"parameterRow"})
public class Batch implements DataSerializable,Serializable  {


    /**
	 * 
	 */
	private static final long serialVersionUID = 7724690669901935007L;

	@Id
	private String batchName;
	
	
	private ParameterRow parameterRow;
	
	private  List<Sequence> sequences;
	
	private LocalDateTime  debutExecutionBatch;
	private LocalDateTime  finExecutionBatch; 

	private  State batchState;
	
	public Batch(String batchName,ParameterRow parameterRow) {
		this.batchName = batchName;
		this.parameterRow = parameterRow;
	}
	
	public Batch(String batchName) {
		this.batchName = batchName;
	}

	@Override
	public void writeData( ObjectDataOutput out )  throws IOException {
		out.writeUTF(batchName);
		parameterRow.writeData(out);
		out.writeInt(sequences.size());
		sequences.forEach(seq -> {
			try {
				seq.writeData(out);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		out.writeObject(debutExecutionBatch);
		out.writeObject(finExecutionBatch);
		out.writeUTF(batchState.name());
	
		
	}

	@Override
	public void readData( ObjectDataInput in )  throws IOException {
		this.batchName = in.readUTF();
		ParameterRow parameter = new ParameterRow();
		this.parameterRow = parameter;
		this.parameterRow.readData(in);
		int size = in.readInt();
		List<Sequence> seqs = new ArrayList<Sequence>(size);
	    for(int i=0; i<size; i++) {
	    	Sequence seq = new Sequence();
	    	seq.readData(in);
	    	seqs.add(seq);
	    }
	    this.sequences = seqs;
	    this.debutExecutionBatch = in.readObject();
	    this.finExecutionBatch = in.readObject();
		this.batchState = State.valueOf(in.readUTF());
	}
	

	
	public Long getExecutionId() {
	        return this.debutExecutionBatch.atZone(ZoneId.systemDefault()).toEpochSecond();
		
	}
	
	
}
