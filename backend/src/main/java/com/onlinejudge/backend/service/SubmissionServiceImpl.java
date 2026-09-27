package com.onlinejudge.backend.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.CreateSubmissionRequest;
import com.onlinejudge.backend.api.dto.SubmissionAcceptedResponse;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.IdempotencyConflictException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Submission-creation orchestration: rate limit, Idempotency-Key handling, then the
 * transactional persist (PRD §7.2). Not itself transactional — the Redis reservation must
 * bracket the database transaction, not run inside it.
 */
@Service
public class SubmissionServiceImpl implements SubmissionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionCreator submissionCreator;
    private final IdempotencyStore idempotencyStore;
    private final SubmissionRateLimiter rateLimiter;

    public SubmissionServiceImpl(UserRepository userRepository, SubmissionRepository submissionRepository,
                                 SubmissionCreator submissionCreator, IdempotencyStore idempotencyStore,
                                 SubmissionRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.submissionCreator = submissionCreator;
        this.idempotencyStore = idempotencyStore;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public SubmissionAcceptedResponse create(String username, CreateSubmissionRequest request,
                                             String idempotencyKey) {
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessRuleException(
                    "Idempotency-Key must not exceed %d characters".formatted(MAX_IDEMPOTENCY_KEY_LENGTH));
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        Long userId = user.getId();

        // Fast path: a completed replay must not consume the rate limit or create anything.
        if (idempotencyKey != null) {
            Optional<Long> existing = idempotencyStore.findCompleted(userId, idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), userId);
            }
        }

        rateLimiter.acquire(userId);

        if (idempotencyKey != null && !idempotencyStore.reserve(userId, idempotencyKey)) {
            // Lost the reservation race: either the other request finished (replay it) or
            // it is still in flight (the client should retry with the same key).
            Optional<Long> existing = idempotencyStore.findCompleted(userId, idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), userId);
            }
            throw new IdempotencyConflictException(
                    "A submission with this Idempotency-Key is already being processed");
        }

        try {
            String correlationId = UUID.randomUUID().toString();
            Submission submission = submissionCreator.create(userId, request.problemId(), request.languageId(),
                    request.sourceCode(), idempotencyKey, correlationId);
            if (idempotencyKey != null) {
                idempotencyStore.complete(userId, idempotencyKey, submission.getId());
            }
            return new SubmissionAcceptedResponse(submission.getId(), submission.getStatus());
        } catch (DataIntegrityViolationException e) {
            // The database unique constraint on idempotency_key fired (e.g. Redis was down
            // while a duplicate retried). If the key belongs to this user, replay it.
            if (idempotencyKey != null) {
                idempotencyStore.release(userId, idempotencyKey);
                Optional<Submission> existing = submissionRepository
                        .findByIdempotencyKeyAndUserId(idempotencyKey, userId);
                if (existing.isPresent()) {
                    return toResponse(existing.get());
                }
            }
            throw e;
        } catch (RuntimeException e) {
            if (idempotencyKey != null) {
                idempotencyStore.release(userId, idempotencyKey);
            }
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markQueued(Long submissionId) {
        submissionRepository.findById(submissionId)
                .ifPresent(submission -> submission.transitionTo(SubmissionStatus.QUEUED));
    }

    private SubmissionAcceptedResponse replay(Long submissionId, Long userId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IdempotencyConflictException(
                        "Idempotency key refers to an unavailable submission"));
        if (!submission.getUser().getId().equals(userId)) {
            throw new IdempotencyConflictException("Idempotency key is already in use");
        }
        return toResponse(submission);
    }

    private static SubmissionAcceptedResponse toResponse(Submission submission) {
        return new SubmissionAcceptedResponse(submission.getId(), submission.getStatus());
    }
}
