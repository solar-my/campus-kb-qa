package com.campus.kb.file;

import com.campus.kb.auth.UserPrincipal;
import com.campus.kb.common.BizException;
import com.campus.kb.file.FileDtos.FileDto;
import com.campus.kb.file.FileDtos.UploadResponse;
import com.campus.kb.rag.VectorStoreService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FileService {

    public static final String UPLOAD_SUCCESS_MESSAGE =
            "✅ 文件已接收，正在解析并录入知识库，解析完成后你可以针对这份资料提问、"
                    + "划重点、梳理考点进行复习。";

    private final KnowledgeFileRepository fileRepository;
    private final FileStorageService storageService;
    private final FileIngestService ingestService;
    private final VectorStoreService vectorStoreService;

    public FileService(KnowledgeFileRepository fileRepository,
                       FileStorageService storageService,
                       FileIngestService ingestService,
                       VectorStoreService vectorStoreService) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.ingestService = ingestService;
        this.vectorStoreService = vectorStoreService;
    }

    public UploadResponse upload(MultipartFile multipart, String visibility, UserPrincipal principal) {
        boolean admin = isAdmin(principal);
        Visibility target = admin && "public".equalsIgnoreCase(visibility)
                ? Visibility.PUBLIC : Visibility.PRIVATE;

        FileStorageService.StoredFile stored = storageService.store(multipart);

        boolean duplicate = target == Visibility.PUBLIC
                ? fileRepository.existsBySha256AndVisibility(stored.sha256(), Visibility.PUBLIC)
                : fileRepository.existsBySha256AndOwnerIdAndVisibility(
                        stored.sha256(), principal.id(), Visibility.PRIVATE);
        if (duplicate) {
            storageService.delete(stored.storedName());
            throw new BizException(HttpStatus.CONFLICT, "相同内容的资料已存在，请勿重复上传");
        }

        KnowledgeFile file = new KnowledgeFile();
        file.setOwnerId(principal.id());
        file.setOwnerName(principal.displayName());
        file.setVisibility(target);
        file.setOriginalName(stored.originalName());
        file.setStoredName(stored.storedName());
        file.setFileType(stored.extension().replace(".", ""));
        file.setFileSize(stored.size());
        file.setSha256(stored.sha256());
        file.setStatus(FileStatus.PENDING);
        file.setChunkCount(0);
        fileRepository.save(file);

        ingestService.processAsync(file.getId());
        return new UploadResponse(true, UPLOAD_SUCCESS_MESSAGE, FileDto.from(file));
    }

    public List<FileDto> myList(UserPrincipal principal) {
        return fileRepository.findByOwnerIdOrderByCreatedAtDesc(principal.id())
                .stream().map(FileDto::from).toList();
    }

    public List<FileDto> publicList() {
        return fileRepository.findByVisibilityOrderByCreatedAtDesc(Visibility.PUBLIC)
                .stream().map(FileDto::from).toList();
    }

    public List<FileDto> accessibleList(UserPrincipal principal) {
        return fileRepository.findByVisibilityOrOwnerIdOrderByCreatedAtDesc(
                        Visibility.PUBLIC, principal.id())
                .stream().map(FileDto::from).toList();
    }

    public KnowledgeFile getAccessible(Long fileId, UserPrincipal principal) {
        KnowledgeFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "文件不存在"));
        if (!canView(file, principal)) {
            // 返回 404 而不是 403，避免泄露其他用户私有资料是否存在。
            throw new BizException(HttpStatus.NOT_FOUND, "文件不存在或无权访问");
        }
        return file;
    }

    public void delete(Long fileId, UserPrincipal principal) {
        KnowledgeFile file = getManageable(fileId, principal);
        if (file.getStatus() == FileStatus.PROCESSING) {
            throw new BizException(HttpStatus.CONFLICT, "文件正在解析入库，请稍后再删除");
        }
        vectorStoreService.deleteByFileId(fileId);
        storageService.delete(file.getStoredName());
        fileRepository.delete(file);
    }

    public FileDto reindex(Long fileId, UserPrincipal principal) {
        KnowledgeFile file = getManageable(fileId, principal);
        if (file.getStatus() == FileStatus.PROCESSING) {
            throw new BizException(HttpStatus.CONFLICT, "文件正在解析入库，请稍后再试");
        }
        file.setStatus(FileStatus.PENDING);
        file.setChunkCount(0);
        file.setErrorMessage(null);
        fileRepository.save(file);
        ingestService.processAsync(fileId);
        return FileDto.from(file);
    }

    public Path pathForDownload(Long fileId, UserPrincipal principal) {
        KnowledgeFile file = getAccessible(fileId, principal);
        Path path = storageService.pathOf(file.getStoredName());
        if (!Files.exists(path)) {
            throw new BizException(HttpStatus.NOT_FOUND, "原始文件已丢失，请重新上传");
        }
        return path;
    }

    public KnowledgeFile getById(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "文件不存在"));
    }

    private KnowledgeFile getManageable(Long fileId, UserPrincipal principal) {
        KnowledgeFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "文件不存在"));
        if (!canManage(file, principal)) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件不存在或无权操作");
        }
        return file;
    }

    public static boolean canView(KnowledgeFile file, UserPrincipal principal) {
        // 管理员可以管理文件，但不应读取学生私有资料的正文/向量。
        return file.getVisibility() == Visibility.PUBLIC
                || file.getOwnerId().equals(principal.id());
    }

    public static boolean canManage(KnowledgeFile file, UserPrincipal principal) {
        return file.getOwnerId().equals(principal.id()) || isAdmin(principal);
    }

    public static boolean isAdmin(UserPrincipal principal) {
        return principal != null && "ADMIN".equals(principal.role());
    }
}