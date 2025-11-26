package bj.gouv.sgg.scheduler;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Moniteur pour détecter et marquer comme FAILED les jobs Spring Batch bloqués.
 * 
 * Un job est considéré comme bloqué si:
 * - Son statut est STARTED
 * - Il n'a pas de end_time
 * - Il tourne depuis plus de jobTimeoutHours heures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobTimeoutMonitor {
    
    private final JdbcTemplate jdbcTemplate;
    private final LawProperties properties;
    private final TelegramNotificationService telegramNotificationService;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Vérifie toutes les 30 minutes s'il y a des jobs bloqués
     * Cron: à la minute 15 et 45 de chaque heure
     */
    @Scheduled(cron = "0 15,45 * * * *")
    public void checkAndCleanStuckJobs() {
        log.debug("🔍 Checking for stuck batch jobs (timeout: {} hours)...", properties.getBatch().getJobTimeoutHours());
        
        try {
            // Requête pour trouver les jobs bloqués (STARTED depuis plus de X heures)
            String findStuckJobsQuery = 
                "SELECT e.JOB_EXECUTION_ID, i.JOB_NAME, e.START_TIME, " +
                "TIMESTAMPDIFF(HOUR, e.START_TIME, NOW()) as HOURS_RUNNING " +
                "FROM BATCH_JOB_EXECUTION e " +
                "JOIN BATCH_JOB_INSTANCE i ON e.JOB_INSTANCE_ID = i.JOB_INSTANCE_ID " +
                "WHERE e.STATUS = 'STARTED' " +
                "AND e.END_TIME IS NULL " +
                "AND TIMESTAMPDIFF(HOUR, e.START_TIME, NOW()) > ?";
            
            List<Map<String, Object>> stuckJobs = jdbcTemplate.queryForList(
                findStuckJobsQuery, 
                properties.getBatch().getJobTimeoutHours()
            );
            
            if (stuckJobs.isEmpty()) {
                log.debug("✅ No stuck jobs found");
                return;
            }
            
            log.warn("⚠️ Found {} stuck job(s)", stuckJobs.size());
            
            for (Map<String, Object> job : stuckJobs) {
                Long jobExecutionId = ((Number) job.get("JOB_EXECUTION_ID")).longValue();
                String jobName = (String) job.get("JOB_NAME");
                Object startTime = job.get("START_TIME");
                Long hoursRunning = ((Number) job.get("HOURS_RUNNING")).longValue();
                
                log.warn("⏰ Stuck job detected: id={} name={} startTime={} hoursRunning={}", 
                    jobExecutionId, jobName, startTime, hoursRunning);
                
                // Marquer le job comme FAILED
                markJobAsFailed(jobExecutionId);
                
                // Marquer aussi les steps en STARTED comme FAILED
                markStuckStepsAsFailed(jobExecutionId);
                
                log.info("✅ Job {} marked as FAILED (was stuck for {} hours)", jobExecutionId, hoursRunning);
                
                // Envoyer notification Telegram
                telegramNotificationService.notifyStuckJob(jobExecutionId, jobName, hoursRunning);
            }
            
        } catch (Exception e) {
            log.error("❌ Error while checking for stuck jobs", e);
        }
    }
    
    /**
     * Marque un job execution comme FAILED
     */
    private void markJobAsFailed(Long jobExecutionId) {
        String updateJobQuery = 
            "UPDATE BATCH_JOB_EXECUTION " +
            "SET STATUS = ?, EXIT_CODE = ?, END_TIME = NOW(), " +
            "EXIT_MESSAGE = ? " +
            "WHERE JOB_EXECUTION_ID = ?";
        
        jdbcTemplate.update(
            updateJobQuery,
            BatchStatus.FAILED.name(),
            BatchStatus.FAILED.name(),
            "Job marked as FAILED by JobTimeoutMonitor - exceeded timeout of " + 
                properties.getBatch().getJobTimeoutHours() + " hours",
            jobExecutionId
        );
    }
    
    /**
     * Marque tous les steps en STARTED d'un job comme FAILED
     */
    private void markStuckStepsAsFailed(Long jobExecutionId) {
        String updateStepsQuery = 
            "UPDATE BATCH_STEP_EXECUTION " +
            "SET STATUS = ?, EXIT_CODE = ?, END_TIME = NOW(), " +
            "EXIT_MESSAGE = ? " +
            "WHERE JOB_EXECUTION_ID = ? " +
            "AND STATUS = 'STARTED' " +
            "AND END_TIME IS NULL";
        
        int updatedSteps = jdbcTemplate.update(
            updateStepsQuery,
            BatchStatus.FAILED.name(),
            BatchStatus.FAILED.name(),
            "Step marked as FAILED by JobTimeoutMonitor - parent job exceeded timeout",
            jobExecutionId
        );
        
        if (updatedSteps > 0) {
            log.info("✅ Marked {} stuck step(s) as FAILED for job {}", updatedSteps, jobExecutionId);
        }
    }
}
