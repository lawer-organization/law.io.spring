# Law Spring Batch - Guide de démarrage

## 📚 Documentation

Ce guide couvre l'installation et l'utilisation basique. Pour plus de détails :

- **[Index Documentation](./README.md)** - Navigation complète
- **[Architecture](./ARCHITECTURE.md)** - Vue d'ensemble du système
- **[Jobs Batch](./BATCH_ARCHITECTURE.md)** - Détails des jobs Spring Batch
- **[Pipeline](./EXTRACTION_CONSOLIDATION.md)** - Extraction et consolidation
- **[Exceptions](./EXCEPTIONS.md)** - Gestion des erreurs

## Installation

1. **Cloner le projet**
```bash
cd /Volumes/FOLDER/dev/projects/law.spring/law.spring
```

2. **Compiler**
```bash
mvn clean package
```

3. **Lancer l'application**
```bash
./start.sh
# ou
mvn spring-boot:run
```

## Utilisation

### Via l'API REST

L'application expose une API REST sur `http://localhost:8080`

#### Lancer les jobs

```bash
# Fetch (vérifier existence)
curl -X POST http://localhost:8080/api/batch/fetch

# Download (télécharger PDFs)
curl -X POST http://localhost:8080/api/batch/download

# Extract (extraire articles)
curl -X POST http://localhost:8080/api/batch/extract

# Pipeline complet
curl -X POST http://localhost:8080/api/batch/full-pipeline
```

#### Ou via le script helper

```bash
./run-job.sh fetch
./run-job.sh download
./run-job.sh extract
./run-job.sh full
```

#### Vérifier le statut

```bash
# Récupérer l'ID retourné lors du lancement
./run-job.sh status 1
```

### Console H2

Accéder à la console de la base de données Spring Batch :

```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/batch-db
Username: sa
Password: (vide)
```

Tables importantes :
- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_STEP_EXECUTION`

## Configuration

Modifier `src/main/resources/application.yml` :

```yaml
law:
  batch:
    chunk-size: 10        # Taille des chunks
    max-threads: 4        # Parallélisation
    throttle-limit: 2     # Limite concurrence
```

## Architecture

### Jobs disponibles

1. **fetchJob** : Vérifie l'existence des documents via HTTP HEAD
   - Reader: Génère tous les documents possibles
   - Processor: Vérifie HTTP 200/404
   - Writer: Tracking

2. **downloadJob** : Télécharge les PDFs
   - Reader: Documents à télécharger
   - Processor: Fetch + Download
   - Writer: Sauvegarde tracking

3. **extractJob** : Extrait les articles
   - Reader: PDFs existants
   - Processor: OCR + Parsing
   - Writer: JSON individuel

4. **fullPipelineJob** : Enchaine fetch → download → extract

### Fonctionnalités Spring Batch

- ✅ **Chunk processing** : Traitement par lots
- ✅ **Multi-threading** : Parallélisation
- ✅ **Throttling** : Limite de concurrence
- ✅ **Restart** : Reprise après échec
- ✅ **Skip** : Gestion des erreurs
- ✅ **Job Repository** : État persisté

## Monitoring

### Logs

```bash
tail -f logs/law-spring-batch.log
```

### Métriques

Via la console H2, consulter :
- Nombre d'items lus/écrits
- Durée d'exécution
- Statut (COMPLETED, FAILED, etc.)

## Données générées

```
src/database/
├── data/
│   ├── pdfs/
│   │   ├── loi/        # PDFs lois
│   │   └── decret/     # PDFs décrets
│   ├── ocr/
│   │   ├── loi/        # Textes extraits
│   │   └── decret/
│   ├── articles/
│   │   ├── loi/        # JSONs individuels
│   │   └── decret/
│   └── output.json     # Consolidation finale
```

## Troubleshooting

### Port déjà utilisé
```bash
# Changer le port dans application.yml
server:
  port: 8081
```

### Erreurs OCR
- Vérifier que `tessdata/fra.traineddata` existe
- Configurer `TESSDATA_PREFIX` si nécessaire

### Job déjà en cours
```
HTTP 409 - Job is already running
```
Attendre la fin du job ou le stopper via H2 console.

## Développement

### Tests
```bash
mvn test
```

### Build sans tests
```bash
mvn package -DskipTests
```

### Mode debug
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

## Ressources

- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
