package bj.gouv.sgg.service;

import bj.gouv.sgg.model.ArticleExtraction;
import bj.gouv.sgg.repository.ArticleExtractionRepository;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service de consolidation des articles depuis la base de données
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsolidationService {
    
    private final ArticleExtractionRepository articleRepository;
    private final Gson gson;
    
    public int consolidateAll() {
        log.info("Starting consolidation from database");
        
        // Charger tous les articles depuis la base de données
        List<ArticleExtraction> articles = articleRepository.findAll();
        log.info("Loaded {} articles from database", articles.size());
        
        // Trier par année, type, numéro, index
        articles.sort(Comparator
            .comparingInt(ArticleExtraction::getDocumentYear)
            .thenComparing(ArticleExtraction::getDocumentType)
            .thenComparingInt(ArticleExtraction::getDocumentNumber)
            .thenComparingInt(ArticleExtraction::getArticleIndex));
        
        log.info("Consolidation completed: {} articles", articles.size());
        return articles.size();
    }
    
    /**
     * Count total articles in database
     */
    public long countArticles() {
        long count = articleRepository.count();
        log.debug("Total articles in database: {}", count);
        return count;
    }
    
    /**
     * Export articles to JSON format
     */
    public String exportToJson() {
        List<ArticleExtraction> articles = articleRepository.findAll();
        
        // Convertir en format Map pour compatibilité
        List<Map<String, Object>> jsonArticles = new ArrayList<>();
        for (ArticleExtraction article : articles) {
            Map<String, Object> articleMap = new HashMap<>();
            articleMap.put("title", article.getTitle());
            articleMap.put("content", article.getContent());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", article.getDocumentType());
            metadata.put("year", article.getDocumentYear());
            metadata.put("number", article.getDocumentNumber());
            metadata.put("article", article.getArticleIndex());
            metadata.put("confidence", article.getConfidence());
            metadata.put("sourceUrl", article.getSourceUrl());
            metadata.put("lawTitle", article.getLawTitle());
            metadata.put("promulgationDate", article.getPromulgationDate());
            metadata.put("promulgationCity", article.getPromulgationCity());
            if (article.getSignatories() != null) {
                metadata.put("signatories", article.getSignatories());
            }
            
            articleMap.put("metadata", metadata);
            jsonArticles.add(articleMap);
        }
        
        return gson.toJson(jsonArticles);
    }
}
