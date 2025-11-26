# 📚 Guide Complet - Law Spring Batch

**Version:** 1.0.0  
**Date:** 24 novembre 2025  
**Raspberry Pi:** Déploiement optimisé ✅

---

## 📑 Table des Matières

1. [Introduction et Vue d'ensemble](#1-introduction-et-vue-densemble)
2. [Architecture du Système](#2-architecture-du-système)
3. [Architecture Spring Batch](#3-architecture-spring-batch)
4. [API REST Reference](#4-api-rest-reference)
5. [Déploiement](#5-déploiement)
6. [Configuration et Monitoring](#6-configuration-et-monitoring)
7. [Troubleshooting](#7-troubleshooting)
8. [Structure du Projet](#8-structure-du-projet)

---

## 1. Introduction et Vue d'ensemble

### Qu'est-ce que Law Spring Batch ?

Application Spring Boot utilisant Spring Batch pour automatiser le traitement des documents juridiques du Bénin (lois et décrets). Le système récupère, télécharge, extrait et consolide automatiquement les documents depuis le site officiel du SGG.

### Fonctionnalités principales

- ✅ **Récupération automatique** des documents via HTTP
- ✅ **Téléchargement et stockage** des PDFs
- ✅ **Extraction OCR** avec Tesseract
- ✅ **Parsing intelligent** des articles
- ✅ **API REST** pour contrôle et consultation
- ✅ **Scheduler intégré** pour exécution automatique
- ✅ **Optimisé Raspberry Pi** avec gestion mémoire

### Technologies

- **Spring Boot 3.2.0** - Framework principal
- **Spring Batch 5.x** - Processing batch
- **Spring Scheduler** - Jobs automatiques
- **MariaDB/MySQL** - Base de données
- **Tesseract OCR** - Extraction de texte
- **Maven** - Build et déploiement
- **systemd** - Service système

---

## 2. Architecture du Système

### Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────┐
│                      SPRING SCHEDULER                       │
│  Jobs automatiques configurés avec expressions cron         │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
   ┌─────────┐          ┌──────────┐         ┌──────────┐
   │  FETCH  │   ───▶   │ DOWNLOAD │  ───▶   │ EXTRACT  │
   │   JOB   │          │   JOB    │         │   JOB    │
   └─────────┘          └──────────┘         └──────────┘
        │                     │                     │
        ▼                     ▼                     ▼
   Vérifie URL      Télécharge PDF         OCR + Parse
   (HTTP HEAD)      + Stockage DB          Articles
```

### Scheduler Configuration

Les jobs s'exécutent automatiquement selon le planning suivant :

| Job | Cron Expression | Fréquence | Description |
|-----|----------------|-----------|-------------|
| `fetch-current` | `0 0 6,12,18 * * *` | 3x/jour | Récupération année courante |
| `fetch-previous` | `0 30 * * * *` | Horaire | Récupération années précédentes |
| `download` | `0 0 */2 * * *` | 2h (heures paires) | Téléchargement PDFs |
| `ocr` | `0 30 */2 * * *` | 2h (heures paires) | Extraction OCR |
| `extract` | `0 0 1-23/2 * * *` | 2h (heures impaires) | Extraction articles |
| `consolidate` | `0 30 1-23/2 * * *` | 2h (heures impaires) | Consolidation |

**Configuration thread pool:** 1 thread pour exécution séquentielle (optimisation Raspberry Pi)

### Flux de données complet

```
┌─────────────┐
│  Site SGG   │
│sgg.gouv.bj  │
└──────┬──────┘
       │
       │ HTTP HEAD (Fetch)
       ▼
┌─────────────────┐
│ fetch_results   │◀── Table MySQL
└────────┬────────┘
         │
         │ Filter exists=true
         ▼
┌─────────────────┐
│  Site SGG PDF   │
└────────┬────────┘
         │
         │ HTTP GET (Download)
         ▼
┌──────────────────┐
│download_results  │◀── LONGBLOB MySQL
│  (PDF bytes)     │
└────────┬─────────┘
         │
         │ Load PDF bytes
         ▼
┌──────────────────┐
│  TesseractOCR    │◀── Service
└────────┬─────────┘
         │
         │ OCR text
         ▼
┌──────────────────┐
│  ocr_results     │◀── LONGTEXT MySQL
└────────┬─────────┘
         │
         │ Parse articles
         ▼
┌──────────────────┐
│ArticleExtractor  │◀── Service
└────────┬─────────┘
         │
         │ Articles individuels
         ▼
┌──────────────────┐
│article_extractions│◀── Table MySQL
└──────────────────┘
```

---

## 3. Architecture Spring Batch

### 3.1. Pattern Reader-Processor-Writer

Chaque job utilise le pattern classique Spring Batch :

```
┌────────────────┐   ┌─────────────┐   ┌───────────┐
│     READER     │──▶│  PROCESSOR  │──▶│  WRITER   │
└────────────────┘   └─────────────┘   └───────────┘
       │                    │                  │
  Génère items     Transforme/Enrichit   Persiste
```

### 3.2. Fetch Job - Vérification documents

#### Fetch Current Step - Année courante

**Objectif:** Scanner tous les documents de l'année en cours (1-2000)

```
┌─────────────────────────────────────────────────────────────┐
│                    FETCH CURRENT STEP                        │
├─────────────────────────────────────────────────────────────┤
│  ┌────────────────────┐   ┌─────────────┐   ┌───────────┐ │
│  │CurrentYearReader   │──▶│FetchProcessor│──▶│TrackingWriter│
│  └────────────────────┘   └─────────────┘   └───────────┘ │
│         │                       │                  │        │
│    Génère URLs          Vérifie HTTP HEAD    Sauvegarde   │
│    année = CURRENT      Code 200 = exists   fetch_results │
│    numéros 1-2000       Auto-retry padding                 │
│    IGNORE cache BD      Gère redirections                  │
└─────────────────────────────────────────────────────────────┘
```

**Configuration:**
- Année: Dynamique (Calendar.getInstance())
- Numéros: 1 à 2000
- Padding: Auto-retry pour numéros 1-9 (ex: 1 → 01)
- Cache: DÉSACTIVÉ (nouveaux documents possibles)

#### Fetch Previous Step - Années précédentes

**Objectif:** Scanner intelligemment 1960 à (année-1) en évitant les URLs connues

```
┌─────────────────────────────────────────────────────────────┐
│                   FETCH PREVIOUS STEP                        │
├─────────────────────────────────────────────────────────────┤
│  ┌────────────────────┐   ┌─────────────┐   ┌───────────┐ │
│  │PreviousYearsReader │──▶│FetchProcessor│──▶│TrackingWriter│
│  └────────────────────┘   └─────────────┘   └───────────┘ │
│         │                       │                  │        │
│    Génère URLs          Vérifie HTTP HEAD    Sauvegarde   │
│    années: 1960 à N-1   Code 200 = exists   fetch_results │
│    numéros: 1-2000      Auto-retry padding                 │
│    UTILISE cache BD     Skip exists/notfound               │
└─────────────────────────────────────────────────────────────┘
```

**Configuration:**
- Années: 1960 à (CURRENT_YEAR - 1)
- Cache: ACTIVÉ (fetch_results)
- Skip si exists=true OU exists=false
- Optimisation: pas de nouveaux documents attendus

### 3.3. Download Job - Téléchargement PDFs

```
┌─────────────────────────────────────────────────────────────┐
│                       DOWNLOAD JOB                           │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐   ┌─────────────────┐   ┌─────────────┐ │
│  │LawDocReader  │──▶│CompositeProcessor│──▶│TrackingWriter│
│  └──────────────┘   └─────────────────┘   └─────────────┘ │
│         │                    │                     │        │
│    Génère URLs        FetchProcessor        Sauvegarde     │
│    (loi/decret)       DownloadProcessor     LONGBLOB       │
│                       PDF + SHA-256         fileSize       │
└─────────────────────────────────────────────────────────────┘
```

**Données stockées:**
- documentId (clé unique)
- URL du document
- pdfContent (LONGBLOB)
- sha256 (checksum intégrité)
- fileSize (bytes)
- downloadedAt (timestamp)

### 3.4. Extract Job - OCR et Parsing

```
┌─────────────────────────────────────────────────────────────┐
│                        EXTRACT JOB                           │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐   ┌────────────────┐   ┌─────────────┐  │
│  │FilePdfReader │──▶│ExtractionProc  │──▶│TrackingWriter│  │
│  └──────────────┘   └────────────────┘   └─────────────┘  │
│         │                    │                    │         │
│    Lit depuis BD        TesseractOcr      Sauvegarde       │
│    download_results     ArticleExtractor  ocr_results      │
│    (LONGBLOB)           Regex parsing     article_extract  │
└─────────────────────────────────────────────────────────────┘
```

**Workflow extraction:**
1. Vérification cache OCR (évite retraitement)
2. Extraction OCR (PDFBox direct ou Tesseract si nécessaire)
3. Parsing articles avec regex patterns
4. Extraction métadonnées (signataires, dates, etc.)
5. Sauvegarde en base de données

### 3.5. Configuration Batch

```yaml
law:
  batch:
    chunk-size: 10        # Items par commit
    max-threads: 4        # Parallélisation
    throttle-limit: 2     # Concurrence max

spring:
  task:
    scheduling:
      pool:
        size: 1           # Thread pool scheduler
```

**Optimisations Raspberry Pi:**
- Chunk size: 10 (équilibre mémoire/performance)
- Thread pool: 1 (exécution séquentielle)
- JVM heap: 256MB-800MB
- Transaction automatique par chunk

---

## 4. API REST Reference

### Base URL

- **Local:** `http://localhost:8080`
- **Raspberry Pi:** `http://192.168.0.37:8080`

### 4.1. Batch Jobs API

#### Lancer les jobs

```bash
# Fetch année courante
POST /api/batch/fetch-current

# Fetch années précédentes
POST /api/batch/fetch-previous

# Télécharger PDFs
POST /api/batch/download

# Extraction OCR
POST /api/batch/ocr

# Extraction articles
POST /api/batch/extract

# Pipeline complet
POST /api/batch/full-pipeline
```

**Response:**
```json
{
  "jobExecutionId": 1,
  "message": "Job started successfully",
  "status": "STARTED"
}
```

#### Statut d'un job

```bash
GET /api/batch/status/{jobExecutionId}
```

**Response:**
```json
{
  "jobName": "fetchCurrentJob",
  "jobExecutionId": 1,
  "startTime": "2025-11-24T06:00:00",
  "endTime": "2025-11-24T06:18:13",
  "status": "COMPLETED",
  "exitStatus": "COMPLETED"
}
```

### 4.2. Articles API

#### Export articles

```bash
GET /api/articles/export
```

**Response:** Array de tous les articles extraits

#### Statistiques articles

```bash
GET /api/articles/stats
```

**Response:**
```json
{
  "totalArticles": 544,
  "byYear": {
    "2025": 431,
    "2024": 113
  },
  "byType": {
    "loi": 544
  }
}
```

### 4.3. Documents API

#### Documents par année

```bash
GET /api/fetch-results/{year}
```

#### Statistiques documents

```bash
GET /api/fetch-results/stats
```

**Response:**
```json
{
  "totalDocuments": 16,
  "byStatus": {
    "EXTRACTED": 15,
    "DOWNLOADED": 1
  },
  "byYear": {
    "2025": 16
  }
}
```

### 4.4. Files API

#### Statistiques fichiers

```bash
GET /api/files/stats
```

**Response:**
```json
{
  "pdfs": {
    "count": 16,
    "totalSizeMB": "123.54 MB"
  },
  "ocr": {
    "count": 16,
    "totalSizeMB": "0.68 MB"
  },
  "articles": {
    "count": 10,
    "totalSizeMB": "0.37 MB"
  }
}
```

#### Lister fichiers

```bash
GET /api/files/pdfs      # Liste PDFs
GET /api/files/ocr       # Liste OCR
GET /api/files/articles  # Liste articles JSON
```

#### Télécharger fichiers

```bash
GET /api/files/pdfs/{filename}
GET /api/files/ocr/{filename}
GET /api/files/articles/{filename}
```

#### Lire contenu

```bash
GET /api/files/ocr/{filename}/content
GET /api/files/articles/{filename}/content
```

### 4.5. Health Check

```bash
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP"
}
```

---

## 5. Déploiement

### 5.1. Déploiement Maven sur Raspberry Pi

#### Configuration SSH

1. **Générer clé SSH:**

```bash
ssh-keygen -t rsa -b 4096
ssh-copy-id pi@192.168.0.37
ssh pi@192.168.0.37 "echo 'Connexion réussie!'"
```

2. **Configurer Maven (~/.m2/settings.xml):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
    <servers>
        <server>
            <id>raspi</id>
            <username>pi</username>
            <privateKey>${user.home}/.ssh/id_rsa</privateKey>
        </server>
    </servers>
</settings>
```

#### Déploiement

```bash
# Déploiement complet
cd law.spring
mvn clean package deploy

# Déploiement sans tests
mvn deploy -DskipTests
```

**Actions effectuées:**
1. ✅ Compilation
2. ✅ Tests
3. ✅ Package JAR
4. ✅ Transfert SSH vers /home/pi/law-spring/
5. ✅ Redémarrage service (si configuré)

### 5.2. Configuration Raspberry Pi

#### Prérequis

- Raspberry Pi 2/3/4 (1GB RAM minimum)
- Raspberry Pi OS 64-bit
- Connexion SSH
- 10GB espace disque

#### Installation automatique

```bash
# Télécharger script
ssh pi@192.168.0.37
curl -O https://raw.githubusercontent.com/lawer-organization/law.io.spring/main/scripts/raspi-setup.sh
chmod +x raspi-setup.sh

# Exécuter installation
./raspi-setup.sh
```

**Le script installe:**
- ✅ Java 17, Maven, Git
- ✅ MariaDB Server
- ✅ Base de données law_batch
- ✅ Clone et build du projet
- ✅ Service systemd optimisé
- ✅ Répertoires de données

#### Service systemd

**Fichier:** `/etc/systemd/system/law-spring-batch.service`

```ini
[Unit]
Description=Law Spring Batch Application
After=network.target mariadb.service

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/law-spring
ExecStart=/usr/bin/java -Xms256m -Xmx800m \
  -jar /home/pi/law-spring/law-spring-batch-1.0.0-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment="SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/law_batch"
Environment="SPRING_DATASOURCE_USERNAME=law_user"
Environment="SPRING_DATASOURCE_PASSWORD=law_password_2024"
Environment="SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect"
Environment="DATA_DIRECTORY=/home/pi/law-data"

[Install]
WantedBy=multi-user.target
```

**Commandes:**

```bash
# Démarrer
sudo systemctl start law-spring-batch

# Arrêter
sudo systemctl stop law-spring-batch

# Redémarrer
sudo systemctl restart law-spring-batch

# Statut
sudo systemctl status law-spring-batch

# Activer au boot
sudo systemctl enable law-spring-batch

# Logs
sudo journalctl -u law-spring-batch -f
```

### 5.3. Configuration Mémoire

**Adaptation selon RAM disponible:**

| Raspberry Pi | RAM | JVM Settings |
|--------------|-----|--------------|
| 1GB | 906 Mo | `-Xms256m -Xmx800m` (défaut) |
| 2GB | 2 Go | `-Xms512m -Xmx1536m` |
| 4GB+ | 4+ Go | `-Xms1G -Xmx3G` |

**Modifier la mémoire:**

```bash
sudo nano /etc/systemd/system/law-spring-batch.service
# Modifier ExecStart avec -Xms et -Xmx
sudo systemctl daemon-reload
sudo systemctl restart law-spring-batch
```

---

## 6. Configuration et Monitoring

### 6.1. Configuration application.yml

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/law_batch}
    username: ${SPRING_DATASOURCE_USERNAME:law_user}
    password: ${SPRING_DATASOURCE_PASSWORD:law_password}
  
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: ${SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT:org.hibernate.dialect.MariaDBDialect}
  
  batch:
    job:
      enabled: false  # Pas d'auto-run
    jdbc:
      initialize-schema: always
  
  task:
    scheduling:
      pool:
        size: ${SPRING_TASK_SCHEDULING_POOL_SIZE:1}

law:
  base-url: https://sgg.gouv.bj/doc
  start-year: 1960
  max-number-per-year: 2000
  
  http:
    timeout: 30000
    max-retries: 3
  
  ocr:
    language: fra
    dpi: 300
    quality-threshold: 0.70
  
  batch:
    chunk-size: 10
    max-threads: 4
```

### 6.2. Base de données

#### Tables applicatives

```sql
-- Documents trouvés
CREATE TABLE fetch_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) UNIQUE NOT NULL,
    url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    document_exists BOOLEAN NOT NULL,
    fetched_at DATETIME NOT NULL
);

-- PDFs téléchargés
CREATE TABLE download_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) UNIQUE NOT NULL,
    pdf_content LONGBLOB NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    downloaded_at DATETIME NOT NULL
);

-- Texte OCR
CREATE TABLE ocr_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) UNIQUE NOT NULL,
    ocr_text LONGTEXT NOT NULL,
    text_length INT NOT NULL,
    extracted_at DATETIME NOT NULL
);

-- Articles extraits
CREATE TABLE article_extractions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) NOT NULL,
    article_index INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    confidence DOUBLE NOT NULL,
    document_type VARCHAR(20),
    document_year INT,
    document_number INT,
    signatories JSON,
    extracted_at DATETIME NOT NULL
);
```

### 6.3. Monitoring

#### Vérifier statut

```bash
# Service systemd
sudo systemctl status law-spring-batch

# Logs en temps réel
sudo journalctl -u law-spring-batch -f

# Dernières 100 lignes
sudo journalctl -u law-spring-batch -n 100 --no-pager

# Health check
curl http://localhost:8080/actuator/health

# Stats fichiers
curl http://localhost:8080/api/files/stats
```

#### Métriques système

```bash
# Mémoire et CPU
free -h
ps aux | grep java

# Espace disque
df -h

# Taille données
du -sh /home/pi/law-data/*
```

#### MariaDB/MySQL

```bash
# Connexion
mysql -u law_user -p law_batch

# Tables
mysql -u law_user -p law_batch -e "SHOW TABLES;"

# Compter articles
mysql -u law_user -p law_batch -e "SELECT COUNT(*) FROM article_extractions;"

# Backup
mysqldump -u law_user -p law_batch > backup_$(date +%Y%m%d).sql
```

---

## 7. Troubleshooting

### 7.1. Application ne démarre pas

**Problème:** Service en failed state

```bash
# Voir logs détaillés
sudo journalctl -u law-spring-batch -n 100 --no-pager

# Erreur commune: "Unable to determine Dialect"
# Vérifier la variable d'environnement
sudo grep DIALECT /etc/systemd/system/law-spring-batch.service
# Doit contenir: SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect

# Tester connexion MariaDB
mysql -u law_user -p law_batch -e "SELECT 1;"
```

**Solution:**
1. Vérifier que MariaDB est démarré: `sudo systemctl status mariadb`
2. Vérifier les variables d'environnement dans le service
3. Recharger et redémarrer: `sudo systemctl daemon-reload && sudo systemctl restart law-spring-batch`

### 7.2. OutOfMemoryError

**Problème:** Pas assez de mémoire JVM

```bash
# Vérifier mémoire disponible
free -h

# Voir utilisation Java
ps aux | grep java
```

**Solution:** Réduire heap JVM

```bash
sudo nano /etc/systemd/system/law-spring-batch.service
# Modifier -Xmx (ex: -Xmx800m → -Xmx600m)
sudo systemctl daemon-reload
sudo systemctl restart law-spring-batch
```

### 7.3. Jobs ne s'exécutent pas

**Problème:** Scheduler inactif

```bash
# Vérifier logs
sudo journalctl -u law-spring-batch | grep -i scheduler

# Vérifier configuration
curl http://localhost:8080/actuator/health
```

**Solution:**
1. Vérifier que @EnableScheduling est présent dans LawSpringBatchApplication
2. Vérifier que spring.task.scheduling.pool.size > 0
3. Consulter les logs d'erreur

### 7.4. Erreur "Connection refused"

**Problème:** Port 8080 non accessible

```bash
# Vérifier port ouvert
netstat -tulpn | grep 8080

# Vérifier processus
ps aux | grep java
```

**Solution:**
1. Vérifier que l'application est démarrée
2. Attendre fin du démarrage (~90 secondes sur Raspberry Pi 1GB)
3. Vérifier pas de conflit de port

### 7.5. Déploiement Maven échoue

**Problème:** Erreur SSH ou transfert

```bash
# Tester connexion SSH
ssh pi@192.168.0.37 "echo 'OK'"

# Vérifier permissions clé
chmod 600 ~/.ssh/id_rsa

# Déployer avec debug
mvn deploy -X
```

**Solution:**
1. Vérifier ~/.m2/settings.xml
2. Régénérer clé SSH si nécessaire
3. Vérifier répertoire destination existe: `ssh pi@192.168.0.37 "ls -ld ~/law-spring"`

---

## 8. Structure du Projet

### Répertoires principaux

```
law.spring/
├── pom.xml                          # Configuration Maven
├── README.md                        # Documentation
├── docker-compose.dev.yml           # Docker dev
│
├── docs/                            # Documentation complète
│   ├── GUIDE_COMPLET.md            # Ce fichier
│   ├── API_REFERENCE.md
│   ├── ARCHITECTURE.md
│   └── ...
│
├── scripts/                         # Scripts utilitaires
│   ├── raspi-setup.sh              # Installation Raspberry Pi
│   ├── consolidate.sh
│   ├── download.sh
│   └── ...
│
├── src/
│   ├── main/
│   │   ├── java/bj/gouv/sgg/
│   │   │   ├── LawSpringBatchApplication.java
│   │   │   │
│   │   │   ├── batch/              # Spring Batch
│   │   │   │   ├── config/
│   │   │   │   │   └── BatchJobConfiguration.java
│   │   │   │   ├── reader/
│   │   │   │   │   ├── LawDocumentReader.java
│   │   │   │   │   ├── CurrentYearLawDocumentReader.java
│   │   │   │   │   └── PreviousYearsLawDocumentReader.java
│   │   │   │   ├── processor/
│   │   │   │   │   ├── FetchProcessor.java
│   │   │   │   │   ├── DownloadProcessor.java
│   │   │   │   │   └── ExtractionProcessor.java
│   │   │   │   ├── writer/
│   │   │   │   │   └── FetchWriter.java
│   │   │   │   └── scheduler/
│   │   │   │       └── BatchJobScheduler.java
│   │   │   │
│   │   │   ├── config/             # Configuration
│   │   │   │   ├── LawProperties.java
│   │   │   │   └── BatchConfiguration.java
│   │   │   │
│   │   │   ├── controller/         # API REST
│   │   │   │   ├── BatchController.java
│   │   │   │   ├── ArticleController.java
│   │   │   │   └── FileResourceController.java
│   │   │   │
│   │   │   ├── model/              # Entités
│   │   │   │   ├── LawDocument.java
│   │   │   │   ├── FetchResult.java
│   │   │   │   ├── DownloadResult.java
│   │   │   │   ├── OcrResult.java
│   │   │   │   └── ArticleExtraction.java
│   │   │   │
│   │   │   ├── repository/         # JPA Repositories
│   │   │   │   ├── FetchResultRepository.java
│   │   │   │   ├── DownloadResultRepository.java
│   │   │   │   └── ArticleExtractionRepository.java
│   │   │   │
│   │   │   └── service/            # Services métier
│   │   │       ├── TesseractOcrService.java
│   │   │       ├── ArticleExtractorService.java
│   │   │       ├── FileStorageService.java
│   │   │       └── NotFoundRangeService.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── patterns.properties
│   │       └── tessdata/
│   │
│   └── test/
│
├── data/                            # Données locales (dev)
│   ├── pdfs/
│   ├── ocr/
│   └── articles/
│
└── target/                          # Build artifacts
    └── law-spring-batch-1.0.0-SNAPSHOT.jar
```

### Composants clés

#### BatchJobScheduler.java

Scheduler principal avec 6 jobs automatiques :

```java
@Component
public class BatchJobScheduler {
    
    @Scheduled(cron = "0 0 6,12,18 * * *")  // 6h, 12h, 18h
    public void runFetchCurrentJob() { }
    
    @Scheduled(cron = "0 30 * * * *")  // Horaire :30
    public void runFetchPreviousJob() { }
    
    @Scheduled(cron = "0 0 */2 * * *")  // 2h (paires) :00
    public void runDownloadJob() { }
    
    @Scheduled(cron = "0 30 */2 * * *")  // 2h (paires) :30
    public void runOcrJob() { }
    
    @Scheduled(cron = "0 0 1-23/2 * * *")  // 2h (impaires) :00
    public void runExtractJob() { }
    
    @Scheduled(cron = "0 30 1-23/2 * * *")  // 2h (impaires) :30
    public void runConsolidateJob() { }
}
```

#### BatchJobConfiguration.java

Configuration des jobs Spring Batch avec readers, processors et writers.

#### Service systemd

Gestion du cycle de vie de l'application sur Raspberry Pi.

---

## 📊 Statistiques de Déploiement

**État actuel (24 novembre 2025):**

- ✅ **Application:** Opérationnelle sur Raspberry Pi 192.168.0.37
- ✅ **Base de données:** law_batch (MariaDB)
- ✅ **Service:** law-spring-batch.service (enabled, active)
- ✅ **Scheduler:** 6 jobs configurés et actifs
- ✅ **Mémoire:** 256MB-800MB heap (optimisé pour 1GB RAM)
- ✅ **Démarrage:** ~90 secondes
- ✅ **API:** http://192.168.0.37:8080 (accessible)

**Jobs testés:**
- ✅ fetch-current: 2484 documents traités en 18min13s
- ✅ download: 2 PDFs téléchargés (68.7MB) en 2min
- ✅ ocr, extract, consolidate: Fonctionnels

---

## 🎯 Checklist de Mise en Production

### Sécurité
- [ ] Changer mot de passe MariaDB
- [ ] Configurer firewall (UFW)
- [ ] Désactiver connexion SSH par mot de passe
- [ ] Configurer HTTPS (Nginx + Let's Encrypt)

### Performance
- [ ] Ajuster JVM heap selon RAM disponible
- [ ] Configurer swap (si < 2GB RAM)
- [ ] Optimiser chunk-size selon charge
- [ ] Monitorer logs disque

### Backup
- [ ] Configurer backup automatique MySQL
- [ ] Sauvegarder données (/home/pi/law-data)
- [ ] Documenter procédure de restore

### Monitoring
- [ ] Configurer alertes système
- [ ] Monitorer espace disque
- [ ] Surveiller température Raspberry Pi
- [ ] Logger métriques jobs

---

## 📚 Ressources

- **Spring Batch:** https://docs.spring.io/spring-batch/
- **Spring Boot:** https://docs.spring.io/spring-boot/
- **Tesseract OCR:** https://github.com/tesseract-ocr/tesseract
- **Maven:** https://maven.apache.org/

---

**Version:** 1.0.0  
**Dernière mise à jour:** 24 novembre 2025  
**Statut:** ✅ Production Ready sur Raspberry Pi
