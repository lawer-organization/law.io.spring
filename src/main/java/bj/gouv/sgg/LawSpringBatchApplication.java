package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// @EnableBatchProcessing - Removed: Spring Boot 3.x auto-configures Batch and initializes schema automatically
public class LawSpringBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawSpringBatchApplication.class, args);
    }
}
