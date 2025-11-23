package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.batch.util.DocumentIdParser;
import bj.gouv.sgg.batch.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.DownloadResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.DownloadResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reader qui lit les PDFs déjà téléchargés depuis la base de données
 * Limite le nombre de documents à traiter selon la configuration
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfFileReader implements ItemReader<LawDocument> {
    
    private final DownloadResultRepository downloadResultRepository;
    private final LawProperties properties;
    private final LawDocumentFactory documentFactory;
    private List<LawDocument> pdfDocuments;
    private int currentIndex = 0;
    
    @Override
    public synchronized LawDocument read() {
        if (pdfDocuments == null) {
            pdfDocuments = loadPdfFilesFromDatabase();
            int maxDocs = properties.getBatch().getMaxDocumentsToExtract();
            int actualCount = Math.min(pdfDocuments.size(), maxDocs);
            
            if (pdfDocuments.size() > maxDocs) {
                pdfDocuments = pdfDocuments.subList(0, maxDocs);
                log.info("Limited to {} PDF files to process (max configured: {})", actualCount, maxDocs);
            } else {
                log.info("Found {} PDF files in database to process", pdfDocuments.size());
            }
        }
        
        if (currentIndex < pdfDocuments.size()) {
            return pdfDocuments.get(currentIndex++);
        }
        
        return null;
    }
    
    private List<LawDocument> loadPdfFilesFromDatabase() {
        List<LawDocument> docs = new ArrayList<>();
        
        List<DownloadResult> downloads = downloadResultRepository.findAll();
        
        for (DownloadResult download : downloads) {
            String documentId = download.getDocumentId();
            
            DocumentIdParser.ParsedDocument parsed = DocumentIdParser.parse(documentId);
            if (parsed != null) {
                LawDocument doc = documentFactory.create(parsed.getType(), parsed.getYear(), parsed.getNumber());
                doc.setPdfPath(documentId); // Virtual path = documentId
                doc.setSha256(download.getSha256());
                doc.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
                docs.add(doc);
            }
        }
        
        // Trier par année DESC, numéro DESC (conforme à CrawlerService)
        docs.sort((a, b) -> {
            if (b.getYear() != a.getYear()) {
                return Integer.compare(b.getYear(), a.getYear());
            }
            return Integer.compare(b.getNumber(), a.getNumber());
        });
        
        return docs;
    }
    
    public void reset() {
        currentIndex = 0;
        pdfDocuments = null;
    }
}
