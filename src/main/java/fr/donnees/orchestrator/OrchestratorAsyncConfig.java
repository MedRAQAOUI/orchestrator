package fr.donnees.orchestrator;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import fr.donnees.orchestrator.exception.OrchestratorAsyncUncaughtExceptionHandler;

@Configuration
//@ComponentScan("fr.donnees.orchestrator.*")
@EnableAsync(proxyTargetClass=true) //detects @Async annotation
public class OrchestratorAsyncConfig implements AsyncConfigurer {
	
	
	
	    @Override
	    public TaskExecutor getAsyncExecutor() {
	        return getThreadPoolTaskExecutor();
	    }
	    
	    @Override
	    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
	    	       return new OrchestratorAsyncUncaughtExceptionHandler();
	    }
	 
	   @Bean(name = "threadPoolTaskExecutor" )
	    public TaskExecutor getThreadPoolTaskExecutor() {
		   int maxPoolsize = Runtime.getRuntime().availableProcessors()*4/5;
		   int corePoolsize = Runtime.getRuntime().availableProcessors()/2;
	        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
	        executor.setCorePoolSize(corePoolsize);
	        executor.setMaxPoolSize(maxPoolsize);
	        executor.setWaitForTasksToCompleteOnShutdown(true);
	        executor.setThreadNamePrefix("Orchestrator-web");
	        executor.initialize();
	        return executor;
	    }



	
	 

}
