package com.agent.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.agent.reconciliation.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AutomatedInvoiceReconciliationApplication {

    private static final Logger log = LoggerFactory.getLogger(AutomatedInvoiceReconciliationApplication.class);

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(AutomatedInvoiceReconciliationApplication.class, args);
    }

    private static void loadDotEnv() {
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            try {
                List<String> lines = Files.readAllLines(envFile);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        int eqIdx = trimmed.indexOf('=');
                        String key = trimmed.substring(0, eqIdx).trim();
                        String value = trimmed.substring(eqIdx + 1).trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null && !value.isEmpty()) {
                            System.setProperty(key, value);
                        }
                    }
                }
                log.info("Successfully loaded environment configuration from .env file");
            } catch (Exception ex) {
                log.warn("Could not read .env file: {}", ex.getMessage());
            }
        }
    }
}
