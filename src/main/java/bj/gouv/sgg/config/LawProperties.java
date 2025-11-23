package bj.gouv.sgg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "law")
public class LawProperties {
    
    private String baseUrl;
    private int endYear;
    private int maxNumberPerYear;
    private String userAgent;
    
    private Directories directories = new Directories();
    private Http http = new Http();
    private Ocr ocr = new Ocr();
    private Batch batch = new Batch();
    
    @Data
    public static class Directories {
        private String database;
        private String data;
    }
    
    @Data
    public static class Http {
        private int timeout;
        private int maxRetries;
    }
    
    @Data
    public static class Ocr {
        private String language;
        private int dpi;
        private double qualityThreshold;
    }
    
    @Data
    public static class Batch {
        private int chunkSize;
        private int maxThreads;
        private int maxItemsToFetchPrevious = 5000; // Nombre maximum de documents à vérifier par exécution de fetch-previous
        private int maxDocumentsToExtract = 100; // Nombre maximum de documents à extraire par exécution
    }
}
