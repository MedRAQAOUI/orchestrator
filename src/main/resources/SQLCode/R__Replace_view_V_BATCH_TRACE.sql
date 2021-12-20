REPLACE VIEW PDY_PDML_IHM_${sid}.V_BATCH_TRACE
AS 
LOCK TABLE PDY_PDML_IHM_${sid}.T_BATCH_TRACE FOR ACCESS 	
LOCK TABLE PDY_PDML_IHM_${sid}.T_SEQUENCE_TRACE FOR ACCESS 	
LOCK TABLE PDY_PDML_IHM_${sid}.T_STEP_TRACE FOR ACCESS 	
SELECT 
 BATCH.BATCH_NAME
 ,BATCH.DH_DEBUT_EXEC_BATCH
 ,BATCH.DH_FIN_EXEC_BATCH
 ,BATCH.BATCH_STATE
 ,SEQU.SEQUENCE_NUMERATOR
 ,SEQU.IMPLICIT_SEQUENCE_NUMERATOR
 ,SEQU.DH_DEBUT_EXEC_SEQUENCE
 ,SEQU.DH_FIN_EXEC_SEQUENCE
 ,SEQU.SEQUENCE_STATE
 ,STEP.STEP_NAME
 ,STEP.DH_DEBUT_EXEC_STEP
 ,STEP.DH_FIN_EXEC_STEP
 ,STEP.STEP_STATE
 ,BATCH.ID_EXECUTION
 ,DENSE_RANK() OVER (PARTITION BY BATCH.BATCH_NAME ORDER BY  BATCH.ID_EXECUTION DESC) AS EXECUTION_TAG

FROM PDY_PDML_IHM_${sid}.T_BATCH_TRACE BATCH
INNER JOIN PDY_PDML_IHM_${sid}.T_SEQUENCE_TRACE SEQU
 ON BATCH.BATCH_NAME = SEQU.BATCH_NAME
   AND BATCH.ID_EXECUTION = SEQU.ID_EXECUTION
INNER JOIN PDY_PDML_IHM_${sid}.T_STEP_TRACE STEP 
  ON SEQU.BATCH_NAME = STEP.BATCH_NAME
   AND SEQU.SEQUENCE_NUMERATOR = STEP.SEQUENCE_NUMERATOR
   AND SEQU.IMPLICIT_SEQUENCE_NUMERATOR = STEP.IMPLICIT_SEQUENCE_NUMERATOR
   AND SEQU.ID_EXECUTION = STEP.ID_EXECUTION;