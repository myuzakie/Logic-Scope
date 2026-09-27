package com.logicscope.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void workspaceForCreatesDirectory() throws IOException {
        WorkspaceManager mgr = new WorkspaceManager(tempDir);
        Path ws = mgr.workspaceFor("proj-abc123");
        assertTrue(Files.isDirectory(ws));
    }

    @Test
    void workspaceForIsUnderWorkspacesRoot() throws IOException {
        WorkspaceManager mgr = new WorkspaceManager(tempDir);
        Path ws = mgr.workspaceFor("proj-abc123");
        assertTrue(ws.startsWith(mgr.workspacesRoot()));
    }

    @Test
    void workspaceForIsIdempotent() throws IOException {
        WorkspaceManager mgr = new WorkspaceManager(tempDir);
        Path ws1 = mgr.workspaceFor("proj-abc123");
        Path ws2 = mgr.workspaceFor("proj-abc123");
        assertEquals(ws1, ws2);
    }

    @Test
    void registryFileIsInsideDataDir() {
        WorkspaceManager mgr = new WorkspaceManager(tempDir);
        assertTrue(mgr.registryFile().startsWith(mgr.dataDir()));
    }

    @Test
    void pathTraversalInProjectIdIsRejected() {
        WorkspaceManager mgr = new WorkspaceManager(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.workspaceFor("../escape"));
    }

    @Test
    void deepTraversalIsRejected() {
        WorkspaceManager mgr = new WorkspaceManager(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.workspaceFor("proj-abc/../../etc"));
    }
}
