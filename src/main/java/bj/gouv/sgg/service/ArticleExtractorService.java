package bj.gouv.sgg.service;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.Signatory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service d'extraction d'articles via regex
 * Utilise ArticleExtractorConfig pour charger les patterns depuis patterns.properties
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleExtractorService {
    
    private final ArticleExtractorConfig config;
    
    public List<Article> extractArticles(String text) {
        List<Article> articles = new ArrayList<>();
        
        try {
            String[] lines = text.split("\n");
            StringBuilder currentArticle = new StringBuilder();
            int index = 0;
            boolean inArticle = false;
            
            for (String line : lines) {
                boolean isStart = config.getArticleStart().matcher(line).find();
                boolean isEnd = config.getArticleEndAny().matcher(line).find();
                
                // Si on détecte un début/fin ET qu'on est déjà dans un article, on sauve l'article précédent
                if ((isStart || isEnd) && inArticle && !currentArticle.isEmpty()) {
                    String content = currentArticle.toString().trim();
                    if (content.length() > 10) {
                        articles.add(Article.builder()
                            .index(index)
                            .content(content)
                            .build());
                        log.debug("Article {} extracted: {} chars", index, content.length());
                    }
                    currentArticle.setLength(0);
                    inArticle = false;
                }
                
                // Début d'un nouvel article
                if (isStart) {
                    inArticle = true;
                    index++;
                }
                
                // Accumuler les lignes de l'article courant
                if (inArticle) {
                    currentArticle.append(line).append("\n");
                }
            }
            
            // Dernier article
            if (inArticle && !currentArticle.isEmpty()) {
                String content = currentArticle.toString().trim();
                if (content.length() > 10) {
                    articles.add(Article.builder()
                        .index(index)
                        .content(content)
                        .build());
                    log.debug("Article {} (last) extracted: {} chars", index, content.length());
                }
            }
            
            log.info("Total articles extracted: {}", articles.size());
            
        } catch (Exception e) {
            log.error("Error extracting articles: {}", e.getMessage());
        }
        
        return articles;
    }
    
    public DocumentMetadata extractMetadata(String text) {
        DocumentMetadata metadata = DocumentMetadata.builder().build();
        
        // Extract law title using config patterns
        Matcher titleStartMatcher = config.getLawTitleStart().matcher(text);
        if (titleStartMatcher.find()) {
            int start = titleStartMatcher.start();
            Matcher titleEndMatcher = config.getLawTitleEnd().matcher(text.substring(start));
            if (titleEndMatcher.find()) {
                String title = text.substring(start, start + titleEndMatcher.start()).trim();
                metadata.setLawTitle(title);
            }
        }
        
        // Extract date using config pattern
        Matcher dateMatcher = config.getPromulgationDate().matcher(text);
        if (dateMatcher.find()) {
            String day = dateMatcher.group(1);
            String month = dateMatcher.group(3);
            String year = dateMatcher.group(4);
            metadata.setPromulgationDate(formatDate(day, month, year));
        }
        
        // Extract city using config pattern
        Matcher cityMatcher = config.getPromulgationCity().matcher(text);
        if (cityMatcher.find()) {
            metadata.setPromulgationCity(cityMatcher.group(1).trim());
        }
        
        // Extract signatories using config patterns
        List<Signatory> signatories = new ArrayList<>();
        for (Map.Entry<Pattern, Signatory> entry : config.getSignatoryPatterns().entrySet()) {
            Matcher matcher = entry.getKey().matcher(text);
            if (matcher.find()) {
                signatories.add(entry.getValue());
            }
        }
        metadata.setSignatories(signatories);
        
        return metadata;
    }
    
    public double calculateConfidence(String text, List<Article> articles) {
        if (text == null || text.isEmpty() || articles.isEmpty()) {
            return 0.0;
        }
        
        // Score basé sur le nombre d'articles
        double articleScore = Math.min(articles.size() / 10.0, 1.0);
        
        // Score basé sur la longueur du texte
        double textLengthScore = Math.min(text.length() / 5000.0, 1.0);
        
        // Score basé sur la qualité OCR (dictionnaire français)
        double unrec = config.unrecognizedWordsRate(text);
        double dictScore = 1.0 - unrec; // Plus de mots reconnus = meilleur score
        
        // Score basé sur les termes juridiques
        int legalTerms = config.legalTermsFound(text);
        double legalScore = Math.min(legalTerms / 8.0, 1.0); // 8 termes max
        
        // Pondération: articles (30%), longueur (20%), dictionnaire (30%), termes juridiques (20%)
        return (articleScore * 0.3) + (textLengthScore * 0.2) + (dictScore * 0.3) + (legalScore * 0.2);
    }
    
    private String formatDate(String day, String month, String year) {
        String[] months = {"janvier", "février", "mars", "avril", "mai", "juin",
                          "juillet", "août", "septembre", "octobre", "novembre", "décembre"};
        
        int monthNum = 1;
        for (int i = 0; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(month)) {
                monthNum = i + 1;
                break;
            }
        }
        
        return String.format("%s-%02d-%s", year, monthNum, day.length() == 1 ? "0" + day : day);
    }
}
