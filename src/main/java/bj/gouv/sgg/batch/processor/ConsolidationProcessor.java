package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.ArticleExtraction;
import bj.gouv.sgg.model.LawDocument;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Processor qui lit les fichiers JSON d'articles et les prépare pour consolidation en base
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsolidationProcessor implements ItemProcessor<LawDocument, List<ArticleExtraction>> {

    private final LawProperties properties;
    private final Gson gson;

    @Override
    // Note: SonarLint warning about @Nullable incompatibility is a false positive
    // ItemProcessor contract explicitly allows null returns for item filtering
    public List<ArticleExtraction> process(LawDocument document) throws Exception {
        // Lire le fichier JSON
        String articlesPath = properties.getDirectories().getData() + File.separator + "articles" + File.separator + document.getType();
        File jsonFile = new File(articlesPath, document.getDocumentId() + ".json");
        
        if (!jsonFile.exists()) {
            log.warn("JSON file not found for consolidation: {}", document.getDocumentId());
            return Collections.emptyList();
        }

        try (FileReader reader = new FileReader(jsonFile)) {
            Type listType = new TypeToken<List<ArticleExtraction>>(){}.getType();
            List<ArticleExtraction> extractions = gson.fromJson(reader, listType);
            log.info("Loaded {} articles from JSON for consolidation: {}", extractions.size(), document.getDocumentId());
            return extractions;
        } catch (Exception e) {
            log.error("Failed to read JSON file for {}: {}", document.getDocumentId(), e.getMessage());
            throw e;
        }
    }
}
