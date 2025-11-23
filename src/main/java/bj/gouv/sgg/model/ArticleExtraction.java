package bj.gouv.sgg.model;

import bj.gouv.sgg.model.converter.SignatoryListConverter;
import com.google.gson.annotations.Expose;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entité représentant un article extrait d'un document
 */
@Entity
@Table(name = "article_extractions", indexes = {
    @Index(name = "idx_article_document_id", columnList = "documentId"),
    @Index(name = "idx_article_index", columnList = "articleIndex"),
    @Index(name = "idx_article_extracted_at", columnList = "extractedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleExtraction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Expose(serialize = false)
    private Long id;
    
    @Expose
    @Column(nullable = false, length = 50)
    private String documentId;
    
    @Expose
    @Column(nullable = false)
    private Integer articleIndex;
    
    @Expose
    @Column(nullable = false, length = 200)
    private String title;
    
    @Expose
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Expose
    @Column(nullable = false)
    private Double confidence;
    
    // Metadata fields
    @Expose
    @Column(length = 20)
    private String documentType;
    
    @Expose
    @Column
    private Integer documentYear;
    
    @Expose
    @Column
    private Integer documentNumber;
    
    @Expose
    @Column(length = 500)
    private String sourceUrl;
    
    @Expose(serialize = false)
    @Column(length = 500)
    private String lawTitle;
    
    @Expose
    @Column(length = 50)
    private String promulgationDate;
    
    @Expose(serialize = false)
    @Column(length = 100)
    private String promulgationCity;
    
    @Expose
    @Column(columnDefinition = "JSON")
    @Convert(converter = SignatoryListConverter.class)
    private List<Signatory> signatories;
    
    @Expose
    @Column(nullable = false)
    private LocalDateTime extractedAt;
}
