package com.joblivo.job.exception;

import com.joblivo.job.model.JobSource;

/**
 * Thrown when multiple {@link com.joblivo.job.ingestion.JobSourceAdapter} implementations
 * attempt to register for the same {@link JobSource}.
 */
public class DuplicateJobSourceAdapterException extends JobException {

    public DuplicateJobSourceAdapterException(JobSource source, Class<?> newClass, Class<?> existingClass) {
        super(String.format("Duplicate JobSourceAdapter registration for source '%s': '%s' conflicts with already registered '%s'",
                source, newClass.getName(), existingClass.getName()));
    }
}
