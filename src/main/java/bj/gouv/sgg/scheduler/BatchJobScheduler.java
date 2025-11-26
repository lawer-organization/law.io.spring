package bj.gouv.sgg.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scheduler pour les jobs Spring Batch
 * 
 * Planning:
 * - fetch-current: 3 fois/jour (6h, 12h, 18h)
 * - fetch-previous: Toutes les heures à :05
 * - download: Toutes les heures à :15
 * - ocr: Toutes les heures à :25
 * - extract: Toutes les heures à :35
 * - consolidate: Toutes les heures à :45
 * 
 * Les jobs sont espacés de 10 minutes pour éviter les chevauchements
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchJobScheduler {
    
    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    
    @Qualifier("fetchCurrentJob")
    private final Job fetchCurrentJob;
    
    @Qualifier("fetchPreviousJob")
    private final Job fetchPreviousJob;
    
    @Qualifier("downloadJob")
    private final Job downloadJob;
    
    @Qualifier("ocrJob")
    private final Job ocrJob;
    
    @Qualifier("articleExtractionJob")
    private final Job articleExtractionJob;
    
    @Qualifier("consolidateJob")
    private final Job consolidateJob;
    
    /**
     * Fetch current - 3 fois par jour (6h, 12h, 18h)
     */
    @Scheduled(cron = "0 0 6,12,18 * * *")
    public void scheduledFetchCurrent() {
        log.info("⏰ Scheduled execution: Fetch Current Year");
        runJobIfNotRunning(fetchCurrentJob, "Fetch Current");
    }
    
    /**
     * Fetch previous - Toutes les heures à :30
     */
    @Scheduled(cron = "0 30 * * * *")
    public void scheduledFetchPrevious() {
        log.info("⏰ Scheduled execution: Fetch Previous Years");
        runJobIfNotRunning(fetchPreviousJob, "Fetch Previous");
    }
    
    /**
     * Download - Toutes les 2 heures à :00 (heures paires uniquement)
     */
    @Scheduled(cron = "0 0 */2 * * *")
    public void scheduledDownload() {
        log.info("⏰ Scheduled execution: Download PDFs");
        runJobIfNotRunning(downloadJob, "Download");
    }
    
    /**
     * OCR - Toutes les 2 heures à :30 (heures paires uniquement)
     */
    @Scheduled(cron = "0 30 */2 * * *")
    public void scheduledOcr() {
        log.info("⏰ Scheduled execution: OCR Processing");
        runJobIfNotRunning(ocrJob, "OCR");
    }
    
    /**
     * Extract Articles - Toutes les 2 heures à :00 (heures impaires uniquement)
     */
    @Scheduled(cron = "0 0 1-23/2 * * *")
    public void scheduledExtract() {
        log.info("⏰ Scheduled execution: Extract Articles");
        runJobIfNotRunning(articleExtractionJob, "Extract Articles");
    }
    
    /**
     * Consolidate - Toutes les 2 heures à :30 (heures impaires uniquement)
     */
    @Scheduled(cron = "0 30 1-23/2 * * *")
    public void scheduledConsolidate() {
        log.info("⏰ Scheduled execution: Consolidate");
        runJobIfNotRunning(consolidateJob, "Consolidate");
    }
    
    /**
     * Exécute un job seulement s'il n'est pas déjà en cours
     */
    private void runJobIfNotRunning(Job job, String jobName) {
        try {
            // Vérifier si le job est déjà en cours d'exécution
            if (isJobRunning(job.getName())) {
                log.warn("⚠️  {} job is already running, skipping scheduled execution", jobName);
                return;
            }
            
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("trigger", "scheduled")
                .toJobParameters();
            
            JobExecution execution = jobLauncher.run(job, jobParameters);
            
            log.info("✅ {} job started successfully with execution ID: {}", jobName, execution.getId());
            
        } catch (Exception e) {
            log.error("❌ Error running scheduled {} job: {}", jobName, e.getMessage(), e);
        }
    }
    
    /**
     * Vérifie si un job est en cours d'exécution
     */
    private boolean isJobRunning(String jobName) {
        Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
        return !runningExecutions.isEmpty();
    }
}
