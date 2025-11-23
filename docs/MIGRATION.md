# Migration law.io.v2 → law.spring

## Différences principales

### 1. Framework
- **Avant** : Java pur + scripts Bash
- **Après** : Spring Boot + Spring Batch

### 2. Orchestration
- **Avant** : Scripts séquentiels (0_run_all.sh)
- **Après** : Jobs Spring Batch chainés

### 3. Configuration
- **Avant** : Constantes Java hardcodées
- **Après** : Properties externalisées (YAML)

### 4. API
- **Avant** : Aucune
- **Après** : REST API complète

### 5. Monitoring
- **Avant** : Logs console uniquement
- **Après** : H2 console + API status + métriques

### 6. Persistance état
- **Avant** : Fichiers .txt (fetch.result.txt, etc.)
- **Après** : Job Repository H2 + fichiers

## Correspondances

| law.io.v2 | law.spring |
|-----------|------------|
| `FetchCurrentJob.java` | `fetchJob` (Spring Batch) |
| `CrawlerJob.java` | `downloadJob` |
| `ExtractJob.java` | `extractJob` |
| `ConsolidationService.java` | `ConsolidationService` (identique) |
| `TesseractOcrService.java` | `TesseractOcrService` (identique) |
| `ArticleExtractor.java` | `ArticleExtractorService` |
| Scripts `src/bin/*.sh` | API REST `/api/batch/*` |

## Services réutilisés

Ces services sont identiques ou très similaires :

- ✅ `TesseractOcrService` : Extraction OCR
- ✅ `ArticleExtractorService` : Parsing regex
- ✅ `ConsolidationService` : Fusion JSON
- ✅ Modèles : `Article`, `DocumentMetadata`, `Signatory`

## Nouveaux composants Spring Batch

### Readers
- `LawDocumentReader` : Génération documents
- `PdfFileReader` : Lecture PDFs

### Processors
- `FetchProcessor` : Vérification HTTP
- `DownloadProcessor` : Téléchargement
- `ExtractionProcessor` : OCR + Parsing

### Writers
- `TrackingWriter` : Logging & tracking

### Configuration
- `BatchJobConfiguration` : Définition des jobs
- `LawProperties` : Configuration externalisée

### Controllers
- `BatchController` : API REST

## Migration du code

### Exemple : FetchJob

**Avant (law.io.v2)** :
```java
public class FetchCurrentJob {
    public static void main(String[] args) {
        FetchService.runCurrent(maxUrl, maxTime, threads);
    }
}
```

**Après (law.spring)** :
```java
@Bean
public Job fetchJob(Step fetchStep) {
    return new JobBuilder("fetchJob", jobRepository)
        .start(fetchStep)
        .build();
}

@Bean
public Step fetchStep(Reader reader, Processor processor, Writer writer) {
    return new StepBuilder("fetchStep", jobRepository)
        .<LawDocument, LawDocument>chunk(10, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .taskExecutor(taskExecutor())
        .build();
}
```

### Avantages de la migration

1. **Restart automatique** : Si un job échoue, Spring Batch peut reprendre
2. **Transaction management** : Commits automatiques par chunk
3. **Monitoring** : Métriques détaillées dans H2
4. **API REST** : Lancement et suivi via HTTP
5. **Multi-threading** : Parallélisation native
6. **Scalabilité** : Support partitioning/chunking distribué

## Commandes équivalentes

### law.io.v2
```bash
./src/bin/1_fetch.current.sh
./src/bin/3_crawl.sh
./src/bin/4_extract.sh
```

### law.spring
```bash
curl -X POST http://localhost:8080/api/batch/fetch
curl -X POST http://localhost:8080/api/batch/download
curl -X POST http://localhost:8080/api/batch/extract

# Ou pipeline complet
curl -X POST http://localhost:8080/api/batch/full-pipeline
```

## Points d'attention

### Compatibilité données

Les deux versions partagent la même structure de fichiers :
```
src/database/
├── data/
│   ├── pdfs/
│   ├── ocr/
│   ├── articles/
│   └── output.json
```

→ **Interopérabilité complète** entre les deux versions.

### Dépendances

Les mêmes bibliothèques sont utilisées :
- PDFBox 3.0.3
- Tesseract (JavaCPP)
- Gson
- Apache HttpClient5

## Prochaines étapes

1. ✅ Migration basique terminée
2. ⏳ Tests d'intégration
3. ⏳ Retry policies
4. ⏳ Skip policies
5. ⏳ Métriques Spring Boot Actuator
6. ⏳ Partitioning pour scalabilité horizontale

## Conclusion

La migration vers Spring Batch apporte :
- 🎯 Architecture professionnelle
- 🔄 Reprise sur échec
- 📊 Monitoring avancé
- 🚀 Scalabilité
- 🔌 Intégration Spring Boot

Tout en conservant la logique métier existante (OCR, parsing, consolidation).
