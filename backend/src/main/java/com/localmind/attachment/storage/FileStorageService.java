package com.localmind.attachment.storage;

import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface FileStorageService {

    void store(InputStream inputStream, String userId, String attachmentId, String originalFileName);

    Resource loadAsResource(String userId, String attachmentId, String originalFileName);

    /** Deletes the stored file if present; failures are logged, never thrown. */
    void delete(String userId, String attachmentId, String originalFileName);
}
