package com.onlinejudge.backend.service;

import com.onlinejudge.common.dto.SubmissionJobMessage;

/**
 * Domain event published inside the submission-creation transaction. The message payload
 * is built there (all data is loaded in the transaction); the transport send happens only
 * after commit (PRD §12).
 */
public record SubmissionCreatedEvent(SubmissionJobMessage message, String correlationId) {
}
