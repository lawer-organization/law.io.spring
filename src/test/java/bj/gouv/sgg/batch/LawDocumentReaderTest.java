package bj.gouv.sgg.batch;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.batch.reader.LawDocumentReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LawDocumentReaderTest {
    
    @Autowired
    private LawProperties properties;
    
    @Test
    void testReaderGeneratesDocuments() throws Exception {
        LawDocumentReader reader = new LawDocumentReader(properties);
        
        LawDocument doc = reader.read();
        assertThat(doc).isNotNull();
        assertThat(doc.getType()).isIn("loi", "decret");
        assertThat(doc.getYear()).isGreaterThanOrEqualTo(properties.getEndYear());
        assertThat(doc.getNumber()).isPositive();
        assertThat(doc.getUrl()).isNotEmpty();
    }
}
