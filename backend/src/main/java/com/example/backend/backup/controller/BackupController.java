package com.example.backend.backup.controller;

import com.example.backend.backup.dto.BackupFileResponse;
import com.example.backend.backup.dto.RestoreBackupRequest;
import com.example.backend.backup.dto.RestoreBackupResponse;
import com.example.backend.backup.service.BackupService;
import com.example.backend.common.security.RequireAnyAuthority;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/** Outline "chức năng cơ bản: backup & restore". ADMIN only. */
@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {
    private final BackupService service;

    public BackupController(BackupService service) { this.service = service; }

    @GetMapping @RequireAnyAuthority({"ADMIN"})
    public List<BackupFileResponse> list() { return service.list(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @RequireAnyAuthority({"ADMIN"})
    public BackupFileResponse create() { return service.create(); }

    @GetMapping("/{name}/download") @RequireAnyAuthority({"ADMIN"})
    public ResponseEntity<FileSystemResource> download(@PathVariable String name) {
        Path file = service.file(name);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(new FileSystemResource(file));
    }

    @PostMapping("/{name}/restore") @RequireAnyAuthority({"ADMIN"})
    public RestoreBackupResponse restore(@PathVariable String name, @Valid @RequestBody RestoreBackupRequest request) {
        return service.restore(name, request.confirmName());
    }

    @DeleteMapping("/{name}") @ResponseStatus(HttpStatus.NO_CONTENT) @RequireAnyAuthority({"ADMIN"})
    public void delete(@PathVariable String name) { service.delete(name); }
}
