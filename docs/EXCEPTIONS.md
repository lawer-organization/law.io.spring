# 🚨 Système d'Exceptions Personnalisées

## Vue d'ensemble

L'application utilise un système d'exceptions hiérarchiques pour gérer les erreurs métier de manière cohérente et maintenable.

## Hiérarchie des Exceptions

```
LawProcessingException (base)
├── DocumentNotFoundException
├── InvalidDocumentIdException
├── PdfDownloadException
│   └── EmptyPdfException
├── OcrProcessingException
│   ├── InsufficientTextException
│   └── TesseractInitializationException
├── ArticleExtractionException
├── FileStorageException
└── BatchProcessingException
```

## Exceptions Disponibles

### 1. `LawProcessingException`
**Type**: Exception de base  
**Usage**: Classe parente pour toutes les exceptions métier  
**Champs**:
- `documentId`: Identifiant du document (nullable)
- `errorCode`: Code d'erreur pour l'API (nullable)

**Exemple**:
```java
throw new LawProcessingException("loi-2020-32", "PROCESSING_ERROR", "Error message");
```

---

### 2. `DocumentNotFoundException`
**Type**: Erreur 404  
**Usage**: Document introuvable sur le serveur  
**Code HTTP**: 404 NOT_FOUND

**Exemple**:
```java
throw new DocumentNotFoundException("loi-2020-32");
throw new DocumentNotFoundException("loi-2020-32", "https://sgg.gouv.bj/doc/loi-2020-32");
```

---

### 3. `InvalidDocumentIdException`
**Type**: Erreur de validation  
**Usage**: Format de documentId invalide  
**Code HTTP**: 400 BAD_REQUEST  
**Format attendu**: `{type}-{year}-{number}` (ex: `loi-2020-32`)

**Exemple**:
```java
throw new InvalidDocumentIdException("invalid-format");
throw new InvalidDocumentIdException("loi-20-32", "Year must be 4 digits");
```

---

### 4. `PdfDownloadException`
**Type**: Erreur de téléchargement  
**Usage**: Échec du téléchargement du PDF  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR

**Exemple**:
```java
throw new PdfDownloadException("loi-2020-32", "Connection timeout");
throw new PdfDownloadException("loi-2020-32", "Download failed", ioException);
```

---

### 5. `EmptyPdfException`
**Type**: Erreur de validation (sous-classe de PdfDownloadException)  
**Usage**: PDF téléchargé est vide (0 bytes)  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR

**Exemple**:
```java
throw new EmptyPdfException("loi-2020-32");
throw new EmptyPdfException("loi-2020-32", "https://sgg.gouv.bj/doc/loi-2020-32/download");
```

---

### 6. `OcrProcessingException`
**Type**: Erreur de traitement OCR  
**Usage**: Échec du traitement OCR  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR

**Exemple**:
```java
throw new OcrProcessingException("loi-2020-32", "OCR extraction failed");
throw new OcrProcessingException("OCR service unavailable", exception);
```

---

### 7. `InsufficientTextException`
**Type**: Erreur de qualité (sous-classe de OcrProcessingException)  
**Usage**: Texte extrait insuffisant  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR  
**Champs**:
- `extractedLength`: Nombre de caractères extraits
- `minimumRequired`: Minimum requis (généralement 1000)

**Exemple**:
```java
throw new InsufficientTextException("loi-2020-32", 450, 1000);
```

---

### 8. `TesseractInitializationException`
**Type**: Erreur d'initialisation (sous-classe de OcrProcessingException)  
**Usage**: Échec d'initialisation de Tesseract  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR  
**Champs**:
- `tessdataPath`: Chemin du répertoire tessdata
- `attempts`: Nombre de tentatives effectuées

**Exemple**:
```java
throw new TesseractInitializationException("/tmp/tessdata", 3);
throw new TesseractInitializationException("/tmp/tessdata", "Language not found", exception);
```

---

### 9. `ArticleExtractionException`
**Type**: Erreur d'extraction  
**Usage**: Échec de l'extraction des articles  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR

**Exemple**:
```java
throw new ArticleExtractionException("loi-2020-32", "No articles found");
throw new ArticleExtractionException("loi-2020-32", "Regex pattern failed", exception);
```

---

### 10. `FileStorageException`
**Type**: Erreur de système de fichiers  
**Usage**: Échec des opérations sur les fichiers  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR  
**Champs**:
- `filePath`: Chemin du fichier concerné
- `operationType`: Type d'opération (READ, WRITE, DELETE, CREATE)

**Exemple**:
```java
throw new FileStorageException("/data/pdfs/loi/loi-2020-32.pdf", 
                               OperationType.WRITE, 
                               "Disk full");

throw new FileStorageException("/data/ocr/loi/loi-2020-32.txt", 
                               OperationType.READ, 
                               "File not found", 
                               ioException);
```

---

### 11. `BatchProcessingException`
**Type**: Erreur de traitement par lot  
**Usage**: Échec d'un job Spring Batch  
**Code HTTP**: 500 INTERNAL_SERVER_ERROR  
**Champs**:
- `batchJobName`: Nom du job batch
- `itemsFailed`: Nombre d'items en échec
- `itemsProcessed`: Nombre d'items traités

**Exemple**:
```java
throw new BatchProcessingException("extractionJob", "Job timeout");
throw new BatchProcessingException("consolidationJob", 5, 95, "Partial failure");
```

---

## Gestionnaire Global (`GlobalExceptionHandler`)

Le `@RestControllerAdvice` capture automatiquement toutes les exceptions et retourne des réponses JSON standardisées.

### Réponse JSON Standard

```json
{
  "timestamp": "2024-01-15T14:30:00",
  "status": 404,
  "error": "DocumentNotFoundException",
  "message": "Document loi-2020-32 not found on server (HTTP 404)",
  "path": "/api/documents/process/loi-2020-32",
  "errorCode": "DOCUMENT_NOT_FOUND",
  "documentId": "loi-2020-32"
}
```

### Codes HTTP Retournés

| Exception | Code HTTP | Statut |
|-----------|-----------|--------|
| `DocumentNotFoundException` | 404 | NOT_FOUND |
| `InvalidDocumentIdException` | 400 | BAD_REQUEST |
| Toutes les autres | 500 | INTERNAL_SERVER_ERROR |

---

## Utilisation dans les Services

### Exemple 1: Validation de documentId

```java
@Service
public class DocumentProcessingService {
    
    public void processDocument(String documentId) {
        ParsedDocument parsed = DocumentIdParser.parse(documentId);
        
        if (parsed == null) {
            throw new InvalidDocumentIdException(documentId);
        }
        
        // Traitement...
    }
}
```

### Exemple 2: Téléchargement PDF

```java
@Service
public class PdfDownloadService {
    
    public File downloadPdf(LawDocument document) {
        try {
            byte[] pdfBytes = restTemplate.getForObject(url, byte[].class);
            
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new EmptyPdfException(document.getDocumentId(), url);
            }
            
            // Sauvegarder...
            return pdfFile;
            
        } catch (EmptyPdfException e) {
            throw e; // Propager telle quelle
        } catch (Exception e) {
            throw new PdfDownloadException(document.getDocumentId(), url, e);
        }
    }
}
```

### Exemple 3: Traitement OCR

```java
@Service
public class TesseractOcrService {
    
    public String extractText(byte[] pdfBytes) {
        String text = performOcr(pdfBytes);
        
        if (text == null || text.length() < 1000) {
            throw new InsufficientTextException(documentId, text.length(), 1000);
        }
        
        return text;
    }
}
```

### Exemple 4: Opérations fichiers

```java
@Service
public class FileStorageService {
    
    public String readOcr(String type, String documentId) {
        Path path = getOcrPath(type, documentId);
        
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new FileStorageException(
                path.toString(), 
                OperationType.READ, 
                "Failed to read OCR file", 
                e
            );
        }
    }
}
```

---

## Logging Automatique

Le `GlobalExceptionHandler` log automatiquement les exceptions :

- **WARN**: Erreurs client (400, 404) → `DocumentNotFoundException`, `InvalidDocumentIdException`
- **ERROR**: Erreurs serveur (500) → Toutes les autres exceptions

**Exemple de logs**:
```
2024-01-15 14:30:00 WARN  GlobalExceptionHandler - Document not found: Document loi-2020-32 not found on server (HTTP 404)
2024-01-15 14:35:12 ERROR GlobalExceptionHandler - PDF download error: Failed to download PDF for document loi-2024-15 from URL: https://sgg.gouv.bj/doc/loi-2024-15/download
```

---

## Bonnes Pratiques

### ✅ À FAIRE

1. **Utiliser l'exception la plus spécifique**
   ```java
   throw new EmptyPdfException(documentId, url); // ✅ Spécifique
   // Plutôt que:
   throw new PdfDownloadException(documentId, "PDF is empty"); // ❌ Trop générique
   ```

2. **Inclure toujours le documentId quand disponible**
   ```java
   throw new OcrProcessingException(documentId, "OCR failed"); // ✅
   ```

3. **Préserver l'exception d'origine**
   ```java
   } catch (IOException e) {
       throw new FileStorageException(path, OperationType.READ, "Read failed", e); // ✅
   }
   ```

4. **Fournir des messages descriptifs**
   ```java
   throw new ArticleExtractionException(
       documentId, 
       "No articles found: regex patterns returned 0 matches"
   ); // ✅
   ```

### ❌ À ÉVITER

1. **Ne pas utiliser Exception générique**
   ```java
   throw new Exception("Something failed"); // ❌
   throw new RuntimeException("Error"); // ❌
   ```

2. **Ne pas avaler les exceptions**
   ```java
   try {
       // ...
   } catch (Exception e) {
       // ❌ Exception ignorée
   }
   ```

3. **Ne pas créer d'exception sans context**
   ```java
   throw new LawProcessingException("Error"); // ❌ Pas de documentId ni errorCode
   ```

---

## Tests

### Tester les exceptions dans les controllers

```java
@Test
void shouldReturn404WhenDocumentNotFound() {
    mockMvc.perform(get("/api/documents/process/loi-9999-99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"))
           .andExpect(jsonPath("$.documentId").value("loi-9999-99"));
}
```

### Tester les exceptions dans les services

```java
@Test
void shouldThrowInvalidDocumentIdException() {
    assertThrows(InvalidDocumentIdException.class, () -> {
        service.processDocument("invalid-format");
    });
}
```

---

## Migration du Code Existant

Pour migrer du code utilisant des exceptions génériques:

### Avant
```java
throw new Exception("Downloaded PDF is empty");
throw new IOException("Failed to initialize Tesseract");
```

### Après
```java
throw new EmptyPdfException(documentId, url);
throw new TesseractInitializationException(tessdataPath, maxRetries);
```

---

## Ajout de Nouvelles Exceptions

1. Créer la classe dans `bj.gouv.sgg.exception`
2. Hériter de `LawProcessingException` ou d'une sous-classe
3. Fournir des constructeurs appropriés
4. Ajouter un handler dans `GlobalExceptionHandler` si besoin d'un traitement spécial
5. Documenter dans ce README

**Template**:
```java
package bj.gouv.sgg.exception;

public class MyCustomException extends LawProcessingException {
    
    public MyCustomException(String documentId, String message) {
        super(documentId, "MY_ERROR_CODE", message);
    }
    
    public MyCustomException(String documentId, String message, Throwable cause) {
        super(documentId, "MY_ERROR_CODE", message, cause);
    }
}
```

---

## Ressources

- Code source: `src/main/java/bj/gouv/sgg/exception/`
- Handler global: `GlobalExceptionHandler.java`
- Modèle de réponse: `ErrorResponse.java`
