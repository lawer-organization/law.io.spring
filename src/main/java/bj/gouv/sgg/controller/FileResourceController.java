package bj.gouv.sgg.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contrôleur REST pour accéder aux fichiers sur le disque (PDFs, OCR, Articles JSON).
 * Endpoints sécurisés par Spring Security (HTTP Basic Auth).
 */
@RestController
@RequestMapping("/api/files")
public class FileResourceController {

    private static final Logger logger = LoggerFactory.getLogger(FileResourceController.class);

    @Value("${law.directories.data:data}")
    private String dataDirectory;

    /**
     * Liste tous les fichiers PDF disponibles.
     * GET /api/files/pdfs
     */
    @GetMapping("/pdfs")
    public ResponseEntity<Map<String, Object>> listPdfs() {
        return listFiles("pdfs/loi", ".pdf");
    }

    /**
     * Liste tous les fichiers OCR (texte) disponibles.
     * GET /api/files/ocr
     */
    @GetMapping("/ocr")
    public ResponseEntity<Map<String, Object>> listOcrFiles() {
        return listFiles("ocr/loi", ".txt");
    }

    /**
     * Liste tous les fichiers JSON d'articles disponibles.
     * GET /api/files/articles
     */
    @GetMapping("/articles")
    public ResponseEntity<Map<String, Object>> listArticleFiles() {
        return listFiles("articles/loi", ".json");
    }

    /**
     * Télécharge un fichier PDF.
     * GET /api/files/pdfs/{filename}
     * Exemple: /api/files/pdfs/loi-2025-11.pdf
     */
    @GetMapping("/pdfs/{filename:.+}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String filename) {
        return downloadFile("pdfs/loi", filename, MediaType.APPLICATION_PDF);
    }

    /**
     * Télécharge un fichier OCR (texte).
     * GET /api/files/ocr/{filename}
     * Exemple: /api/files/ocr/loi-2025-11.txt
     */
    @GetMapping("/ocr/{filename:.+}")
    public ResponseEntity<Resource> downloadOcrFile(@PathVariable String filename) {
        return downloadFile("ocr/loi", filename, MediaType.TEXT_PLAIN);
    }

    /**
     * Télécharge un fichier JSON d'articles.
     * GET /api/files/articles/{filename}
     * Exemple: /api/files/articles/loi-2025-11.json
     */
    @GetMapping("/articles/{filename:.+}")
    public ResponseEntity<Resource> downloadArticleFile(@PathVariable String filename) {
        return downloadFile("articles/loi", filename, MediaType.APPLICATION_JSON);
    }

    /**
     * Lit le contenu d'un fichier OCR (texte brut).
     * GET /api/files/ocr/{filename}/content
     * Retourne le texte directement (pas de téléchargement).
     */
    @GetMapping("/ocr/{filename:.+}/content")
    public ResponseEntity<Map<String, Object>> readOcrContent(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(dataDirectory, "ocr/loi", filename);
            
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "error", "File not found",
                                "filename", filename
                        ));
            }

            String content = Files.readString(filePath);
            long fileSize = Files.size(filePath);

            Map<String, Object> response = new HashMap<>();
            response.put("filename", filename);
            response.put("content", content);
            response.put("size", fileSize);
            response.put("lines", content.split("\n").length);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error reading OCR file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Error reading file",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Lit le contenu d'un fichier JSON d'articles.
     * GET /api/files/articles/{filename}/content
     * Retourne le JSON parsé.
     */
    @GetMapping("/articles/{filename:.+}/content")
    public ResponseEntity<Object> readArticleContent(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(dataDirectory, "articles/loi", filename);
            
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "error", "File not found",
                                "filename", filename
                        ));
            }

            String content = Files.readString(filePath);
            
            // Retourner le JSON brut (sera parsé par le client)
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);

        } catch (IOException e) {
            logger.error("Error reading article file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Error reading file",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Statistiques globales des fichiers.
     * GET /api/files/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Compter les PDFs
        long pdfCount = countFiles("pdfs/loi", ".pdf");
        long pdfSize = getTotalSize("pdfs/loi", ".pdf");

        // Compter les OCR
        long ocrCount = countFiles("ocr/loi", ".txt");
        long ocrSize = getTotalSize("ocr/loi", ".txt");

        // Compter les articles
        long articleCount = countFiles("articles/loi", ".json");
        long articleSize = getTotalSize("articles/loi", ".json");

        stats.put("pdfs", Map.of(
                "count", pdfCount,
                "totalSize", pdfSize,
                "totalSizeMB", String.format("%.2f MB", pdfSize / (1024.0 * 1024.0))
        ));

        stats.put("ocr", Map.of(
                "count", ocrCount,
                "totalSize", ocrSize,
                "totalSizeMB", String.format("%.2f MB", ocrSize / (1024.0 * 1024.0))
        ));

        stats.put("articles", Map.of(
                "count", articleCount,
                "totalSize", articleSize,
                "totalSizeMB", String.format("%.2f MB", articleSize / (1024.0 * 1024.0))
        ));

        stats.put("dataDirectory", dataDirectory);

        return ResponseEntity.ok(stats);
    }

    // ========== Méthodes privées ==========

    /**
     * Liste les fichiers dans un sous-répertoire avec une extension donnée.
     */
    private ResponseEntity<Map<String, Object>> listFiles(String subDirectory, String extension) {
        try {
            Path directory = Paths.get(dataDirectory, subDirectory);

            if (!Files.exists(directory)) {
                return ResponseEntity.ok(Map.of(
                        "files", Collections.emptyList(),
                        "count", 0,
                        "directory", directory.toString(),
                        "exists", false
                ));
            }

            try (Stream<Path> paths = Files.list(directory)) {
                List<Map<String, Object>> files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(extension))
                        .sorted()
                        .map(this::fileToMap)
                        .collect(Collectors.toList());

                Map<String, Object> response = new HashMap<>();
                response.put("files", files);
                response.put("count", files.size());
                response.put("directory", directory.toString());
                response.put("exists", true);

                return ResponseEntity.ok(response);
            }

        } catch (IOException e) {
            logger.error("Error listing files in {}", subDirectory, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Error listing files",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Télécharge un fichier.
     */
    private ResponseEntity<Resource> downloadFile(String subDirectory, String filename, MediaType mediaType) {
        try {
            // Validation du nom de fichier (sécurité)
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                logger.warn("Invalid filename attempted: {}", filename);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            Path filePath = Paths.get(dataDirectory, subDirectory, filename);

            if (!Files.exists(filePath)) {
                logger.warn("File not found: {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error downloading file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convertit un Path en Map avec infos du fichier.
     */
    private Map<String, Object> fileToMap(Path path) {
        Map<String, Object> fileInfo = new HashMap<>();
        try {
            fileInfo.put("filename", path.getFileName().toString());
            fileInfo.put("size", Files.size(path));
            fileInfo.put("sizeMB", String.format("%.2f MB", Files.size(path) / (1024.0 * 1024.0)));
            fileInfo.put("lastModified", Files.getLastModifiedTime(path).toString());
        } catch (IOException e) {
            logger.error("Error getting file info: {}", path, e);
            fileInfo.put("error", e.getMessage());
        }
        return fileInfo;
    }

    /**
     * Compte le nombre de fichiers dans un répertoire.
     */
    private long countFiles(String subDirectory, String extension) {
        try {
            Path directory = Paths.get(dataDirectory, subDirectory);
            if (!Files.exists(directory)) {
                return 0;
            }

            try (Stream<Path> paths = Files.list(directory)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(extension))
                        .count();
            }
        } catch (IOException e) {
            logger.error("Error counting files in {}", subDirectory, e);
            return 0;
        }
    }

    /**
     * Calcule la taille totale des fichiers dans un répertoire.
     */
    private long getTotalSize(String subDirectory, String extension) {
        try {
            Path directory = Paths.get(dataDirectory, subDirectory);
            if (!Files.exists(directory)) {
                return 0;
            }

            try (Stream<Path> paths = Files.list(directory)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(extension))
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            logger.error("Error calculating total size in {}", subDirectory, e);
            return 0;
        }
    }
}
