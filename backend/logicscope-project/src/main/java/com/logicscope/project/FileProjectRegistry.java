package com.logicscope.project;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logicscope.domain.ProjectId;
import com.logicscope.domain.ProjectRecord;
import com.logicscope.domain.ProjectSourceType;
import com.logicscope.domain.ProjectStatus;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed implementation of {@link ProjectRegistry}.
 *
 * <p>The registry is stored as a JSON array at the path supplied by
 * {@link WorkspaceManager#registryFile()}. Each entry maps to a flat JSON object with
 * all {@link ProjectRecord} fields.
 *
 * <p>Concurrency: every read-modify-write cycle holds an exclusive OS-level file lock on a
 * companion lock file ({@code registry.json.lock}). Read operations use a shared lock. This
 * makes concurrent CLI invocations safe; it does not protect against out-of-process
 * modifications.
 *
 * <p>Atomic writes: the JSON is written to a temporary sibling file and then moved to the
 * registry path. On filesystems that support atomic rename this means a reader always sees
 * either the old or the new version, never a partial write.
 */
public final class FileProjectRegistry implements ProjectRegistry {

    private static final ObjectMapper MAPPER = buildMapper();

    /**
     * JVM-level locks keyed by canonical registry file path.
     * Prevents {@link java.nio.channels.OverlappingFileLockException} when multiple
     * {@link FileProjectRegistry} instances in the same JVM access the same file.
     */
    private static final ConcurrentHashMap<String, ReentrantLock> JVM_LOCKS =
            new ConcurrentHashMap<>();

    private final Path registryFile;
    private final Path lockFile;
    private final ReentrantLock jvmLock;

    public FileProjectRegistry(WorkspaceManager workspaceManager) {
        this(workspaceManager.registryFile());
    }

    /** Package-private for testing. */
    FileProjectRegistry(Path registryFile) {
        this.registryFile = registryFile;
        this.lockFile = registryFile.resolveSibling(registryFile.getFileName() + ".lock");
        this.jvmLock = JVM_LOCKS.computeIfAbsent(
                registryFile.toAbsolutePath().normalize().toString(),
                k -> new ReentrantLock());
    }

    @Override
    public void save(ProjectRecord record) throws IOException {
        Files.createDirectories(registryFile.getParent());
        jvmLock.lock();
        try (var lockChannel = openLockFile();
             var ignored = lockChannel.lock()) { // exclusive OS-level lock for cross-process safety
            List<ProjectRecord> records = readUnderLock();
            Map<String, ProjectRecord> byId = new LinkedHashMap<>();
            for (ProjectRecord existing : records) {
                byId.put(existing.id().value(), existing);
            }
            byId.put(record.id().value(), record);
            writeUnderLock(new ArrayList<>(byId.values()));
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public Optional<ProjectRecord> findById(String id) throws IOException {
        return findAll().stream().filter(r -> r.id().value().equals(id)).findFirst();
    }

    @Override
    public List<ProjectRecord> findAll() throws IOException {
        if (!Files.exists(registryFile)) {
            return List.of();
        }
        jvmLock.lock();
        try (var lockChannel = openLockFile();
             var ignored = lockChannel.lock(0, Long.MAX_VALUE, true)) { // shared OS-level lock
            return readUnderLock();
        } finally {
            jvmLock.unlock();
        }
    }

    private List<ProjectRecord> readUnderLock() throws IOException {
        if (!Files.exists(registryFile)) {
            return new ArrayList<>();
        }
        List<RecordDto> dtos = MAPPER.readValue(registryFile.toFile(),
                new TypeReference<List<RecordDto>>() {
                });
        return dtos.stream().map(FileProjectRegistry::fromDto).toList();
    }

    private void writeUnderLock(List<ProjectRecord> records) throws IOException {
        List<RecordDto> dtos = records.stream().map(FileProjectRegistry::toDto).toList();
        Path tmp = registryFile.resolveSibling(registryFile.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), dtos);
        try {
            Files.move(tmp, registryFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Fallback for filesystems that do not support atomic rename
            Files.move(tmp, registryFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private FileChannel openLockFile() throws IOException {
        Files.createDirectories(lockFile.getParent());
        return new RandomAccessFile(lockFile.toFile(), "rw").getChannel();
    }

    private static ObjectMapper buildMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .build();
    }

    // ---- DTO for serialization ----

    /** Flat JSON representation of a {@link ProjectRecord}. */
    static final class RecordDto {
        public String id;
        public String name;
        public String sourceType;
        public String sourceUrl;
        public String localPath;
        public String commitSha;
        public String status;
        public String failureReason;
        public String registeredAt;
    }

    private static RecordDto toDto(ProjectRecord r) {
        RecordDto dto = new RecordDto();
        dto.id = r.id().value();
        dto.name = r.name();
        dto.sourceType = r.sourceType().name();
        dto.sourceUrl = r.sourceUrl();
        dto.localPath = r.localPath();
        dto.commitSha = r.commitSha();
        dto.status = r.status().name();
        dto.failureReason = r.failureReason();
        dto.registeredAt = r.registeredAt().toString();
        return dto;
    }

    private static ProjectRecord fromDto(RecordDto dto) {
        return new ProjectRecord(
                new ProjectId(dto.id),
                dto.name,
                ProjectSourceType.valueOf(dto.sourceType),
                dto.sourceUrl,
                dto.localPath,
                dto.commitSha,
                ProjectStatus.valueOf(dto.status),
                dto.failureReason,
                Instant.parse(dto.registeredAt));
    }
}
