package fr.donnees.orchestrator.exception;

import java.lang.reflect.Method;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrchestratorAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

	@Override
	public void handleUncaughtException(Throwable ex, Method method, Object... params) {
		ObjectMapper objectMapper = new ObjectMapper();
		String json ="";
		try {
			json = objectMapper.writeValueAsString(params);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 log.error("OrchestratorAsyncUncaughtExceptionHandler:invoke async method occurs error. method: {}, params: {}",
	                method.getName(), json, ex);
	}

}
