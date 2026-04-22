package com.wikisprint.server.global.common.storage;

import com.wikisprint.server.global.common.status.FileException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Component
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.storage.root:./storage}")
    private String storageRoot;

    @Value("${file.max-size:52428800}")
    private long maxFileSize;

    @PostConstruct
    void ensureRoot() throws IOException {
        Files.createDirectories(Paths.get(storageRoot));
    }

    @Override
    public String buildStoragePath(String userUuid, String accountUuid, String category, String identifier) {
        StringBuilder path = new StringBuilder();
        path.append(storageRoot).append("/");
        path.append(sanitizePath(userUuid)).append("/");
        path.append(sanitizePath(accountUuid)).append("/");
        path.append(sanitizePath(category));

        if (identifier != null && !identifier.isEmpty()) {
            path.append("/").append(sanitizePath(identifier));
        }

        return path.toString();
    }

    @Override
    public String saveFile(MultipartFile file, String storagePath, String storedName) throws IOException {
        validateFile(file);

        Path directoryPath = Paths.get(storagePath);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }

        Path filePath = directoryPath.resolve(storedName);

        if (!filePath.normalize().startsWith(directoryPath.normalize())) {
            throw new FileException("Invalid file path detected");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        return filePath.toString();
    }

    @Override
    public boolean deleteFile(String fullPath) {
        try {
            Path path = Paths.get(fullPath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean deleteDirectory(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                // ignore individual deletion failures
                            }
                        });
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public byte[] readFile(String fullPath) throws IOException {
        Path path = Paths.get(fullPath);
        if (!Files.exists(path)) {
            throw new FileException("File not found: " + fullPath);
        }
        return Files.readAllBytes(path);
    }

    @Override
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new FileException("File size exceeds maximum allowed size (" + maxFileSize / 1024 / 1024 + "MB)");
        }
    }

    @Override
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    @Override
    public String buildUri(String userUuid, String accountUuid, String category, String identifier, String storedName) {
        StringBuilder uri = new StringBuilder();
        uri.append(sanitizePath(userUuid)).append("/");
        uri.append(sanitizePath(accountUuid)).append("/");
        uri.append(sanitizePath(category));

        if (identifier != null && !identifier.isEmpty()) {
            uri.append("/").append(sanitizePath(identifier));
        }

        uri.append("/").append(storedName);
        return uri.toString();
    }

    @Override
    public String getStorageRoot() {
        return storageRoot;
    }

    private String sanitizePath(String pathComponent) {
        if (pathComponent == null) {
            return "";
        }
        return pathComponent.replaceAll("[^a-zA-Z0-9\\-_]", "");
    }
}
