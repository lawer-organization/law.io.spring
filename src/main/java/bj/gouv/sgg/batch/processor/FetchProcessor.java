package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.util.RateLimitHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processor qui vérifie l'existence d'un document via HTTP HEAD
 * Gère automatiquement le padding pour les numéros 1-9
 * Gère les rate limits (429) avec retry automatique
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final LawProperties properties;
    private final RateLimitHandler rateLimitHandler;

    // Statistiques locales pour le step courant
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger foundCount = new AtomicInteger(0);
    private final AtomicInteger paddedFound2Count = new AtomicInteger(0);
    private final AtomicInteger paddedFound3Count = new AtomicInteger(0);
    private final AtomicInteger notFoundCount = new AtomicInteger(0);
    private final AtomicInteger rateLimitedCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    @Override
    public LawDocument process(LawDocument document) throws Exception {
        totalProcessed.incrementAndGet();

        int statusCode = rateLimitHandler.executeWithRetry(document.getUrl(), this::tryFetch);

        if (statusCode == 200) {
            document.setExists(true);
            document.setStatus(LawDocument.ProcessingStatus.FETCHED);
            foundCount.incrementAndGet();
            log.debug("Found: {}", document.getDocumentId());
            return document;
        }

        if (statusCode == 429) {
            rateLimitedCount.incrementAndGet();
            log.warn("Rate limited (429) after retries, skipping: {}", document.getDocumentId());
            return null;
        }

        if (statusCode < 0) { // Erreur réseau
            errorCount.incrementAndGet();
            log.warn("Network/error status {} for {}", statusCode, document.getDocumentId());
            return null;
        }

        // Padding 2 chiffres (1-9)
        if (statusCode == 404 && document.getNumber() >= 1 && document.getNumber() <= 9) {
            String paddedUrl = buildPaddedUrl(document, 2);
            int paddedStatus = rateLimitHandler.executeWithRetry(paddedUrl, this::tryFetch);
            if (paddedStatus == 200) {
                document.setUrl(paddedUrl);
                document.setExists(true);
                document.setStatus(LawDocument.ProcessingStatus.FETCHED);
                paddedFound2Count.incrementAndGet();
                foundCount.incrementAndGet();
                log.debug("Found with 2-digit padding: {} -> {}", document.getDocumentId(), paddedUrl);
                return document;
            } else if (paddedStatus == 429) {
                rateLimitedCount.incrementAndGet();
                log.warn("Rate limited (429) on padded (2) URL, skipping: {}", document.getDocumentId());
                return null;
            }
            // 404 confirmé même avec padding 2
            document.setExists(false);
            document.setStatus(LawDocument.ProcessingStatus.PENDING);
            notFoundCount.incrementAndGet();
            log.trace("Not found (404) with or without 2-digit padding: {}", document.getDocumentId());
            return document;
        }

        // Padding 3 chiffres (10-99)
        if (statusCode == 404 && document.getNumber() >= 10 && document.getNumber() <= 99) {
            String paddedUrl = buildPaddedUrl(document, 3);
            int paddedStatus = rateLimitHandler.executeWithRetry(paddedUrl, this::tryFetch);
            if (paddedStatus == 200) {
                document.setUrl(paddedUrl);
                document.setExists(true);
                document.setStatus(LawDocument.ProcessingStatus.FETCHED);
                paddedFound3Count.incrementAndGet();
                foundCount.incrementAndGet();
                log.debug("Found with 3-digit padding: {} -> {}", document.getDocumentId(), paddedUrl);
                return document;
            } else if (paddedStatus == 429) {
                rateLimitedCount.incrementAndGet();
                log.warn("Rate limited (429) on padded (3) URL, skipping: {}", document.getDocumentId());
                return null;
            }
            // 404 confirmé même avec padding 3
            document.setExists(false);
            document.setStatus(LawDocument.ProcessingStatus.PENDING);
            notFoundCount.incrementAndGet();
            log.trace("Not found (404) with or without 3-digit padding: {}", document.getDocumentId());
            return document;
        }

        // 404 sans padding possible (>=100)
        if (statusCode == 404) {
            document.setExists(false);
            document.setStatus(LawDocument.ProcessingStatus.PENDING);
            notFoundCount.incrementAndGet();
            log.trace("Not found (404): {}", document.getDocumentId());
            return document;
        }

        log.warn("Unexpected status {} for: {}", statusCode, document.getDocumentId());
        return null;
    }
    
    private int tryFetch(String url) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpHead request = new HttpHead(url);
            request.setHeader("User-Agent", properties.getUserAgent());
            
            var response = client.executeOpen(null, request, null);
            try {
                int statusCode = response.getCode();
                EntityUtils.consume(response.getEntity());
                return statusCode;
            } finally {
                response.close();
            }
        } catch (Exception e) {
            log.error("Error fetching {}: {}", url, e.getMessage());
            return -1;
        }
    }
    
    private String buildPaddedUrl(LawDocument document, int digits) {
        String format = "%0" + digits + "d";
        String paddedNumber = String.format(format, document.getNumber());
        return String.format("%s/%s-%d-%s/download", 
            properties.getBaseUrl(), document.getType(), document.getYear(), paddedNumber);
    }

    public void resetStats() {
        totalProcessed.set(0);
        foundCount.set(0);
        paddedFound2Count.set(0);
        paddedFound3Count.set(0);
        notFoundCount.set(0);
        rateLimitedCount.set(0);
        errorCount.set(0);
    }

    public String statsSummary() {
        return String.format("processed=%d found=%d notFound=%d pad2=%d pad3=%d rateLimited=%d errors=%d", 
            totalProcessed.get(), foundCount.get(), notFoundCount.get(), paddedFound2Count.get(), 
            paddedFound3Count.get(), rateLimitedCount.get(), errorCount.get());
    }
}
