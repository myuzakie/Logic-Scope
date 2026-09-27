package com.logicscope.project;

import com.logicscope.domain.ProjectId;
import com.logicscope.domain.ProjectRecord;
import com.logicscope.domain.ProjectSourceType;
import com.logicscope.domain.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FileProjectRegistryTest {

    @TempDir
    Path tempDir;

    private FileProjectRegistry registry() {
        return new FileProjectRegistry(tempDir.resolve("registry.json"));
    }

    private ProjectRecord record(String idValue, String name) {
        return new ProjectRecord(
                new ProjectId(idValue), name, ProjectSourceType.LOCAL,
                null, "/some/path", null, ProjectStatus.READY, null, Instant.now());
    }

    @Test
    void savesAndFindsById() throws IOException {
        var reg = registry();
        ProjectRecord r = record("proj-aabbccddee01", "my-project");
        reg.save(r);
        Optional<ProjectRecord> found = reg.findById("proj-aabbccddee01");
        assertTrue(found.isPresent());
        assertEquals("my-project", found.get().name());
    }

    @Test
    void findAllReturnsEmpty_whenNoRegistryFile() throws IOException {
        var reg = registry();
        assertEquals(List.of(), reg.findAll());
    }

    @Test
    void findByIdReturnsEmpty_whenNotFound() throws IOException {
        var reg = registry();
        reg.save(record("proj-aabbccddee01", "a"));
        assertTrue(reg.findById("proj-notexist000").isEmpty());
    }

    @Test
    void saveUpdatesExistingRecord() throws IOException {
        var reg = registry();
        ProjectRecord original = record("proj-aabbccddee01", "original-name");
        reg.save(original);

        ProjectRecord updated = original.withStatus(ProjectStatus.FAILED, "something went wrong");
        reg.save(updated);

        ProjectRecord found = reg.findById("proj-aabbccddee01").orElseThrow();
        assertEquals(ProjectStatus.FAILED, found.status());
        assertEquals("something went wrong", found.failureReason());
        assertEquals(1, reg.findAll().size()); // not duplicated
    }

    @Test
    void findAllReturnsAllRecords() throws IOException {
        var reg = registry();
        reg.save(record("proj-aabbccddee01", "a"));
        reg.save(record("proj-aabbccddee02", "b"));
        reg.save(record("proj-aabbccddee03", "c"));
        assertEquals(3, reg.findAll().size());
    }

    @Test
    void persistsAcrossInstances() throws IOException {
        Path registryFile = tempDir.resolve("registry.json");
        new FileProjectRegistry(registryFile).save(record("proj-aabbccddee01", "persisted"));

        Optional<ProjectRecord> found = new FileProjectRegistry(registryFile)
                .findById("proj-aabbccddee01");
        assertTrue(found.isPresent());
        assertEquals("persisted", found.get().name());
    }

    @Test
    void preservesAllFields() throws IOException {
        var reg = registry();
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        ProjectRecord r = new ProjectRecord(
                new ProjectId("proj-aabbccddee01"),
                "test-project",
                ProjectSourceType.GIT,
                "https://github.com/example/repo",
                "/workspace/proj-aabbccddee01",
                "abc123def456abc123def456abc123def456abc1",
                ProjectStatus.READY,
                null,
                now);
        reg.save(r);

        ProjectRecord loaded = reg.findById("proj-aabbccddee01").orElseThrow();
        assertEquals("test-project", loaded.name());
        assertEquals(ProjectSourceType.GIT, loaded.sourceType());
        assertEquals("https://github.com/example/repo", loaded.sourceUrl());
        assertEquals("/workspace/proj-aabbccddee01", loaded.localPath());
        assertEquals("abc123def456abc123def456abc123def456abc1", loaded.commitSha());
        assertEquals(ProjectStatus.READY, loaded.status());
        assertNull(loaded.failureReason());
        assertEquals(now, loaded.registeredAt());
    }

    @Test
    void concurrentWritesProduceValidRegistry() throws Exception {
        Path registryFile = tempDir.resolve("concurrent-registry.json");
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        List<Exception> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    String idValue = String.format("proj-%012d", index);
                    new FileProjectRegistry(registryFile)
                            .save(record(idValue, "project-" + index));
                } catch (Exception e) {
                    errors.add(e);
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "Concurrent writes produced errors: " + errors);
        List<ProjectRecord> all = new FileProjectRegistry(registryFile).findAll();
        assertEquals(threadCount, all.size(), "All records must be present after concurrent writes");
    }
}
