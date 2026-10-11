package com.onlinejudge.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.StandingsProblemCell;
import com.onlinejudge.backend.api.dto.StandingsResponse;
import com.onlinejudge.backend.api.dto.StandingsRow;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestParticipant;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.ContestStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * Standings aggregation (PRD §7.8): first AC per problem earns the problem's points, wrong
 * attempts before it add a 10-minute penalty each, ranking is points DESC / penalty ASC /
 * last-AC ASC with competition ranks, and the last {@code freezeMinutes} of a running contest
 * is hidden behind a snapshot cutoff. Computed in Java over the contest-window submissions and
 * cached for 15s (StandingsCache).
 */
@Service
@Transactional(readOnly = true)
public class StandingsServiceImpl implements StandingsService {

    private static final int WRONG_ATTEMPT_PENALTY_MINUTES = 10;

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final StandingsCache standingsCache;

    public StandingsServiceImpl(ContestRepository contestRepository,
                                ContestProblemRepository contestProblemRepository,
                                ContestParticipantRepository contestParticipantRepository,
                                SubmissionRepository submissionRepository,
                                UserRepository userRepository,
                                StandingsCache standingsCache) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.standingsCache = standingsCache;
    }

    @Override
    public StandingsResponse standings(Long contestId, String usernameOrNull) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found: " + contestId));
        User caller = usernameOrNull == null ? null
                : userRepository.findByUsername(usernameOrNull).orElse(null);
        boolean admin = caller != null && caller.getRoles().stream()
                .anyMatch(role -> AppRole.ROLE_ADMIN.name().equals(role.getName()));
        if (contest.isPrivate() && !admin) {
            throw new ResourceNotFoundException("Contest not found: " + contestId);
        }
        Optional<StandingsResponse> cached = standingsCache.get(contestId);
        if (cached.isPresent()) {
            return cached.get();
        }
        StandingsResponse computed = compute(contest);
        standingsCache.put(contestId, computed);
        return computed;
    }

    private StandingsResponse compute(Contest contest) {
        Instant now = Instant.now();
        ContestStatus status = ContestStatus.of(contest.getStartTime(), contest.getEndTime(), now);
        boolean freezeActive = contest.getFreezeMinutes() > 0 && status == ContestStatus.RUNNING
                && !now.isBefore(contest.getEndTime().minusSeconds(contest.getFreezeMinutes() * 60L));
        Instant frozenAt = freezeActive
                ? contest.getEndTime().minusSeconds(contest.getFreezeMinutes() * 60L)
                : null;
        Instant cutoff = freezeActive ? frozenAt : contest.getEndTime();

        List<ContestProblem> links = contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(contest.getId());
        List<ContestParticipant> participants = contestParticipantRepository.findByContestId(contest.getId());
        if (links.isEmpty() || participants.isEmpty()) {
            return new StandingsResponse(freezeActive, frozenAt, List.of());
        }
        List<Long> problemIds = links.stream().map(link -> link.getProblem().getId()).toList();
        Map<Long, Integer> pointsByProblem = links.stream().collect(Collectors.toMap(
                link -> link.getProblem().getId(), ContestProblem::getPoints));
        Map<Long, String> usernameByUser = participants.stream().collect(Collectors.toMap(
                participant -> participant.getUser().getId(),
                participant -> participant.getUser().getUsername()));
        List<Long> userIds = List.copyOf(usernameByUser.keySet());

        List<Submission> submissions = submissionRepository.findForStandings(
                problemIds, userIds, contest.getStartTime(), cutoff);
        Map<Long, List<Submission>> byUserAndProblem = submissions.stream().collect(Collectors.groupingBy(
                submission -> key(submission.getUser().getId(), submission.getProblem().getId())));

        List<Row> rows = new ArrayList<>();
        for (Long userId : userIds) {
            int totalPoints = 0;
            int penalty = 0;
            Instant lastAc = null;
            List<StandingsProblemCell> cells = new ArrayList<>();
            for (Long problemId : problemIds) {
                List<Submission> mine = byUserAndProblem.getOrDefault(key(userId, problemId), List.of()).stream()
                        .sorted(Comparator.comparing(Submission::getSubmittedAt))
                        .toList();
                Submission firstAc = mine.stream()
                        .filter(submission -> submission.getVerdict() == Verdict.ACCEPTED)
                        .findFirst().orElse(null);
                int wrongAttempts = 0;
                for (Submission submission : mine) {
                    if (firstAc != null && !submission.getSubmittedAt().isBefore(firstAc.getSubmittedAt())) {
                        break;
                    }
                    if (submission.getVerdict() != Verdict.ACCEPTED
                            && submission.getVerdict() != Verdict.SYSTEM_ERROR) {
                        wrongAttempts++;
                    }
                }
                if (firstAc != null) {
                    int minute = (int) Duration.between(contest.getStartTime(),
                            firstAc.getSubmittedAt()).toMinutes();
                    totalPoints += pointsByProblem.getOrDefault(problemId, 0);
                    penalty += minute + WRONG_ATTEMPT_PENALTY_MINUTES * wrongAttempts;
                    lastAc = lastAc == null || firstAc.getSubmittedAt().isAfter(lastAc)
                            ? firstAc.getSubmittedAt() : lastAc;
                    cells.add(new StandingsProblemCell(problemId, true, wrongAttempts, minute));
                } else {
                    cells.add(new StandingsProblemCell(problemId, false, wrongAttempts, null));
                }
            }
            rows.add(new Row(userId, usernameByUser.get(userId), totalPoints, penalty, lastAc, cells));
        }
        rows.sort(Comparator.comparingInt(Row::totalPoints).reversed()
                .thenComparingInt(Row::penalty)
                .thenComparing(row -> row.lastAc, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.userId));
        List<StandingsRow> ranked = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int rank = index + 1;
            if (index > 0) {
                Row previous = rows.get(index - 1);
                if (previous.totalPoints == row.totalPoints && previous.penalty == row.penalty
                        && Objects.equals(previous.lastAc, row.lastAc)) {
                    rank = ranked.get(index - 1).rank();
                }
            }
            ranked.add(new StandingsRow(rank, row.username, row.totalPoints, row.penalty, row.cells));
        }
        return new StandingsResponse(freezeActive, frozenAt, ranked);
    }

    private static long key(Long userId, Long problemId) {
        return userId * 1_000_000_007L + problemId;
    }

    private record Row(Long userId, String username, int totalPoints, int penalty, Instant lastAc,
                       List<StandingsProblemCell> cells) {
    }
}
