package bj.gouv.sgg.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration Spring Batch avancée
 */
@Configuration
public class BatchConfiguration {
    
    /**
     * JobLauncher asynchrone pour permettre le retour immédiat de l'API
     */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }
    
    /**
     * RestTemplate pour les appels HTTP
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
