package bj.gouv.sgg.controller;

import bj.gouv.sgg.service.DocumentProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour le traitement intelligent de documents
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Processing", description = "API pour le traitement intelligent et idempotent de documents")
public class DocumentProcessingController {

    private final DocumentProcessingService processingService;

    @PostMapping("/process/{documentId}")
    @Operation(summary = "Traiter un document spécifique", 
               description = "Traite un document de manière intelligente et idempotente. " +
                           "Si le document est déjà traité, il est ignoré (sauf avec force=true). " +
                           "Le processus inclut : fetch URL, téléchargement PDF, OCR et extraction des articles.")
    public ResponseEntity<DocumentProcessingService.ProcessingResult> processDocument(
            @Parameter(description = "ID du document (ex: loi-2025-8, decret-2024-15)", required = true)
            @PathVariable String documentId,
            
            @Parameter(description = "Forcer le retraitement même si déjà traité", required = false)
            @RequestParam(defaultValue = "false") boolean force) {
        
        log.info("Received process request for document: {} (force={})", documentId, force);
        
        DocumentProcessingService.ProcessingResult result = processingService.processDocument(documentId, force);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/process")
    @Operation(summary = "Traiter un document avec paramètres détaillés",
               description = "Alternative pour spécifier le type, l'année et le numéro séparément")
    public ResponseEntity<DocumentProcessingService.ProcessingResult> processDocumentDetailed(
            @Parameter(description = "Type de document (loi ou decret)", required = true)
            @RequestParam String type,
            
            @Parameter(description = "Année du document", required = true)
            @RequestParam int year,
            
            @Parameter(description = "Numéro du document", required = true)
            @RequestParam int number,
            
            @Parameter(description = "Forcer le retraitement même si déjà traité", required = false)
            @RequestParam(defaultValue = "false") boolean force) {
        
        String documentId = String.format("%s-%d-%d", type, year, number);
        log.info("Received detailed process request for: {} (force={})", documentId, force);
        
        DocumentProcessingService.ProcessingResult result = processingService.processDocument(documentId, force);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
