package fr.donnees.orchestrator.model;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.util.SerializationUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

import fr.donnees.orchestrator.utils.OrchestratorUtils;
import fr.donnees.orchestrator.utils.ParametersDeserializer;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * POJO structure for parameters
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter 
@Setter
@EqualsAndHashCode
@ToString
@JsonDeserialize(using = ParametersDeserializer.class)
@JsonIgnoreProperties({"batchName","namedParameters"})
public class ParameterRow implements  DataSerializable,Serializable  {
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4846063094747693190L;
	private String batchName;
	private  SqlParameterSource namedParameters;
	
	private List<Field> fields;
	
	
	
	public ParameterRow(String batchName) {
		this.batchName = batchName;
	}

	@Override
	public void writeData( ObjectDataOutput out )  throws IOException {
		
		out.writeUTF(batchName);
		List<String> parameterNames = Arrays.asList(namedParameters.getParameterNames());
		out.writeInt(parameterNames.size());

		parameterNames.stream()
              .forEach(  (parameterName) ->{
            	  try {
            	  out.writeUTF(parameterName);
            	  out.writeInt(namedParameters.getSqlType(parameterName));
            	  out.writeObject(((SqlParameterValue)namedParameters.getValue(parameterName)).getValue());
            	  
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
              }
        		
        		);
		out.writeInt(fields.size());
		fields.stream()
        .forEach(  (field) ->{
      	  try {
      	  out.writeUTF((String)field.getKey());
      	  out.writeObject(field.getValue());
      	  
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        });

		
	}

	@Override
	public void readData( ObjectDataInput in )  throws IOException {

	
		this.batchName = in.readUTF();
		MapSqlParameterSource namedParameters = new MapSqlParameterSource();
		int size = in.readInt();
		String parameterName ;
		int sqlType;
		Object parameterValue;
		 for(int i=0; i<size; i++) {
			  parameterName = in.readUTF();
			  sqlType=in.readInt();
			  parameterValue=in.readObject();
			 namedParameters.addValue(parameterName,(Object)OrchestratorUtils.constructParameter(parameterName,sqlType,parameterValue));
		    }
		this.namedParameters=namedParameters;
		
		List<Field> fields = new ArrayList<Field>();
		size = in.readInt();
		String key;
		Object value;
		for(int i=0; i<size; i++) {
			   key = in.readUTF();
			   value=in.readObject();
			  fields.add(new Field<String,Object>(key,value));
		    }
		this.fields = fields;

		
	}

}
