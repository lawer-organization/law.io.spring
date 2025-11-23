package bj.gouv.sgg.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Listener pour logger les événements du job
 */
@Slf4j
@Component
public class JobCompletionListener implements JobExecutionListener {
    
    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Job {} started", jobExecution.getJobInstance().getJobName());
    }
    
    @Override
    public void afterJob(JobExecution jobExecution) {
        long duration = java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        
        log.info("Job {} finished: status={}, duration={}ms", 
            jobExecution.getJobInstance().getJobName(),
            jobExecution.getStatus(),
            duration);
    }
}
