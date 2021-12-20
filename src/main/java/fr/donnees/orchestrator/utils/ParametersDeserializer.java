package fr.donnees.orchestrator.utils;

import java.io.IOException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import fr.donnees.orchestrator.model.Field;
import fr.donnees.orchestrator.model.ParameterRow;
import lombok.extern.slf4j.Slf4j;


/**
 *  Parameters deserialization tools
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Slf4j
public class ParametersDeserializer extends StdDeserializer<ParameterRow> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 8135365319765758729L;
	

	   public ParametersDeserializer() { 
	        this(null); 
	    } 

	    public ParametersDeserializer(Class<?> vc) { 
	        super(vc); 
	    }

	    @Override
	    public ParameterRow deserialize(JsonParser jp, DeserializationContext ctxt) 
	      throws IOException, JsonProcessingException {
	        JsonNode node = jp.getCodec().readTree(jp);	        	     
	        String batchName = node.get("batchName").asText();	        
	        ParameterRow paramRow = new ParameterRow(batchName);
	  	        
	        Iterator<Entry<String, JsonNode>> nodes = node.get("fields").fields();
	        MapSqlParameterSource namedParameters = new MapSqlParameterSource();
	        List<Field> fields = new ArrayList<Field>();
	        while (nodes.hasNext()) {
	        	  Map.Entry<String, JsonNode> entry = (Map.Entry<String, JsonNode>) nodes.next();
	        	  log.info("key --> " + entry.getKey() + " value-->" + entry.getValue());
	        	  namedParameters.addValue(entry.getKey(),(Object)OrchestratorUtils.constructSqlParameter(entry));
	        	  fields.add((Field)OrchestratorUtils.constructParameterField(entry));
	        	}
	        
	        paramRow.setNamedParameters(namedParameters);
	        paramRow.setFields(fields);
	      
	        return paramRow;
	    }
	    
	
	    

	    
}
