package com.onlinejudge.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.common.entity.Contest;

/** Runs the contest auto-publish pass on a fixed delay (PRD §7.8). */
@Component
public class ContestProblemPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContestProblemPublisher.class);

    private final ContestRepository contestRepository;
    private final ContestPublicationService contestPublicationService;

    public ContestProblemPublisher(ContestRepository contestRepository,
                                   ContestPublicationService contestPublicationService) {
        this.contestRepository = contestRepository;
        this.contestPublicationService = contestPublicationService;
    }

    /**
     * Publishes problems of public contests that have ended (PRD §7.8). Runs on a fixed
     * delay; failures are isolated per contest and retried on the next tick because the
     * marker is only set after a successful pass.
     */
    @Scheduled(fixedDelayString = "${oj.contests.publish-delay-ms:60000}")
    public void publishEndedContests() {
        for (Contest contest : contestRepository
                .findByIsPrivateFalseAndEndTimeLessThanEqualAndProblemsPublishedAtIsNull(Instant.now())) {
            try {
                contestPublicationService.publishContest(contest.getId());
            } catch (RuntimeException e) {
                log.error("Failed to publish problems for contest {}: {}", contest.getId(), e.getMessage(), e);
            }
        }
    }
}
