package fr.donnees.orchestrator.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlParameterValue;

import com.fasterxml.jackson.databind.JsonNode;

import fr.donnees.orchestrator.model.Field;

/**
 * Deserialization utility
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
public class OrchestratorUtils {
	
	private static final DateTimeFormatter LOCAL_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
	private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
	 
	public static  Field constructParameterField(Map.Entry<String, JsonNode> entry){
		if(isValidDate(entry.getValue().asText()))
			return new Field<String,LocalDate>(entry.getKey(),LocalDate.parse(entry.getValue().asText(), LOCAL_DATE_FORMATTER));
		
		if(isValidDateTime(entry.getValue().asText()))
    	    return new Field<String,LocalDateTime>(entry.getKey(),LocalDateTime.parse(entry.getValue().asText(), LOCAL_DATE_TIME_FORMATTER));
    	  
		if(entry.getValue().isBoolean())
    	    return new Field<String,Boolean>(entry.getKey(),entry.getValue().asBoolean());
		
		if(entry.getValue().isBigDecimal())
    	    return new Field<String,BigDecimal>(entry.getKey(),entry.getValue().decimalValue());
    	  
		if(entry.getValue().isBigInteger())
    	    return new Field<String,BigInteger>(entry.getKey(),entry.getValue().bigIntegerValue());
		
		if(entry.getValue().isDouble())
    	    return new Field<String,Double>(entry.getKey(),entry.getValue().asDouble());
		
		if(entry.getValue().isFloat())
    	    return new Field<String,Float>(entry.getKey(),entry.getValue().floatValue());
		
		if(entry.getValue().isInt())
    	    return new Field<String,Integer>(entry.getKey(),entry.getValue().asInt());
		
		if(entry.getValue().isIntegralNumber())
    	    return new Field<String,Integer>(entry.getKey(),entry.getValue().asInt());
    	
		if(entry.getValue().isLong())
    	    return new Field<String,Long>(entry.getKey(),entry.getValue().asLong());
		
		if(entry.getValue().isNumber())
    	    return new Field<String,Number>(entry.getKey(),entry.getValue().numberValue());
		
		return new Field<String,String>(entry.getKey(),entry.getValue().asText()); 
	}
	 
	    public static  SqlParameterValue constructSqlParameter(Map.Entry<String, JsonNode> entry){
	    	if(isValidDate(entry.getValue().asText()))
	    		return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.DATE),java.sql.Date.valueOf( LocalDate.parse(entry.getValue().asText(), LOCAL_DATE_FORMATTER)));
	    	
	    	if(isValidDateTime(entry.getValue().asText()))
	    	    return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.TIMESTAMP),java.sql.Timestamp.valueOf( LocalDateTime.parse(entry.getValue().asText(), LOCAL_DATE_TIME_FORMATTER)));
	    	
	    	if(entry.getValue().isBoolean())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.BOOLEAN),entry.getValue().asBoolean());
	    	
	    	if(entry.getValue().isBigDecimal())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.DECIMAL),entry.getValue().decimalValue());
	    	
	    	if(entry.getValue().isBigInteger())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.BIGINT),entry.getValue().bigIntegerValue());
	    	
	    	if(entry.getValue().isDouble())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.DOUBLE),entry.getValue().asDouble());
	    	
	    	if(entry.getValue().isFloat())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.FLOAT),entry.getValue().floatValue());
	    	
	    	if(entry.getValue().isInt())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.INTEGER),entry.getValue().asInt());
	    	
	    	if(entry.getValue().isLong())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.BIGINT),entry.getValue().asLong());
	    	
	    	if(entry.getValue().isNumber())
	    	     return new SqlParameterValue(new SqlParameter(entry.getKey(),Types.NUMERIC),entry.getValue().numberValue());
	    	
	    	
	    	
	    	return  new SqlParameterValue(new SqlParameter(entry.getKey(),Types.VARCHAR),entry.getValue().asText());
	    }
	    
	    public static  boolean isValidDate(String dateStr) {
	        try {
	           // this.LOCAL_DATE_FORMATTER.parse(dateStr);
	            LocalDate.parse(dateStr, LOCAL_DATE_FORMATTER);
	        } catch (DateTimeParseException e) {
	            return false;
	        }
	        return true;
	    }
	    
	    public static boolean isValidDateTime(String dateTimeStr) {
	        try {
	           // this.LOCAL_DATE_TIME_FORMATTER.parse(dateTimeStr);
	        	LocalDateTime.parse(dateTimeStr, LOCAL_DATE_TIME_FORMATTER);
	        } catch (DateTimeParseException e) {
	            return false;
	        }
	        return true;
	    }
	    
	    public static  SqlParameterValue constructParameter(String parameterName,int sqlType,Object value){
	    	 switch(sqlType) {	    	
	    	case Types.DATE: 
	    		return new SqlParameterValue(new SqlParameter(parameterName,Types.DATE),(Date)value);
    		case Types.TIMESTAMP: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.BOOLEAN: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.DECIMAL: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.BIGINT: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.DOUBLE: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.FLOAT: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.INTEGER: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.NUMERIC: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		case Types.VARCHAR: 
    			return new SqlParameterValue(new SqlParameter(parameterName,Types.TIMESTAMP),(Timestamp)value);
    		
	    	}
	    	 return null;
	    }

}
