package com.campus.kb.file;

import com.campus.kb.common.BizException;
import com.campus.kb.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            ".pdf", ".docx", ".doc", ".ppt", ".pptx", ".txt", ".md", ".markdown");

    private final AppProperties properties;
    private Path uploadDir;

    public FileStorageService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.uploadDir = Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录：" + uploadDir, e);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String originalName = sanitize(file.getOriginalFilename());
        String suffix = extensionOf(originalName);
        if (!ALLOWED.contains(suffix)) {
            throw new BizException("不支持的文件类型：" + (suffix.isBlank() ? "未知" : suffix)
                    + "，支持 PDF / DOCX / PPT / PPTX / TXT / MD");
        }
        long maxBytes = properties.getStorage().getMaxFileMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "文件超过 " + properties.getStorage().getMaxFileMb() + "MB 限制");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + suffix;
        Path destination = uploadDir.resolve(storedName).normalize();
        if (!destination.startsWith(uploadDir)) {
            throw new BizException("非法文件名");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        try (InputStream input = new DigestInputStream(file.getInputStream(), digest);
             OutputStream output = Files.newOutputStream(destination)) {
            input.transferTo(output);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
            }
            throw new BizException("文件保存失败，请重试");
        }

        return new StoredFile(
                originalName,
                storedName,
                suffix,
                file.getSize(),
                HexFormat.of().formatHex(digest.digest()));
    }

    public Path pathOf(String storedName) {
        Path path = uploadDir.resolve(storedName).normalize();
        if (!path.startsWith(uploadDir)) {
            throw new BizException("非法文件路径");
        }
        return path;
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(pathOf(storedName));
        } catch (IOException ignored) {
        }
    }

    private String sanitize(String name) {
        String value = name == null ? "未命名文件" : Paths.get(name).getFileName().toString();
        value = value.replaceAll("[\\x00-\\x1f\\x7f]", "").trim();
        return value.isBlank() ? "未命名文件" : value;
    }

    private String extensionOf(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return name.substring(index).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String originalName, String storedName, String extension,
                             long size, String sha256) {
    }
}