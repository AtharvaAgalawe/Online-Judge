package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.QueueStatusResponse;

/** Admin queue-health read (PRD §7.7); database-derived, not broker depth. */
public interface QueueStatusService {

    QueueStatusResponse current();
}
