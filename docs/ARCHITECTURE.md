# Architecture Spring Batch - Law.io

## 🏗️ Vue d'ensemble

Cette application utilise **Spring Batch** pour traiter de manière robuste et scalable les documents juridiques du Bénin.

## 📊 Diagramme de flux

```
┌─────────────────────────────────────────────────────────────┐
│                      FULL PIPELINE JOB                       │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
   ┌─────────┐          ┌──────────┐         ┌──────────┐
   │  FETCH  │   ───▶   │ DOWNLOAD │  ───▶   │ EXTRACT  │
   │   STEP  │          │   STEP   │         │   STEP   │
   └─────────┘          └──────────┘         └──────────┘
        │                     │                     │
        ▼                     ▼                     ▼
   Vérifie URL      Télécharge PDF         OCR + Parse
   (HTTP HEAD)      + Calcul SHA256         Articles
```

## 🔄 Architecture Spring Batch

### Components

```
┌──────────────────────────────────────────────────────────┐
│                    SPRING BATCH STEP                      │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────┐      ┌───────────┐      ┌──────────┐     │
│  │  READER  │ ───▶ │ PROCESSOR │ ───▶ │  WRITER  │     │
│  └──────────┘      └───────────┘      └──────────┘     │
│       │                   │                   │          │
│  Génère items     Transforme/Filtre    Persiste         │
│                                                           │
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
                ┌────────────────┐
                │  CHUNK (n=10)  │
                │                │
                │  Commit batch  │
                └────────────────┘
```

### Readers

1. **LawDocumentReader** : Génère tous les documents possibles
   - Années : 1960 → aujourd'hui
   - Types : loi + decret
   - Numéros : 1 → 2000 par année

2. **FilePdfReader** : Lit les PDFs existants sur disque (filesystem)
   - Scan récursif des répertoires
   - Parse les noms de fichiers

### Processors

1. **FetchProcessor** : Vérifie l'existence via HTTP HEAD
   - Filtre les 404
   - Retient les 200

2. **DownloadProcessor** : Télécharge les PDFs
   - Calcul SHA-256
   - Idempotence (skip si existe)

3. **ExtractionProcessor** : Extrait le contenu
   - OCR Tesseract (fallback)
   - Parsing regex des articles
   - Calcul score confiance

### Writers

**TrackingWriter** : Persistance simple avec logs
- Trace les documents traités
- Statistiques en temps réel

## 🔧 Configuration Batch

```yaml
law:
  batch:
    chunk-size: 10        # Items par commit
    max-threads: 4        # Parallélisation
    throttle-limit: 2     # Concurrence max
```

### Chunk-oriented Processing

```java
.<LawDocument, LawDocument>chunk(10, transactionManager)
```

- Lit 10 items
- Traite en parallèle (multi-thread)
- Écrit en batch
- Commit transaction

### Multi-threading

```java
.taskExecutor(taskExecutor())
.throttleLimit(2)
```

- `taskExecutor` : Pool de threads
- `throttleLimit` : Max 2 threads simultanés pour éviter surcharge API

## 📦 Job Repository (H2)

Spring Batch persiste l'état des jobs dans H2 :

```
BATCH_JOB_INSTANCE       # Instances de jobs
BATCH_JOB_EXECUTION      # Exécutions
BATCH_STEP_EXECUTION     # Exécutions de steps
BATCH_JOB_EXECUTION_PARAMS
```

### Avantages

✅ **Restart automatique** : Reprend où le job s'est arrêté  
✅ **Statut persisté** : Survit aux redémarrages  
✅ **Métriques** : Nombre d'items lus/écrits/échoués  
✅ **Historique** : Toutes les exécutions conservées  

## 🎯 Patterns Spring Batch

### 1. Skip Policy

```java
.faultTolerant()
.skip(Exception.class)
.skipLimit(10)
```

Continue même si certains items échouent (max 10).

### 2. Retry Logic

```java
.retryLimit(3)
.retry(IOException.class)
```

Réessaie jusqu'à 3 fois en cas d'erreur réseau.

### 3. Listeners

```java
@Component
public class JobCompletionListener implements JobExecutionListener {
    @Override
    public void afterJob(JobExecution jobExecution) {
        // Actions post-job
    }
}
```

Hooks avant/après job pour logging, notifications, etc.

## 🔐 Transaction Management

Spring Batch gère les transactions automatiquement :

```
Chunk 1 (10 items) ───▶ [Process] ───▶ [Write] ───▶ COMMIT
Chunk 2 (10 items) ───▶ [Process] ───▶ [Write] ───▶ COMMIT
...
```

Si un chunk échoue : **ROLLBACK** uniquement ce chunk.

## 🚀 Scalabilité

### Vertical Scaling

```yaml
batch:
  max-threads: 8  # Plus de threads
  chunk-size: 20  # Chunks plus gros
```

### Horizontal Scaling (futur)

Spring Batch supporte :
- **Remote Partitioning** : Steps sur machines différentes
- **Remote Chunking** : Reader centralisé, processors distribués

## 📊 Monitoring

### Via API REST

```bash
GET /api/batch/status/{jobExecutionId}
```

Retourne :
```json
{
  "jobExecutionId": 1,
  "jobName": "fetchJob",
  "status": "COMPLETED",
  "startTime": "2024-11-19T10:00:00",
  "endTime": "2024-11-19T10:05:00",
  "exitStatus": "COMPLETED"
}
```

### Via H2 Console

```sql
SELECT * FROM BATCH_STEP_EXECUTION 
WHERE JOB_EXECUTION_ID = 1;
```

Colonnes importantes :
- `READ_COUNT` : Items lus
- `WRITE_COUNT` : Items écrits
- `COMMIT_COUNT` : Nombre de commits
- `ROLLBACK_COUNT` : Nombre de rollbacks

## 🔄 Comparaison avec law.io.v2

| Aspect | law.io.v2 | law.spring (Batch) |
|--------|-----------|-------------------|
| **Orchestration** | Scripts Bash séquentiels | Jobs Spring Batch chainés |
| **Parallélisation** | ExecutorService manuel | Spring Batch multi-thread |
| **Idempotence** | Fichiers .txt | Job Repository H2 |
| **Restart** | Ré-exécution complète | Restart depuis échec |
| **Monitoring** | Logs uniquement | API REST + H2 + Metrics |
| **Transaction** | Manuelle | Automatique par chunk |
| **Scalabilité** | Limitée | Horizontale (partitioning) |

## 🎓 Best Practices

### 1. Chunk Size

- **Petit (5-10)** : Moins de mémoire, commits fréquents
- **Grand (50-100)** : Meilleure performance, plus de risque

**Recommandation** : 10-20 pour équilibre.

### 2. Thread Pool

- **Trop de threads** : Surcharge API externe
- **Pas assez** : Sous-utilisation CPU

**Recommandation** : 2-4 threads + throttle limit.

### 3. Skip vs Fail

- **Skip** : Continuer malgré erreurs (logs warning)
- **Fail** : Arrêter tout le job

**Recommandation** : Skip pour erreurs business, fail pour erreurs techniques.

## 📚 Ressources

- [Spring Batch Reference](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Baeldung Spring Batch Guide](https://www.baeldung.com/spring-batch-intro)
- [Spring Batch Patterns](https://spring.io/blog/2021/03/23/spring-batch-patterns)
