package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.batch.util.DocumentIdParser;
import bj.gouv.sgg.batch.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader qui lit les fichiers PDF présents sur le disque.
 */
@Slf4j
@Component
@org.springframework.batch.core.configuration.annotation.StepScope
@RequiredArgsConstructor
public class FilePdfReader implements ItemReader<LawDocument> {

    private final FileStorageService fileStorageService;
    private final LawProperties properties;
    private final LawDocumentFactory documentFactory;
    private List<LawDocument> documents;
    private int index = 0;

    @Override
    public synchronized LawDocument read() {
        if (documents == null) {
            log.info("FilePdfReader: Starting filesystem scan from working directory: {}", System.getProperty("user.dir"));
            documents = scanFilesystem();
            int maxDocs = properties.getBatch().getMaxDocumentsToExtract();
            if (documents.size() > maxDocs) {
                documents = documents.subList(0, maxDocs);
                log.info("Limited to {} PDF files to process (max configured: {})", maxDocs, maxDocs);
            } else {
                log.info("Found {} PDF files on disk to process", documents.size());
            }
        }
        if (index < documents.size()) {
            return documents.get(index++);
        }
        return null;
    }

    private List<LawDocument> scanFilesystem() {
        List<LawDocument> list = new ArrayList<>();
        Path base = Path.of("data", "pdfs", "loi"); // Pour l'instant seulement lois
        if (!Files.exists(base)) {
            return list;
        }
        try (var stream = Files.list(base)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".pdf"))
                  .forEach(p -> {
                      String filename = p.getFileName().toString();
                      String id = filename.substring(0, filename.length() - 4); // retirer .pdf
                      
                      DocumentIdParser.ParsedDocument parsed = DocumentIdParser.parse(id);
                      if (parsed != null && !fileStorageService.ocrExists(parsed.getType(), id)) {
                          LawDocument doc = documentFactory.create(parsed.getType(), parsed.getYear(), parsed.getNumber());
                          doc.setPdfPath(id);
                          doc.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
                          list.add(doc);
                      }
                  });
        } catch (Exception e) {
            log.error("Error scanning PDF directory: {}", e.getMessage());
        }
        list.sort((a, b) -> {
            int yc = Integer.compare(b.getYear(), a.getYear());
            if (yc != 0) return yc;
            return Integer.compare(b.getNumber(), a.getNumber());
        });
        return list;
    }

    public void reset() {
        documents = null;
        index = 0;
    }
}
