package com.example.backend.backup.service;

import com.example.backend.backup.config.BackupProperties;
import com.example.backend.backup.dto.BackupFileResponse;
import com.example.backend.backup.dto.RestoreBackupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Full logical backup of the shop database with pg_dump (custom format) and restore with pg_restore.
 * Dump bytes travel over stdout/stdin so the tools may run locally or inside the database container.
 * Only one backup/restore runs at a time. The password is passed through PGPASSWORD, never on the command line.
 */
@Service
@EnableConfigurationProperties(BackupProperties.class)
public class BackupServiceImpl implements BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupServiceImpl.class);
    private static final Pattern NAME = Pattern.compile("^backup-\\d{8}-\\d{6}(-pre-restore)?\\.dump$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final BackupProperties properties;
    private final String user;
    private final String password;
    private final String host;
    private final int port;
    private final String database;
    private final ReentrantLock lock = new ReentrantLock();

    public BackupServiceImpl(BackupProperties properties, @Value("${spring.datasource.url}") String url,
                             @Value("${spring.datasource.username}") String user, @Value("${spring.datasource.password:}") String password) {
        this.properties = properties;
        this.user = user;
        this.password = password;
        URI uri = URI.create(url.replaceFirst("^jdbc:", ""));
        this.host = properties.getHost().isBlank() ? uri.getHost() : properties.getHost();
        this.port = properties.getPort() != null ? properties.getPort() : (uri.getPort() > 0 ? uri.getPort() : 5432);
        this.database = uri.getPath().replaceFirst("^/", "");
    }

    @Override
    public List<BackupFileResponse> list() {
        Path dir = directory();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> NAME.matcher(p.getFileName().toString()).matches())
                    .map(this::describe).sorted(Comparator.comparing(BackupFileResponse::name).reversed()).toList();
        } catch (IOException e) {
            throw new BackupException(500, "Không đọc được thư mục sao lưu");
        }
    }

    @Override
    public BackupFileResponse create() {
        return exclusively(() -> dump(""));
    }

    @Override
    public Path file(String name) {
        Path path = resolve(name);
        if (!Files.isRegularFile(path)) throw new BackupException(404, "Không tìm thấy bản sao lưu " + name);
        return path;
    }

    @Override
    public RestoreBackupResponse restore(String name, String confirmName) {
        if (!name.equals(confirmName == null ? null : confirmName.trim())) {
            throw new BackupException(400, "Tên xác nhận không khớp với bản sao lưu cần khôi phục");
        }
        Path source = file(name);
        return exclusively(() -> {
            BackupFileResponse safety = dump("-pre-restore");
            List<String> command = command("pg_restore", "--clean", "--if-exists", "--no-owner", "--no-privileges",
                    "--single-transaction", "--exit-on-error");
            run(command, source, null);
            log.warn("Database restored from backup {} (previous data saved as {})", name, safety.name());
            return new RestoreBackupResponse(name, safety);
        });
    }

    @Override
    public void delete(String name) {
        exclusively(() -> {
            try {
                Files.delete(file(name));
            } catch (IOException e) {
                throw new BackupException(500, "Không xóa được bản sao lưu");
            }
            return null;
        });
    }

    private BackupFileResponse dump(String suffix) {
        Path dir = directory();
        String name = "backup-" + STAMP.format(LocalDateTime.now()) + suffix + ".dump";
        Path target = dir.resolve(name);
        while (Files.exists(target)) { // two backups within the same second
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BackupException(500, "Đã hủy sao lưu"); }
            name = "backup-" + STAMP.format(LocalDateTime.now()) + suffix + ".dump";
            target = dir.resolve(name);
        }
        Path partial = dir.resolve(name + ".part");
        try {
            run(command("pg_dump", "-Fc", "--no-owner", "--no-privileges"), null, partial);
            if (Files.size(partial) == 0) throw new BackupException(500, "pg_dump không tạo ra dữ liệu");
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new BackupException(500, "Không ghi được tệp sao lưu");
        } finally {
            try { Files.deleteIfExists(partial); } catch (IOException ignored) { /* best effort */ }
        }
        log.info("Database backup written: {}", name);
        return describe(target);
    }

    private List<String> command(String tool, String... options) {
        List<String> command = new ArrayList<>();
        if (!properties.getCommandPrefix().isBlank()) command.addAll(List.of(properties.getCommandPrefix().trim().split("\\s+")));
        command.add(tool);
        command.addAll(List.of("-h", host, "-p", String.valueOf(port), "-U", user, "-d", database));
        command.addAll(List.of(options));
        return command;
    }

    /** Runs the tool; stdin from {@code input} and/or stdout to {@code output}. Fails with the tool's own message. */
    private void run(List<String> command, Path input, Path output) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PGPASSWORD", password == null ? "" : password);
        builder.redirectInput(input == null ? ProcessBuilder.Redirect.from(nullDevice()) : ProcessBuilder.Redirect.from(input.toFile()));
        builder.redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()));
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new BackupException(500, "Không chạy được " + command.get(0)
                    + ": kiểm tra cấu hình app.backup.command-prefix hoặc cài PostgreSQL client");
        }
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> {
            try (InputStream in = process.getErrorStream()) {
                return new String(in.readNBytes(8192), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
        try {
            if (!process.waitFor(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new BackupException(504, "Sao lưu/khôi phục quá thời gian cho phép");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new BackupException(500, "Đã hủy thao tác sao lưu/khôi phục");
        }
        if (process.exitValue() != 0) {
            String detail = stderr.getNow("").strip();
            log.error("{} failed with exit code {}: {}", command.get(command.size() - 1), process.exitValue(), detail);
            throw new BackupException(500, "Thao tác thất bại: " + (detail.isEmpty() ? "mã lỗi " + process.exitValue()
                    : detail.lines().reduce((a, b) -> b).orElse(detail)));
        }
    }

    private static File nullDevice() {
        return new File(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null");
    }

    private <T> T exclusively(java.util.function.Supplier<T> work) {
        if (!lock.tryLock()) throw new BackupException(409, "Đang có thao tác sao lưu/khôi phục khác, vui lòng thử lại sau");
        try { return work.get(); } finally { lock.unlock(); }
    }

    private Path directory() {
        Path dir = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        try { Files.createDirectories(dir); } catch (IOException e) { throw new BackupException(500, "Không tạo được thư mục sao lưu"); }
        return dir;
    }

    /** Only names this service generated are accepted, which also rules out path traversal. */
    private Path resolve(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new BackupException(400, "Tên bản sao lưu không hợp lệ");
        return directory().resolve(name);
    }

    private BackupFileResponse describe(Path path) {
        try {
            var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            String name = path.getFileName().toString();
            return new BackupFileResponse(name, attributes.size(),
                    OffsetDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), ZoneId.systemDefault()), name.contains("-pre-restore"));
        } catch (IOException e) {
            throw new BackupException(500, "Không đọc được tệp sao lưu");
        }
    }
}
