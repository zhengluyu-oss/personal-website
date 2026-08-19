package xyz.kuailemao.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储抽象，当前实现为阿里云 OSS。
 */
public interface FileStorageService {

    void put(String objectKey, InputStream inputStream, long contentLength, String contentType);

    void delete(String objectKey);

    void deleteBatch(List<String> objectKeys);

    List<String> list(String prefix);

    boolean exists(String objectKey);
}
