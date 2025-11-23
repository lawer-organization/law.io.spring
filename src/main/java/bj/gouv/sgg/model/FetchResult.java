package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant les résultats de fetch stockés en base de données
 */
@Entity
@Table(name = "fetch_results", indexes = {
    @Index(name = "idx_document_id", columnList = "documentId"),
    @Index(name = "idx_document_type_year", columnList = "documentType,year"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_fetched_at", columnList = "fetchedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String documentId;
    
    @Column(nullable = false, length = 20)
    private String documentType; // "loi" ou "decret"
    
    @Column(nullable = false)
    private Integer year;
    
    @Column(nullable = false)
    private Integer number;
    
    @Column(nullable = false, length = 500)
    private String url;
    
    @Column(nullable = false, length = 50)
    private String status; // "FOUND", "FETCHED"
    
    @Column(nullable = false)
    private LocalDateTime fetchedAt;
    
    @Column(length = 1000)
    private String errorMessage;
}
