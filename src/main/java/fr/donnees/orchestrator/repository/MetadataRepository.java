package fr.donnees.orchestrator.repository;

import static java.util.function.Function.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import fr.donnees.orchestrator.model.Batch;
import fr.donnees.orchestrator.model.Sequence;
import fr.donnees.orchestrator.model.SequenceType;
import fr.donnees.orchestrator.model.State;
import fr.donnees.orchestrator.model.Step;
import fr.donnees.orchestrator.model.StepType;
import fr.donnees.orchestrator.utils.Expression;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

/**
 * 
 * @author MOHAMED RAQAOUI (MR6E5DFN)
 *
 */
@Repository
@Slf4j
public class MetadataRepository {

	@Autowired
	@Qualifier("jdbcTemplateApp")
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	@Qualifier("namedParameterJdbcTemplate")
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@Value("${spring.flyway.placeholders.sidpdml}")
	private String sidpdml;
	
	
	
	
	private static String BATCH_METADATA_QUERY = "exec PDY_PDML_IHM_${sidpdml}.MS_GET_BATCH_METADATA(:batchName); ";
	private static String BATCH_TRACE_QUERY = "exec PDY_PDML_IHM_${sidpdml}.MM_BATCH_TRACE(:batchName,:executionId,:batchState,:debutExecutionBatch,:finExecutionBatch); ";
	private static String SEQUENCE_TRACE_QUERY = "exec PDY_PDML_IHM_${sidpdml}.MM_SEQUENCE_TRACE(:batchName,:executionId,:sequenceNumerator,:implicitSequenceNumerator,:sequenceState,:debutExecutionSequence,:finExecutionSequence); ";
	private static String STEP_TRACE_QUERY = "exec PDY_PDML_IHM_${sidpdml}.MM_STEP_TRACE(:batchName,:executionId,:sequenceNumerator,:implicitSequenceNumerator,:stepName,:stepState,:debutExecutionStep,:finExecutionStep); ";
	
	public Batch getBatchMetadataByName(Batch batch) {
		log.info("MetadataRepository:getBatchMetadataByName batchName = "+batch.getBatchName());

		SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("batchName",new SqlParameterValue(new SqlParameter("batchName",Types.VARCHAR),batch.getBatchName()));
		
		
		return namedParameterJdbcTemplate.query(new Expression(BATCH_METADATA_QUERY)
				.replacement().from("${sidpdml}").to(sidpdml, identity()).addReplacement().getTargetExpression(), namedParameters, getBatchExtractor(batch));
		
		
	}
	
	
	@SuppressWarnings("serial")
	public ResultSetExtractor<Batch> getBatchExtractor(Batch batch) {
		log.info("MetadataRepository:getBatchExtractor Batch pojo construct  batchName ="+batch.getBatchName());
		
		
		return new ResultSetExtractor<Batch>(){
			
		public Batch extractData(ResultSet rs) throws SQLException, DataAccessException {
	    boolean hasData = false;
	    batch.setBatchState(State.IN_CONSTRUCT);
	    List<Step> steps ;
	    List<Sequence> sequences;
	    
		Integer sequenceNumber ;
		Integer stepNumber ;
		Integer currentSequence = 1 ;
		boolean isImplicitSequence = false;
					
		while(rs.next()) {
			  hasData = true;
			 sequenceNumber = rs.getInt("SEQUENCE_NUMBER");
			 stepNumber = rs.getInt("STEP_NUMBER");
			 
			 
			if( rs.getRow() == 1) {
			    final Integer stepNumberTmp = stepNumber;
			    isImplicitSequence = StepType.SQLV.equals(StepType.valueOf(rs.getString("STEP_TYPE")));
			    final boolean isImplicitSequenceTmp = isImplicitSequence;
			    sequences = new ArrayList<Sequence>(sequenceNumber){{add(rs.getInt("SEQUENCE_NUMERATOR")-1,new Sequence(rs.getString("BATCH_NAME"),rs.getInt("SEQUENCE_NUMERATOR"),0, isImplicitSequenceTmp ?SequenceType.IMPLICIT:SequenceType.ORDINARY,new ArrayList<Step>(stepNumberTmp){{add(new Step(rs.getString("BATCH_NAME"),rs.getInt("SEQUENCE_NUMERATOR"),0,rs.getString("STEP_NAME"),rs.getString("STEP_DESCRIPTION"),StepType.valueOf(rs.getString("STEP_TYPE")),rs.getString("SECRET_NAME"),rs.getString("TARGET_TABLE"),rs.getString("STEP_INIT"),rs.getString("STEP_MAIN"),rs.getString("STEP_CLOSURE"),null,null,State.IN_CONSTRUCT));}},null,null,State.IN_CONSTRUCT));}};
			    batch.setSequences(sequences);
			}else 
			if(currentSequence == rs.getInt("SEQUENCE_NUMERATOR")  ) {
				isImplicitSequence = StepType.SQLV.equals(StepType.valueOf(rs.getString("STEP_TYPE")));
				Step step = new Step(rs.getString("BATCH_NAME"),currentSequence,0,rs.getString("STEP_NAME"),rs.getString("STEP_DESCRIPTION"),StepType.valueOf(rs.getString("STEP_TYPE")),rs.getString("SECRET_NAME"),rs.getString("TARGET_TABLE"),rs.getString("STEP_INIT"),rs.getString("STEP_MAIN"),rs.getString("STEP_CLOSURE"),null,null,State.IN_CONSTRUCT);
				final Integer currentSequenceTmp = currentSequence;
				final boolean isImplicitSequenceTmp = isImplicitSequence;
				batch.getSequences().stream().filter(seq -> seq.getSequenceNumerator() == currentSequenceTmp)
				  .forEach(seq -> { 
					  if(isImplicitSequenceTmp)
						   seq.setSequenceType(SequenceType.IMPLICIT);
					  seq.getSteps().add(step);});
			}else {
				currentSequence = rs.getInt("SEQUENCE_NUMERATOR");	
				isImplicitSequence = StepType.SQLV.equals(StepType.valueOf(rs.getString("STEP_TYPE")));
				steps = new ArrayList<Step>(stepNumber){{add(new Step(rs.getString("BATCH_NAME"),rs.getInt("SEQUENCE_NUMERATOR"),0,rs.getString("STEP_NAME"),rs.getString("STEP_DESCRIPTION"),StepType.valueOf(rs.getString("STEP_TYPE")),rs.getString("SECRET_NAME"),rs.getString("TARGET_TABLE"),rs.getString("STEP_INIT"),rs.getString("STEP_MAIN"),rs.getString("STEP_CLOSURE"),null,null,State.IN_CONSTRUCT));}};
				Sequence sequence = new Sequence(rs.getString("BATCH_NAME"),currentSequence,0,isImplicitSequence ?SequenceType.IMPLICIT:SequenceType.ORDINARY,steps,null,null,State.IN_CONSTRUCT);
				batch.getSequences().add(currentSequence-1,sequence);
			}
			
		}
		
		if(hasData)		
		  return batch;		
		else 
		  return null;
		
			  }    	  
	      };
		
		}
		
	public int traceBatch(Batch batch) {
		log.info("MetadataRepository:traceBatch batch <<"+batch.getBatchName()+">>");

		MapSqlParameterSource namedParameters = new MapSqlParameterSource().addValue("batchName",new SqlParameterValue(new SqlParameter("batchName",Types.VARCHAR),batch.getBatchName()));
		namedParameters.addValue("executionId",new SqlParameterValue(new SqlParameter("executionId",Types.BIGINT),batch.getExecutionId()));
		namedParameters.addValue("batchState",new SqlParameterValue(new SqlParameter("batchState",Types.VARCHAR),batch.getBatchState()));
		namedParameters.addValue("debutExecutionBatch",new SqlParameterValue(new SqlParameter("debutExecutionBatch",Types.TIMESTAMP),Timestamp.valueOf(batch.getDebutExecutionBatch())  ));
		namedParameters.addValue("finExecutionBatch",new SqlParameterValue(new SqlParameter("finExecutionBatch",Types.TIMESTAMP),batch.getFinExecutionBatch()!=null?Timestamp.valueOf(batch.getFinExecutionBatch()):null ));
		
		return  namedParameterJdbcTemplate.update(new Expression(BATCH_TRACE_QUERY)
					.replacement().from("${sidpdml}").to(sidpdml, identity()).addReplacement().getTargetExpression(),namedParameters);
				
	}
	
	public int traceSequence(Sequence sequence,Long executionId) {
		String impText = sequence.getImplicitSequenceNumerator().intValue()==0?"":"  implicit sequence <<"+sequence.getImplicitSequenceNumerator()+">> in";
		log.info("MetadataRepository:traceSequence  "+impText+"  sequence <<"+sequence.getSequenceNumerator()+">> in batch <<"+sequence.getBatchName()+">>");
		
		MapSqlParameterSource namedParameters = new MapSqlParameterSource().addValue("batchName",new SqlParameterValue(new SqlParameter("batchName",Types.VARCHAR),sequence.getBatchName()));
		namedParameters.addValue("executionId",new SqlParameterValue(new SqlParameter("executionId",Types.BIGINT),executionId));
		namedParameters.addValue("sequenceNumerator",new SqlParameterValue(new SqlParameter("sequenceNumerator",Types.TINYINT),sequence.getSequenceNumerator()));
		namedParameters.addValue("implicitSequenceNumerator",new SqlParameterValue(new SqlParameter("implicitSequenceNumerator",Types.TINYINT),sequence.getImplicitSequenceNumerator()));
		namedParameters.addValue("sequenceState",new SqlParameterValue(new SqlParameter("sequenceState",Types.VARCHAR),sequence.getSequenceState()));
		namedParameters.addValue("debutExecutionSequence",new SqlParameterValue(new SqlParameter("debutExecutionSequence",Types.TIMESTAMP),Timestamp.valueOf(sequence.getDebutExecutionSequence())  ));
		namedParameters.addValue("finExecutionSequence",new SqlParameterValue(new SqlParameter("finExecutionSequence",Types.TIMESTAMP),sequence.getFinExecutionSequence()!=null?Timestamp.valueOf(sequence.getFinExecutionSequence()):null ));
		
		return  namedParameterJdbcTemplate.update(new Expression(SEQUENCE_TRACE_QUERY)
					.replacement().from("${sidpdml}").to(sidpdml, identity()).addReplacement().getTargetExpression(),namedParameters);
		
	}
	
	
	public int traceStep(Step step,Long executionId) {
		String impText = step.getImplicitSequenceNumerator().intValue()==0?"":"  implicit sequence <<"+step.getImplicitSequenceNumerator()+">> in";
		log.info("MetadataRepository:traceStep step <<"+step.getStepName()+">> in "+impText+"   sequence <<"+step.getSequenceNumerator()+">> batch <<"+step.getBatchName()+">>");
		
		MapSqlParameterSource namedParameters = new MapSqlParameterSource().addValue("batchName",new SqlParameterValue(new SqlParameter("batchName",Types.VARCHAR),step.getBatchName()));
		namedParameters.addValue("executionId",new SqlParameterValue(new SqlParameter("executionId",Types.BIGINT),executionId));
		namedParameters.addValue("sequenceNumerator",new SqlParameterValue(new SqlParameter("sequenceNumerator",Types.TINYINT),step.getSequenceNumerator()));
		namedParameters.addValue("implicitSequenceNumerator",new SqlParameterValue(new SqlParameter("implicitSequenceNumerator",Types.TINYINT),step.getImplicitSequenceNumerator()));
		namedParameters.addValue("stepName",new SqlParameterValue(new SqlParameter("stepName",Types.VARCHAR),step.getStepName()));
		namedParameters.addValue("stepState",new SqlParameterValue(new SqlParameter("stepState",Types.VARCHAR),step.getStepState()));
		namedParameters.addValue("debutExecutionStep",new SqlParameterValue(new SqlParameter("debutExecutionStep",Types.TIMESTAMP),Timestamp.valueOf(step.getDebutExecutionStep())  ));
		namedParameters.addValue("finExecutionStep",new SqlParameterValue(new SqlParameter("finExecutionStep",Types.TIMESTAMP),step.getFinExecutionStep()!=null?Timestamp.valueOf(step.getFinExecutionStep()):null ));
		
		return  namedParameterJdbcTemplate.update(new Expression(STEP_TRACE_QUERY)
					.replacement().from("${sidpdml}").to(sidpdml, identity()).addReplacement().getTargetExpression(),namedParameters);
		
	}
	
	
}
