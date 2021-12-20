package fr.donnees.orchestrator.exception;

import java.lang.Thread.UncaughtExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrchestratorUncaughtExceptionHandler implements UncaughtExceptionHandler {

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		log.error("OrchestratorUncaughtExceptionHandler:threadId="+t.getId()+"threadName=:"+t.getName()+":exception==>",e);

		
		
	}

}
