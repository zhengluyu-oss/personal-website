package xyz.kuailemao.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.enums.UploadEnum;
import xyz.kuailemao.exceptions.FileUploadException;
import xyz.kuailemao.storage.FileStorageService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传：校验与命名，IO 委托对象存储。
 */
@Slf4j
@Component
public class FileUploadUtils {

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private OssProperties ossProperties;

    /**
     * 上传文件
     *
     * @param uploadEnum 文件枚举
     * @param file       文件
     * @return 上传后的文件地址
     */
    public String upload(UploadEnum uploadEnum, MultipartFile file) throws Exception {
        isCheck(uploadEnum, file);
        if (!isFormatFile(file.getOriginalFilename(), uploadEnum.getFormat())) {
            log.error("--------------------上传文件格式不正确--------------------");
            throw new FileUploadException("上传文件类型错误");
        }
        String objectKey = uploadEnum.getDir() + UUID.randomUUID() + "." + getFileExtension(file.getOriginalFilename());
        putFile(objectKey, file);
        return publicUrl(objectKey);
    }

    /**
     * 上传文件 -- 指定文件名
     *
     * @param uploadEnum 文件枚举
     * @param file       文件
     * @param fileName   文件名 (不带后缀)
     * @return 上传后的文件地址
     */
    public String upload(UploadEnum uploadEnum, MultipartFile file, String fileName) throws FileUploadException {
        isCheck(uploadEnum, file);
        if (!isFormatFile(file.getOriginalFilename(), uploadEnum.getFormat())) {
            log.error("--------------------上传文件格式不正确--------------------");
            throw new FileUploadException("上传文件类型错误");
        }
        String objectKey = uploadEnum.getDir() + fileName + "." + getFileExtension(file.getOriginalFilename());
        putFile(objectKey, file);
        return publicUrl(objectKey);
    }

    /**
     * 上传文件 -- 指定动态存储文件夹 -- 指定文件名
     *
     * @param uploadEnum 文件枚举
     * @param file       文件
     * @param fileName   文件名 (不带后缀)
     * @return 上传后的文件地址
     */
    public String upload(UploadEnum uploadEnum, MultipartFile file, String fileName, String dir) throws FileUploadException {
        isCheck(uploadEnum, file);
        if (!isFormatFile(file.getOriginalFilename(), uploadEnum.getFormat())) {
            log.error("--------------------上传文件格式不正确--------------------");
            throw new FileUploadException("上传文件类型错误");
        }
        String objectKey = uploadEnum.getDir() + dir + "/" + fileName + "." + getFileExtension(file.getOriginalFilename());
        putFile(objectKey, file);
        return publicUrl(objectKey);
    }

    private void putFile(String objectKey, MultipartFile file) throws FileUploadException {
        try (InputStream stream = file.getInputStream()) {
            fileStorageService.put(objectKey, stream, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("上传文件到对象存储失败: {}", objectKey, e);
            throw new FileUploadException("文件上传错误");
        }
    }

    /**
     * 文件上传合法校验
     */
    public void isCheck(UploadEnum uploadEnum, MultipartFile file) throws FileUploadException {
        if (file.isEmpty()) {
            throw new FileUploadException("上传文件为空");
        }
        if (verifyTheFileSize(file.getSize(), uploadEnum.getLimitSize())) {
            throw new FileUploadException("上传文件超过限制大小:" + uploadEnum.getLimitSize() + "MB");
        }
    }

    public String getFileExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
    }

    public Boolean verifyTheFileSize(Long fileSize, Double limitSize) {
        return convertFileSizeToMB(fileSize) >= limitSize;
    }

    public double convertFileSizeToMB(long sizeInBytes) {
        double sizeInMB = (double) sizeInBytes / (1024 * 1024);
        String formatted = String.format("%.2f", sizeInMB);
        return Double.parseDouble(formatted);
    }

    public List<String> listFiles(String dir) {
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        return fileStorageService.list(prefix);
    }

    public boolean deleteFiles(List<String> fileNames) {
        try {
            List<String> keys = new ArrayList<>();
            for (String name : fileNames) {
                keys.add(toObjectKey(name));
            }
            fileStorageService.deleteBatch(keys);
            return true;
        } catch (Exception e) {
            log.error("批量删除文件失败", e);
            return false;
        }
    }

    public boolean deleteFile(String dir, String fileName) {
        try {
            String dirPrefix = dir.endsWith("/") ? dir : dir + "/";
            String objectName = dirPrefix + fileName;
            if (!fileStorageService.exists(objectName)) {
                log.error("文件 {} 不存在", fileName);
                return false;
            }
            fileStorageService.delete(objectName);
            log.info("文件 {} 已从对象存储删除", objectName);
            return true;
        } catch (Exception e) {
            log.error("删除文件 {} 失败: {}", fileName, e.getMessage());
            return false;
        }
    }

    public boolean isFormatFile(String fileName, List<String> format) {
        if (fileName == null) {
            return false;
        }
        for (String s : format) {
            if (fileName.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFileExist(String dir, String fileName) {
        String dirPrefix = dir.endsWith("/") ? dir : dir + "/";
        return fileStorageService.exists(dirPrefix + fileName);
    }

    public String getFileName(String path) {
        return path.substring(path.lastIndexOf("/") + 1);
    }

    public Double convertFileSizeToKB(Long fileSize) {
        return fileSize / 1024.0;
    }

    /**
     * 将完整 URL 或相对 object key 解析为 OSS 对象键。
     */
    public String toObjectKey(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            throw new IllegalArgumentException("文件路径为空");
        }
        String value = urlOrKey.trim();
        if (!value.contains("://")) {
            return stripLeadingSlash(value);
        }
        String base = publicBaseUrl();
        if (value.startsWith(base + "/")) {
            return stripLeadingSlash(value.substring(base.length() + 1));
        }
        int schemeEnd = value.indexOf("://");
        int pathStart = value.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) {
            throw new IllegalArgumentException("无法从 URL 解析对象键: " + urlOrKey);
        }
        return stripLeadingSlash(value.substring(pathStart));
    }

    public String publicUrl(String objectKey) {
        return publicBaseUrl() + "/" + stripLeadingSlash(objectKey);
    }

    private String publicBaseUrl() {
        String domain = ossProperties.getDomain();
        if (domain != null && !domain.isBlank()) {
            return trimTrailingSlash(domain.trim());
        }
        String endpoint = ossProperties.getEndpoint().trim().replaceFirst("^https?://", "");
        return "https://" + ossProperties.getBucketName() + "." + endpoint;
    }

    private static String stripLeadingSlash(String value) {
        return value.replaceFirst("^/+", "");
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }
}
