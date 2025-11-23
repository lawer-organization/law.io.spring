package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Writer qui enregistre les PDFs sur disque et met à jour le statut dans fetch_results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileDownloadWriter implements ItemWriter<LawDocument> {

    private final FileStorageService fileStorageService;
    private final FetchResultRepository fetchResultRepository;

    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws Exception {
        int saved = 0;
        int skipped = 0;
        for (LawDocument doc : chunk) {
            boolean actionable = doc != null && doc.getPdfContent() != null && doc.getPdfContent().length > 0
                    && !fileStorageService.pdfExists(doc.getType(), doc.getDocumentId());
            if (!actionable) {
                skipped++;
            } else {
                fileStorageService.savePdf(doc.getType(), doc.getDocumentId(), doc.getPdfContent());
                fetchResultRepository.findByDocumentId(doc.getDocumentId()).ifPresent(fr -> {
                    fr.setStatus("DOWNLOADED");
                    fetchResultRepository.save(fr);
                });
                saved++;
                log.info("PDF enregistré sur disque: {} ({} bytes)", doc.getDocumentId(), doc.getPdfContent().length);
            }
        }
        log.info("DownloadWriter: saved={} skipped={}", saved, skipped);
    }
}
