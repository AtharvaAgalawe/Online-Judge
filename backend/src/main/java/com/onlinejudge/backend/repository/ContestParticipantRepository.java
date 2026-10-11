package com.onlinejudge.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.ContestParticipant;
import com.onlinejudge.common.entity.ContestParticipantId;

public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, ContestParticipantId> {

    boolean existsByContestIdAndUserId(Long contestId, Long userId);

    List<ContestParticipant> findByContestId(Long contestId);

    long countByContestId(Long contestId);
}
