package xyz.kuailemao.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.kuailemao.config.OssProperties;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AliyunOssFileStorage implements FileStorageService {

    private static final int DELETE_BATCH_LIMIT = 1000;

    @Resource
    private OSS ossClient;

    @Resource
    private OssProperties ossProperties;

    private String bucket() {
        return ossProperties.getBucketName();
    }

    @Override
    public void put(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }
        ossClient.putObject(bucket(), objectKey, inputStream, metadata);
    }

    @Override
    public void delete(String objectKey) {
        ossClient.deleteObject(bucket(), objectKey);
    }

    @Override
    public void deleteBatch(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        for (int i = 0; i < objectKeys.size(); i += DELETE_BATCH_LIMIT) {
            int end = Math.min(i + DELETE_BATCH_LIMIT, objectKeys.size());
            List<String> chunk = objectKeys.subList(i, end);
            ossClient.deleteObjects(new DeleteObjectsRequest(bucket()).withKeys(new ArrayList<>(chunk)).withQuiet(true));
        }
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        String marker = null;
        ObjectListing listing;
        do {
            ListObjectsRequest request = new ListObjectsRequest(bucket()).withPrefix(prefix);
            if (marker != null) {
                request.setMarker(marker);
            }
            listing = ossClient.listObjects(request);
            for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                String key = summary.getKey();
                if (key != null && !key.endsWith("/")) {
                    keys.add(key);
                }
            }
            marker = listing.getNextMarker();
        } while (listing.isTruncated());
        return keys;
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            return ossClient.doesObjectExist(bucket(), objectKey);
        } catch (Exception e) {
            log.error("判断 OSS 对象是否存在失败: {}", objectKey, e);
            return false;
        }
    }
}
