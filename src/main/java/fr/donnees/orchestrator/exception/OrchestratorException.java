package fr.donnees.orchestrator.exception;

public class OrchestratorException extends RuntimeException {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -15853171653762679L;

	public OrchestratorException(String errorMessage, Throwable err) {
		super(errorMessage, err);
	}

}
