package com.agent.reconciliation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for securely storing invoice files locally and loading them as Spring resources.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path uploadDirectory;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDirectory);
            log.info("Initialized file upload directory at: {}", this.uploadDirectory);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create the upload directory at " + this.uploadDirectory, ex);
        }
    }

    /**
     * Stores an uploaded multipart file in the uploads directory with a UUID-based filename.
     *
     * @param file the uploaded file
     * @return the normalized absolute file path of the saved file
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty or null file.");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "invoice"));
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Filename contains invalid relative path sequence: " + originalFilename);
        }

        String uniqueFilename = UUID.randomUUID() + "_" + originalFilename;
        Path targetLocation = this.uploadDirectory.resolve(uniqueFilename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully stored uploaded file to: {}", targetLocation);
            return targetLocation.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store file " + uniqueFilename, ex);
        }
    }

    /**
     * Retrieves a previously saved file as a Spring Resource.
     *
     * @param filePath the file path of the saved file
     * @return Spring Resource pointing to the file
     */
    public Resource loadFileAsResource(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be null or blank.");
        }

        try {
            Path path = Paths.get(filePath).normalize();
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable at: " + filePath);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Malformed file path URL: " + filePath, ex);
        }
    }

    public Path getUploadDirectory() {
        return uploadDirectory;
    }
}
