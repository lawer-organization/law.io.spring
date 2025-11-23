package bj.gouv.sgg.config;

import bj.gouv.sgg.model.Signatory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration holder pour l'extraction d'articles
 * Charge patterns.properties, signatories.csv et dictionnaire français
 */
@Slf4j
@Component
@Getter
public class ArticleExtractorConfig {

    private final Properties props = new Properties();
    private final Map<Pattern, Signatory> signatoryPatterns = new LinkedHashMap<>();
    private final Set<String> frenchDict = new HashSet<>();

    // Patterns pré-compilés
    private Pattern articleStart;
    private Pattern articleEndAny;
    private Pattern lawTitleStart;
    private Pattern lawTitleEnd;
    private Pattern lawEndStart;
    private Pattern lawEndEnd;
    private Pattern promulgationCity;
    private Pattern promulgationDate;

    private final String[] legalTerms = new String[]{
            "article", "loi", "décret", "dispositions", "promulgué",
            "république", "président", "ministre", "journal officiel",
            "conformément", "application", "notamment", "toutefois"
    };

    @PostConstruct
    public void init() {
        loadProperties();
        loadSignatories();
        loadDictionary();
        compilePatterns();
    }

    private void loadProperties() {
        try (InputStream is = getClass().getResourceAsStream("/patterns.properties")) {
            if (is != null) {
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                log.info("Loaded patterns.properties ({} entries)", props.size());
            } else {
                log.warn("patterns.properties not found in resources");
            }
        } catch (Exception e) {
            log.error("Failed to load patterns.properties: {}", e.getMessage());
        }
    }

    private void loadSignatories() {
        try (InputStream is = getClass().getResourceAsStream("/signatories.csv")) {
            if (is == null) {
                log.warn("signatories.csv not found");
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line = r.readLine(); // header
                int count = 0;
                while ((line = r.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        String[] parts = line.split(",", 5);
                        if (parts.length >= 3) {
                            String pattern = parts[0].trim();
                            String role = parts[1].trim();
                            String name = parts[2].trim();
                            java.time.LocalDate start = parseDate(parts, 3);
                            java.time.LocalDate end = parseDate(parts, 4);
                            
                            if (addSignatoryPattern(pattern, role, name, start, end)) {
                                count++;
                            }
                        }
                    }
                }
                log.info("Loaded {} signatory patterns from signatories.csv", count);
            }
        } catch (Exception e) {
            log.error("Failed to load signatories.csv: {}", e.getMessage());
        }
    }
    
    private LocalDate parseDate(String[] parts, int index) {
        if (parts.length > index && !parts[index].trim().isEmpty()) {
            try {
                return LocalDate.parse(parts[index].trim());
            } catch (Exception e) {
                log.debug("Failed to parse date: {}", parts[index]);
            }
        }
        return null;
    }
    
    private boolean addSignatoryPattern(String pattern, String role, String name, 
                                        LocalDate start, LocalDate end) {
        try {
            Pattern p = Pattern.compile(pattern);
            signatoryPatterns.put(p, Signatory.builder()
                    .role(role)
                    .name(name)
                    .mandateStart(start)
                    .mandateEnd(end)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("Invalid signatory pattern: {}", pattern);
            return false;
        }
    }

    private void loadDictionary() {
        try (InputStream is = getClass().getResourceAsStream("/liste.de.mots.francais.frgut.txt")) {
            if (is == null) {
                log.warn("French dictionary not found");
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String w;
                while ((w = r.readLine()) != null) {
                    w = w.trim().toLowerCase();
                    if (!w.isEmpty()) frenchDict.add(w);
                }
            }
            log.info("Loaded french dictionary: {} words", frenchDict.size());
        } catch (Exception e) {
            log.error("Failed to load french dictionary: {}", e.getMessage());
        }
    }

    private void compilePatterns() {
        try {
            articleStart = Pattern.compile(props.getProperty("article.start"));
            articleEndAny = Pattern.compile(props.getProperty("article.end.any"));
            lawTitleStart = Pattern.compile(props.getProperty("lawTitle.start"));
            lawTitleEnd = Pattern.compile(props.getProperty("lawTitle.end"));
            lawEndStart = Pattern.compile(props.getProperty("lawEndStart"));
            lawEndEnd = Pattern.compile(props.getProperty("lawEndEnd"));
            promulgationCity = Pattern.compile(props.getProperty("promulgation.city"));
            promulgationDate = Pattern.compile(props.getProperty("promulgation.date"));
            
            log.info("Compiled all regex patterns from properties");
        } catch (Exception e) {
            log.error("Failed to compile patterns: {}", e.getMessage());
            throw new IllegalStateException("Required patterns missing in patterns.properties", e);
        }
    }

    /**
     * Calcule le taux de mots non reconnus (0.0 - 1.0)
     * Utilisé pour mesurer la qualité de l'OCR
     */
    public double unrecognizedWordsRate(String text) {
        if (text == null || text.isEmpty() || frenchDict.isEmpty()) return 0.0;
        String[] words = text.toLowerCase().split("[^a-zàâäéèêëïîôùûüÿçœæ]+");
        int total = 0;
        int unrec = 0;
        for (String w : words) {
            if (w.length() < 3) continue;
            total++;
            if (!frenchDict.contains(w)) unrec++;
        }
        if (total == 0) return 0.0;
        return (double) unrec / total;
    }

    /**
     * Compte les termes juridiques trouvés dans le texte
     * Utilisé pour valider que le texte est bien juridique
     */
    public int legalTermsFound(String text) {
        if (text == null || text.isEmpty()) return 0;
        String t = text.toLowerCase();
        int found = 0;
        for (String term : legalTerms) {
            if (t.contains(term)) found++;
        }
        return found;
    }

    public Map<Pattern, Signatory> getSignatoryPatterns() {
        return Collections.unmodifiableMap(signatoryPatterns);
    }
}
