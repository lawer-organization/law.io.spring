package bj.gouv.sgg.controller;

import bj.gouv.sgg.service.ConsolidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller pour gérer les articles extraits
 */
@Slf4j
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Tag(name = "Articles", description = "API pour gérer les articles extraits des documents")
public class ArticleController {
    
    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    
    private final ConsolidationService consolidationService;
    
    /**
     * Export all articles to JSON
     */
    @Operation(summary = "Exporte les articles en JSON", 
               description = "Récupère tous les articles extraits depuis la base de données au format JSON")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Export réussi"),
        @ApiResponse(responseCode = "500", description = "Erreur lors de l'export")
    })
    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportArticles() {
        try {
            log.info("Exporting articles to JSON");
            String json = consolidationService.exportToJson();
            log.info("Successfully exported articles");
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
        } catch (Exception e) {
            log.error("Error exporting articles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get article statistics
     */
    @Operation(summary = "Obtient les statistiques des articles", 
               description = "Retourne le nombre total d'articles extraits et consolidés")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistiques récupérées avec succès"),
        @ApiResponse(responseCode = "500", description = "Erreur lors de la récupération")
    })
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getArticleStats() {
        try {
            long count = consolidationService.countArticles();
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalArticles", count);
            stats.put(MESSAGE_KEY, "Article stats retrieved successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting article stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
