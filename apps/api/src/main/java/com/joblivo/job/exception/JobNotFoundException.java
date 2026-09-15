package com.joblivo.job.exception;

import com.joblivo.job.model.JobSource;

import java.util.UUID;

/**
 * Thrown when a requested job entity is not found by ID or by source and external ID.
 */
public class JobNotFoundException extends JobException {

    public JobNotFoundException(UUID id) {
        super("Job not found with id: " + id);
    }

    public JobNotFoundException(JobSource source, String externalJobId) {
        super("Job not found for source '" + source + "' and externalJobId: '" + externalJobId + "'");
    }
}
