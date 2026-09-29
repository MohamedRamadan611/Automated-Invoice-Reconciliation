package com.agent.reconciliation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempUploadDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(tempUploadDir.toString());
    }

    @Test
    @DisplayName("Should successfully store file with UUID-prefixed name and load it as Resource")
    void shouldStoreAndLoadFileSuccessfully() throws IOException {
        byte[] content = "Sample invoice PDF binary content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample_invoice.pdf",
                "application/pdf",
                content
        );

        String storedPath = fileStorageService.storeFile(file);

        assertThat(storedPath).isNotNull().isNotBlank();
        Path savedFilePath = Path.of(storedPath);
        assertThat(Files.exists(savedFilePath)).isTrue();
        assertThat(savedFilePath.getFileName().toString()).contains("sample_invoice.pdf");
        assertThat(Files.readAllBytes(savedFilePath)).isEqualTo(content);

        // Load as resource
        Resource resource = fileStorageService.loadFileAsResource(storedPath);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should reject empty or null files")
    void shouldRejectEmptyOrNullFiles() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeFile(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to store empty or null file.");

        assertThatThrownBy(() -> fileStorageService.storeFile(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject filename with path traversal sequence")
    void shouldRejectPathTraversal() {
        MockMultipartFile badFile = new MockMultipartFile("file", "../secret.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeFile(badFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename contains invalid relative path sequence");
    }

    @Test
    @DisplayName("Should throw exception when loading non-existent file")
    void shouldThrowWhenFileNotFound() {
        String nonExistentPath = tempUploadDir.resolve("missing_invoice.pdf").toString();

        assertThatThrownBy(() -> fileStorageService.loadFileAsResource(nonExistentPath))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File not found or not readable");
    }
}
