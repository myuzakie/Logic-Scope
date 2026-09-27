package com.logicscope.project;

import com.logicscope.domain.ProjectRecord;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Persistent storage for {@link ProjectRecord} instances.
 *
 * <p>Implementations must be safe for concurrent calls from the same JVM and should
 * produce a valid registry file after any single completed operation.
 */
public interface ProjectRegistry {

    /**
     * Saves or replaces the record with the same ID. If no record with this ID exists,
     * it is added.
     *
     * @throws IOException if the registry cannot be written
     */
    void save(ProjectRecord record) throws IOException;

    /**
     * Returns the record with the given ID, or empty if not found.
     *
     * @throws IOException if the registry cannot be read
     */
    Optional<ProjectRecord> findById(String id) throws IOException;

    /**
     * Returns all registered projects.
     *
     * @throws IOException if the registry cannot be read
     */
    List<ProjectRecord> findAll() throws IOException;
}
