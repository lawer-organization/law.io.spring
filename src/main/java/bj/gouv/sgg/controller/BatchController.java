package bj.gouv.sgg.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller pour lancer et monitorer les jobs Batch
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Tag(name = "Batch Jobs", description = "API pour lancer et monitorer les jobs Spring Batch")
public class BatchController {
    
    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    private static final String JOB_EXECUTION_ID_KEY = "jobExecutionId";
    private static final String STATUS_KEY = "status";
    
    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    
    @Qualifier("fetchCurrentJob")
    private final Job fetchCurrentJob;
    
    @Qualifier("fetchPreviousJob")
    private final Job fetchPreviousJob;
    
    @Qualifier("fetchAllJob")
    private final Job fetchAllJob;
    
    @Qualifier("fetchJob")
    private final Job fetchJob;
    
    @Qualifier("downloadJob")
    private final Job downloadJob;
    
    @Qualifier("ocrJob")
    private final Job ocrJob;
    
    @Qualifier("articleExtractionJob")
    private final Job articleExtractionJob;
    
    @Qualifier("consolidateJob")
    private final Job consolidateJob;
    
    @Qualifier("fullPipelineJob")
    private final Job fullPipelineJob;
    
    /**
     * Lance le job de fetch pour l'année en cours
     */
    @Operation(summary = "Lance le fetch pour l'année en cours", 
               description = "Scan complet (1-2000) des documents de l'année en cours sans cache")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/fetch-current")
    public ResponseEntity<Map<String, Object>> runFetchCurrentJob() {
        return runJob(fetchCurrentJob, "Fetch Current Year Job");
    }
    
    /**
     * Lance le job de fetch pour les années précédentes
     */
    @Operation(summary = "Lance le fetch pour les années précédentes", 
               description = "Scan optimisé (1960 à année-1) avec utilisation du cache pour éviter les doublons")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/fetch-previous")
    public ResponseEntity<Map<String, Object>> runFetchPreviousJob() {
        return runJob(fetchPreviousJob, "Fetch Previous Years Job");
    }
    
    /**
     * Lance le fetch complet (current + previous)
     */
    @Operation(summary = "Lance le fetch complet", 
               description = "Exécute d'abord le fetch de l'année en cours puis des années précédentes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/fetch-all")
    public ResponseEntity<Map<String, Object>> runFetchAllJob() {
        return runJob(fetchAllJob, "Fetch All Job");
    }
    
    /**
     * Lance le job de download
     */
    @Operation(summary = "Lance le job de téléchargement", 
               description = "Télécharge les PDFs des documents et les stocke en base de données")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> runDownloadJob() {
        return runJob(downloadJob, "Download Job");
    }
    
    /**
     * Lance le job d'extraction OCR
     */
    @Operation(summary = "Lance le job d'extraction OCR", 
               description = "Extrait le texte OCR et les articles des PDFs stockés en base")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/ocr")
    public ResponseEntity<Map<String, Object>> runOcrJob() {
        return runJob(ocrJob, "OCR Job");
    }
    
    /**
     * Lance le job d'extraction d'articles depuis les fichiers OCR existants
     */
    @Operation(summary = "Lance l'extraction d'articles", 
               description = "Extrait les articles depuis les fichiers OCR et les exporte en JSON sur disque (sans sauvegarder en BD)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> runArticleExtractionJob() {
        return runJob(articleExtractionJob, "Article Extraction Job");
    }
    
    /**
     * Lance le job de consolidation des articles JSON vers la base de données
     */
    @Operation(summary = "Lance la consolidation des articles", 
               description = "Importe les fichiers JSON d'articles vers la base de données")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/consolidate")
    public ResponseEntity<Map<String, Object>> runConsolidateJob() {
        return runJob(consolidateJob, "Consolidation Job");
    }
    
    /**
     * Lance le pipeline complet
     */
    @Operation(summary = "Lance le pipeline complet", 
               description = "Exécute fetch + download + extract en une seule opération. " +
                           "Supporte le traitement d'un document spécifique via documentId (ex: loi-2025-1)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Job démarré avec succès"),
        @ApiResponse(responseCode = "409", description = "Job déjà en cours d'exécution"),
        @ApiResponse(responseCode = "400", description = "Paramètre documentId invalide"),
        @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @PostMapping("/full-pipeline")
    public ResponseEntity<Map<String, Object>> runFullPipeline(
            @Parameter(description = "ID du document à traiter (ex: loi-2025-1, decret-2024-15). " +
                                   "Si non spécifié, traite tous les documents")
            @RequestParam(required = false) String documentId,
            @Parameter(description = "Force le retraitement même si le document existe déjà en BD")
            @RequestParam(defaultValue = "false") boolean force) {
        
        if (documentId != null && !documentId.isEmpty()) {
            return runSingleDocumentPipeline(documentId, force);
        }
        
        return runJob(fullPipelineJob, "Full Pipeline Job");
    }
    
    private ResponseEntity<Map<String, Object>> runSingleDocumentPipeline(String documentId, boolean force) {
        try {
            // Parser le documentId (format: type-year-number, ex: loi-2025-1)
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                return ResponseEntity.badRequest()
                    .body(Map.of(ERROR_KEY, "Format documentId invalide. Attendu: type-year-number (ex: loi-2025-1)"));
            }
            
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2]);
            
            if (!type.equals("loi") && !type.equals("decret")) {
                return ResponseEntity.badRequest()
                    .body(Map.of(ERROR_KEY, "Type invalide. Attendu: 'loi' ou 'decret'"));
            }
            
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("documentId", documentId)
                .addString("type", type)
                .addLong("year", (long) year)
                .addLong("number", (long) number)
                .addString("force", String.valueOf(force))
                .toJobParameters();
            
            JobExecution execution = jobLauncher.run(fullPipelineJob, jobParameters);
            
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, "Single Document Pipeline started for " + documentId);
            response.put("documentId", documentId);
            response.put("force", force);
            response.put(JOB_EXECUTION_ID_KEY, execution.getId());
            response.put(STATUS_KEY, execution.getStatus().toString());
            
            log.info("Single Document Pipeline started for {} (force={}) with execution ID: {}", 
                documentId, force, execution.getId());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                .body(Map.of(ERROR_KEY, "Année ou numéro invalide dans documentId"));
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("Pipeline is already running for document");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, "Job is already running"));
        } catch (Exception e) {
            log.error("Error starting pipeline: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
    
    /**
     * Récupère le statut d'un job
     */
    @Operation(summary = "Récupère le statut d'un job", 
               description = "Obtient les détails d'exécution d'un job batch par son ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statut récupéré avec succès"),
        @ApiResponse(responseCode = "404", description = "Job non trouvé")
    })
    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(
        @Parameter(description = "ID de l'exécution du job", required = true)
        @PathVariable Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put(JOB_EXECUTION_ID_KEY, execution.getId());
        response.put("jobName", execution.getJobInstance().getJobName());
        response.put(STATUS_KEY, execution.getStatus().toString());
        response.put("startTime", execution.getStartTime());
        response.put("endTime", execution.getEndTime());
        response.put("exitStatus", execution.getExitStatus().getExitCode());
        
        return ResponseEntity.ok(response);
    }
    
    private ResponseEntity<Map<String, Object>> runJob(Job job, String jobDescription) {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
            
            JobExecution execution = jobLauncher.run(job, jobParameters);
            
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, jobDescription + " started successfully");
            response.put(JOB_EXECUTION_ID_KEY, execution.getId());
            response.put(STATUS_KEY, execution.getStatus().toString());
            
            log.info("{} started with execution ID: {}", jobDescription, execution.getId());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("{} is already running", jobDescription);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, "Job is already running"));
                
        } catch (JobRestartException | JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
            log.error("Error starting {}: {}", jobDescription, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
