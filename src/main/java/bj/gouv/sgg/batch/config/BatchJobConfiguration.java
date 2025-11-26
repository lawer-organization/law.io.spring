package bj.gouv.sgg.batch.config;

import bj.gouv.sgg.batch.listener.TelegramJobExecutionListener;
import bj.gouv.sgg.batch.processor.ArticleExtractionProcessor;
import bj.gouv.sgg.batch.processor.ConsolidationProcessor;
import bj.gouv.sgg.batch.processor.DownloadProcessor;
import bj.gouv.sgg.batch.processor.ExtractionProcessor;
import bj.gouv.sgg.batch.processor.FetchProcessor;
import bj.gouv.sgg.batch.reader.ConsolidationReader;
import bj.gouv.sgg.batch.reader.CurrentYearLawDocumentReader;
import bj.gouv.sgg.batch.reader.DownloadedDocumentReader;
import bj.gouv.sgg.batch.reader.FetchedDocumentReader;
import bj.gouv.sgg.batch.reader.LawDocumentReader;
import bj.gouv.sgg.batch.reader.FilePdfReader;
import bj.gouv.sgg.batch.reader.OcrFileReader;
import bj.gouv.sgg.batch.reader.PreviousYearsLawDocumentReader;
import bj.gouv.sgg.batch.reader.SingleDocumentReaderFactory;
import bj.gouv.sgg.batch.writer.ArticleExtractionWriter;
import bj.gouv.sgg.batch.writer.ConsolidationWriter;
import bj.gouv.sgg.batch.writer.FileDownloadWriter;
import bj.gouv.sgg.batch.writer.ExtractionWriter;
import bj.gouv.sgg.batch.writer.FetchWriter;
import bj.gouv.sgg.model.ArticleExtraction;
import bj.gouv.sgg.service.NotFoundRangeService;
import bj.gouv.sgg.batch.writer.ForceAwareWriter;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration des jobs Spring Batch
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchJobConfiguration {
    
    private final LawProperties properties;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TelegramJobExecutionListener telegramJobExecutionListener;
    
    // ========================================================================
    // FETCH CURRENT YEAR JOB - Scan complet de l'année en cours
    // ========================================================================
    
    @Bean
    public Job fetchCurrentJob(Step fetchCurrentStep) {
        return new JobBuilder("fetchCurrentJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fetchCurrentStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step fetchCurrentStep(CurrentYearLawDocumentReader currentYearReader, 
                                 FetchProcessor processor,
                                 FetchWriter fetchWriter,
                                 NotFoundRangeService notFoundRangeService) {
        return new StepBuilder("fetchCurrentStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(currentYearReader)
            .processor(processor)
            .writer(fetchWriter)
            .taskExecutor(taskExecutor())
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution stepExecution) {
                    currentYearReader.reset();
                    processor.resetStats();
                }
                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    // Consolidation globale des ranges NOT_FOUND pour l'année courante
                    int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                    int merged = notFoundRangeService.consolidateRanges("loi", year);
                    log.info("Post-step consolidation: merged={} year={}", merged, year);
                    log.info("FetchProcessor stats: {}", processor.statsSummary());
                    return stepExecution.getExitStatus();
                }
            })
            .build();
    }
    
    // ========================================================================
    // FETCH PREVIOUS YEARS JOB - Scan optimisé avec cache (1960 à année-1)
    // ========================================================================
    
    @Bean
    public Job fetchPreviousJob(Step fetchPreviousStep) {
        return new JobBuilder("fetchPreviousJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fetchPreviousStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step fetchPreviousStep(PreviousYearsLawDocumentReader previousYearsReader, 
                                  FetchProcessor processor,
                                  FetchWriter fetchWriter) {
        return new StepBuilder("fetchPreviousStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(previousYearsReader)
            .processor(processor)
            .writer(fetchWriter)
            .taskExecutor(taskExecutor())
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution stepExecution) {
                    previousYearsReader.reset();
                }
            })
            .build();
    }
    
    // ========================================================================
    // FETCH ALL JOB - Fetch current + previous en séquence
    // ========================================================================
    
    @Bean
    public Job fetchAllJob(Step fetchCurrentStep, Step fetchPreviousStep) {
        return new JobBuilder("fetchAllJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fetchCurrentStep)
            .next(fetchPreviousStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    // ========================================================================
    // LEGACY FETCH JOB - Utilise l'ancien LawDocumentReader
    // @Deprecated - utiliser fetchCurrentJob et fetchPreviousJob à la place
    // ========================================================================
    
    @Bean
    public Job fetchJob(Step fetchStep) {
        return new JobBuilder("fetchJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fetchStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step fetchStep(LawDocumentReader reader, 
                          FetchProcessor processor,
                          FetchWriter fetchWriter) {
        return new StepBuilder("fetchStep", jobRepository)
            .<LawDocument, LawDocument>chunk(properties.getBatch().getChunkSize(), transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(fetchWriter)
            .taskExecutor(taskExecutor())
            .build();
    }
    
    // ========================================================================
    // DOWNLOAD JOB - Télécharge les PDFs
    // ========================================================================
    
    @Bean
    public Job downloadJob(Step downloadStep) {
        return new JobBuilder("downloadJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(downloadStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step downloadStep(FetchedDocumentReader reader,
                             DownloadProcessor downloadProcessor,
                             FileDownloadWriter downloadWriter) {
        
        // Le reader ne retourne que les documents FETCHED, donc pas besoin de FetchProcessor
        // On télécharge directement en mono-thread pour éviter les duplicates
        // Le processor télécharge le PDF et le writer le sauvegarde en base
        return new StepBuilder("downloadStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager) // Process one document at a time
            .reader(reader)
            .processor(downloadProcessor)
            .writer(downloadWriter) // Sauvegarde dans download_results
            // Pas de taskExecutor = exécution synchrone en mono-thread
            .build();
    }
    
    // ========================================================================
    // EXTRACT JOB - Extrait les articles
    // ========================================================================
    
    @Bean
    public Job ocrJob(Step ocrStep) {
        return new JobBuilder("ocrJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(ocrStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step ocrStep(DownloadedDocumentReader reader,
                        ExtractionProcessor processor,
                        ExtractionWriter extractionWriter) {
        return new StepBuilder("ocrStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager) // Process one document at a time
            .reader(reader)
            .processor(processor)
            .writer(extractionWriter)
            // Pas de taskExecutor = exécution synchrone en mono-thread
            .build();
    }
    
    // ========================================================================
    // ARTICLE EXTRACTION JOB - Extrait les articles depuis les fichiers OCR existants
    // ========================================================================
    
    @Bean
    public Job articleExtractionJob(Step articleExtractionStep) {
        return new JobBuilder("articleExtractionJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(articleExtractionStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step articleExtractionStep(OcrFileReader ocrFileReader,
                                      ArticleExtractionProcessor articleExtractionProcessor,
                                      ArticleExtractionWriter articleExtractionWriter) {
        return new StepBuilder("articleExtractionStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager)
            .reader(ocrFileReader)
            .processor(articleExtractionProcessor)
            .writer(articleExtractionWriter)
            .build();
    }
    
    // ========================================================================
    // CONSOLIDATION JOB - Consolide les fichiers JSON d'articles en base de données
    // ========================================================================
    
    @Bean
    public Job consolidateJob(Step consolidateStep) {
        return new JobBuilder("consolidateJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(consolidateStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    @Bean
    public Step consolidateStep(ConsolidationReader consolidationReader,
                                ConsolidationProcessor consolidationProcessor,
                                ConsolidationWriter consolidationWriter) {
        return new StepBuilder("consolidateStep", jobRepository)
            .<LawDocument, java.util.List<ArticleExtraction>>chunk(1, transactionManager)
            .reader(consolidationReader)
            .processor(consolidationProcessor)
            .writer(consolidationWriter)
            .build();
    }
    
    // ========================================================================
    // FULL PIPELINE JOB - Fetch All -> Download -> Extract
    // Supporte le mode single document via job parameters
    // ========================================================================
    
    @Bean
    public Job fullPipelineJob(Step singleDocumentFetchStep, 
                               Step singleDocumentDownloadStep, 
                               Step singleDocumentOcrStep) {
        return new JobBuilder("fullPipelineJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(singleDocumentFetchStep)
            .next(singleDocumentDownloadStep)
            .next(singleDocumentOcrStep)
            .listener(telegramJobExecutionListener)
            .build();
    }
    
    // ========================================================================
    // SINGLE DOCUMENT STEPS - Pour le traitement d'un document spécifique
    // ========================================================================
    
    @Bean
    public Step singleDocumentFetchStep(SingleDocumentReaderFactory readerFactory,
                                        FetchProcessor processor,
                                        ForceAwareWriter writer) {
        return new StepBuilder("singleDocumentFetchStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager)
            .reader(readerFactory)
            .processor(processor)
            .writer(writer)
            .build();
    }
    
    @Bean
    public Step singleDocumentDownloadStep(SingleDocumentReaderFactory readerFactory,
                                           DownloadProcessor downloadProcessor,
                                           FileDownloadWriter downloadWriter) {
        return new StepBuilder("singleDocumentDownloadStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager)
            .reader(readerFactory)
            .processor(downloadProcessor)
            .writer(downloadWriter)
            .build();
    }
    
    @Bean
    public Step singleDocumentOcrStep(FilePdfReader reader,
                                      ExtractionProcessor processor,
                                      ExtractionWriter extractionWriter) {
        return new StepBuilder("singleDocumentOcrStep", jobRepository)
            .<LawDocument, LawDocument>chunk(1, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(extractionWriter)
            .build();
    }
    
    // ========================================================================
    // Task Executor
    // ========================================================================
    
    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("law-batch-");
        
        // Limite de threads basée sur la capacité de la machine
        int maxThreads = properties.getBatch().getMaxThreads();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        
        // Ne jamais dépasser le nombre de processeurs disponibles
        int concurrencyLimit = Math.min(maxThreads, availableProcessors);
        
        log.info("TaskExecutor configuration: maxThreads={}, availableProcessors={}, concurrencyLimit={}", 
                 maxThreads, availableProcessors, concurrencyLimit);
        
        executor.setConcurrencyLimit(concurrencyLimit);
        return executor;
    }
}
