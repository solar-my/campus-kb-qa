package com.campus.kb.file;

import com.campus.kb.auth.UserPrincipal;
import com.campus.kb.file.FileDtos.FileDto;
import com.campus.kb.file.FileDtos.UploadResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "visibility", required = false) String visibility,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return fileService.upload(file, visibility, principal);
    }

    @GetMapping("/myList")
    public List<FileDto> myList(@AuthenticationPrincipal UserPrincipal principal) {
        return fileService.myList(principal);
    }

    @GetMapping("/publicList")
    public List<FileDto> publicList() {
        return fileService.publicList();
    }

    @GetMapping("/accessibleList")
    public List<FileDto> accessibleList(@AuthenticationPrincipal UserPrincipal principal) {
        return fileService.accessibleList(principal);
    }

    @DeleteMapping("/{fileId}")
    public Map<String, Object> delete(@PathVariable Long fileId,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        fileService.delete(fileId, principal);
        return Map.of("success", true, "message", "删除成功");
    }

    @PostMapping("/{fileId}/reindex")
    public FileDto reindex(@PathVariable Long fileId,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return fileService.reindex(fileId, principal);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long fileId,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        Path path = fileService.pathForDownload(fileId, principal);
        KnowledgeFile file = fileService.getById(fileId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }
}