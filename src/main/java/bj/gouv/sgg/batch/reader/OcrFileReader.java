package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader qui lit les fichiers OCR présents sur le disque pour extraction d'articles.
 * Ne lit que les fichiers OCR qui n'ont pas encore d'articles extraits en base.
 */
@Slf4j
@Component
@org.springframework.batch.core.configuration.annotation.StepScope
@RequiredArgsConstructor
public class OcrFileReader implements ItemReader<LawDocument> {

    private final LawProperties properties;
    private List<LawDocument> documents;
    private int index = 0;

    @Override
    public synchronized LawDocument read() {
        if (documents == null) {
            log.info("OcrFileReader: Starting filesystem scan from working directory: {}", System.getProperty("user.dir"));
            documents = scanFilesystem();
            int maxDocs = properties.getBatch().getMaxDocumentsToExtract();
            if (documents.size() > maxDocs) {
                documents = documents.subList(0, maxDocs);
                log.info("Limited to {} OCR files to process (max configured: {})", maxDocs, maxDocs);
            } else {
                log.info("Found {} OCR files on disk to process", documents.size());
            }
        }
        if (index < documents.size()) {
            return documents.get(index++);
        }
        return null;
    }

    private List<LawDocument> scanFilesystem() {
        List<LawDocument> list = new ArrayList<>();
        Path base = Path.of("data", "ocr", "loi"); // Pour l'instant seulement lois
        if (!Files.exists(base)) {
            return list;
        }
        try (var stream = Files.list(base)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".txt"))
                  .forEach(p -> {
                      String filename = p.getFileName().toString();
                      String id = filename.substring(0, filename.length() - 4); // retirer .txt
                      String[] parts = id.split("-");
                      if (parts.length >= 3) {
                          try {
                              String type = parts[0];
                              int year = Integer.parseInt(parts[1]);
                              int number = Integer.parseInt(parts[2]);
                              
                              LawDocument doc = LawDocument.builder()
                                      .type(type)
                                      .year(year)
                                      .number(number)
                                      .pdfPath(id)
                                      .status(LawDocument.ProcessingStatus.DOWNLOADED)
                                      .build();
                              list.add(doc);
                          } catch (NumberFormatException e) {
                              log.warn("Cannot parse file name: {}", filename);
                          }
                      }
                  });
        } catch (Exception e) {
            log.error("Error scanning OCR directory: {}", e.getMessage());
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
