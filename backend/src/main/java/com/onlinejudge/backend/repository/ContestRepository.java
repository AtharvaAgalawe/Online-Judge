package com.onlinejudge.backend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.onlinejudge.common.entity.Contest;

public interface ContestRepository extends JpaRepository<Contest, Long>, JpaSpecificationExecutor<Contest> {

    /** Candidates for the auto-publish job: public, ended, not yet processed. */
    List<Contest> findByIsPrivateFalseAndEndTimeLessThanEqualAndProblemsPublishedAtIsNull(Instant now);
}
