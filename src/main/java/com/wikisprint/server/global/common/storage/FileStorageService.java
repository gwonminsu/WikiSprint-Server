package com.wikisprint.server.global.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {
    String buildStoragePath(String userUuid, String accountUuid, String category, String identifier);
    String buildUri(String userUuid, String accountUuid, String category, String identifier, String storedName);
    String saveFile(MultipartFile file, String storagePath, String storedName) throws IOException;
    boolean deleteFile(String fullPath);
    boolean deleteDirectory(String directoryPath);
    byte[] readFile(String fullPath) throws IOException;
    void validateFile(MultipartFile file);
    String getFileExtension(String filename);
    String getStorageRoot();
}
