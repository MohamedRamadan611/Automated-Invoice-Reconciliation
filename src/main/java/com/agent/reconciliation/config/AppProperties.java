package com.agent.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Type-safe configuration properties for Automated Invoice Reconciliation.
 * Groups mail settings, application URLs, and file storage settings.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Mail mail,
        Upload upload
) {
    public AppProperties {
        if (mail == null) {
            mail = new Mail(null, null, null, null);
        }
        if (upload == null) {
            upload = new Upload("uploads");
        }
    }

    public record Mail(
            @DefaultValue("mohamed.ramadan97116@gmail.com") String managerEmail,
            @DefaultValue("mohamed.ramadan61197@gmail.com") String defaultVendorEmail,
            @DefaultValue("http://localhost:8080") String backendUrl,
            @DefaultValue("http://localhost:3000") String frontendUrl
    ) {
        public String getEffectiveManagerEmail() {
            return managerEmail != null && !managerEmail.isBlank() ? managerEmail.trim() : "mohamed.ramadan97116@gmail.com";
        }

        public String getEffectiveVendorEmail() {
            return defaultVendorEmail != null && !defaultVendorEmail.isBlank() ? defaultVendorEmail.trim() : "mohamed.ramadan61197@gmail.com";
        }

        public String getEffectiveBackendUrl() {
            return backendUrl != null && !backendUrl.isBlank() ? backendUrl.trim() : "http://localhost:8080";
        }

        public String getEffectiveFrontendUrl() {
            return frontendUrl != null && !frontendUrl.isBlank() ? frontendUrl.trim() : "http://localhost:3000";
        }
    }

    public record Upload(
            @DefaultValue("uploads") String dir
    ) {
        public String getEffectiveDir() {
            return dir != null && !dir.isBlank() ? dir.trim() : "uploads";
        }
    }
}
