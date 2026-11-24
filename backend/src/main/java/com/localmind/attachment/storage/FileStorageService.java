package com.localmind.attachment.storage;

import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * Stores, loads and deletes uploaded attachment files, keyed by user id, attachment id and original file name.
 */
public interface FileStorageService {

    void store(InputStream inputStream, String userId, String attachmentId, String originalFileName);

    Resource loadAsResource(String userId, String attachmentId, String originalFileName);

    void delete(String userId, String attachmentId, String originalFileName);
}
