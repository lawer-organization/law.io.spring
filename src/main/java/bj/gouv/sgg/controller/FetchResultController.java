package bj.gouv.sgg.controller;

import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.repository.FetchResultRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller pour consulter les résultats de fetch
 */
@Slf4j
@RestController
@RequestMapping("/api/fetch-results")
@RequiredArgsConstructor
@Tag(name = "Fetch Results", description = "API pour consulter les résultats de fetch des documents")
public class FetchResultController {
    
    private final FetchResultRepository fetchResultRepository;
    
    /**
     * Récupère les URLs trouvées pour une année donnée avec filtrage optionnel par type
     */
    @Operation(summary = "Récupère les URLs trouvées pour une année", 
               description = "Retourne la liste des documents trouvés (exists=true) pour l'année spécifiée. Peut être filtré par type (loi/decret)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liste des URLs trouvées")
    })
    @GetMapping("/{year}")
    public ResponseEntity<Map<String, Object>> getFetchResultsByYear(
        @Parameter(description = "Année à consulter (ex: 2025)", required = true)
        @PathVariable int year,
        @Parameter(description = "Type de document: 'loi', 'decret', ou vide pour tous", required = false)
        @RequestParam(required = false) String type) {
        
        // Récupérer tous les résultats pour l'année (uniquement les documents trouvés)
        List<FetchResult> results;
        if (type != null && !type.isEmpty()) {
            String normalizedType = type.toLowerCase().trim();
            results = fetchResultRepository.findByYear(year).stream()
                .filter(f -> normalizedType.equals(f.getDocumentType()))
                .sorted((a, b) -> Integer.compare(a.getNumber(), b.getNumber()))
                .toList();
        } else {
            results = fetchResultRepository.findByYear(year).stream()
                .sorted((a, b) -> {
                    int typeCompare = a.getDocumentType().compareTo(b.getDocumentType());
                    if (typeCompare != 0) return typeCompare;
                    return Integer.compare(a.getNumber(), b.getNumber());
                })
                .toList();
        }
        
        // Compter par type
        long loiCount = results.stream().filter(f -> "loi".equals(f.getDocumentType())).count();
        long decretCount = results.stream().filter(f -> "decret".equals(f.getDocumentType())).count();
        
        Map<String, Object> response = new HashMap<>();
        response.put("year", year);
        response.put("type", type != null ? type : "all");
        response.put("totalFound", results.size());
        response.put("loiCount", loiCount);
        response.put("decretCount", decretCount);
        response.put("results", results.stream()
            .map(f -> Map.of(
                "documentId", f.getDocumentId(),
                "url", f.getUrl(),
                "fetchedAt", f.getFetchedAt()
            ))
            .toList());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Récupère les statistiques globales des fetch results
     */
    @Operation(summary = "Récupère les statistiques des fetch results", 
               description = "Retourne le nombre total de documents trouvés et non trouvés")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistiques récupérées")
    })
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalFound = fetchResultRepository.count();
        // Les NOT_FOUND sont maintenant dans fetch_not_found_ranges, pas dans fetch_results
        // Pour obtenir le total de NOT_FOUND, il faudrait interroger fetch_not_found_ranges
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFound", totalFound);
        stats.put("loiCount", fetchResultRepository.countByDocumentType("loi"));
        stats.put("decretCount", fetchResultRepository.countByDocumentType("decret"));
        
        return ResponseEntity.ok(stats);
    }
}
