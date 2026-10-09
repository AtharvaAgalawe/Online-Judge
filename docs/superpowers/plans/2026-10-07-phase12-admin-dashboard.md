# Phase 12 — Admin Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give admins a UI to author/publish problems, browse all submissions, and see queue health — backed by four new `ROLE_ADMIN` read endpoints.

**Architecture:** Backend adds thin admin controllers → services (authorization at controller **and** service) → repository Specifications/JPQL, reusing existing admin write API. Frontend adds a `RequireAdmin`-guarded `/admin` area reusing Phase 11 components. No schema migration, no new dependencies.

**Tech Stack:** Java 21 / Spring Boot 3.x / Spring Data JPA / PostgreSQL / Flyway (unchanged) · JUnit 5 + Mockito + Testcontainers · React 18 + TS + TanStack Query + react-router-dom (Phase 11).

**Spec:** `docs/superpowers/specs/2026-10-07-phase12-admin-dashboard-design.md`

## Global Constraints

- **No schema change** (migrations immutable, AGENTS §23). **No new dependencies** (AGENTS §17).
- All four new endpoints require `ROLE_ADMIN` at **controller** (`@PreAuthorize("hasRole('ADMIN')")`) and **service** layer. Unauthenticated → 401; authenticated non-admin → 403 (RFC 7807).
- Controllers thin; **controllers never call repositories**. DTOs (`record`) at the API boundary; never return entities.
- `@Transactional` boundaries on services. Admin list/detail mapping (lazy tags, createdBy) happens **inside** the service transaction (`open-in-view: false`).
- Queue-status is **DB-derived only** (`execution_jobs` + `submissions`); it must **not** claim to report RabbitMQ queue depth. State that in Javadoc/PRD.
- Hidden test-case content is returned only by admin endpoints (already guarded) and never on solver surfaces.
- Do **NOT** commit or push. The user commits themselves. Each task ends by staging and giving a suggested Conventional Commits message.
- Gates: backend → `.\mvnw.cmd -B verify` (unit + Checkstyle + SpotBugs), and `.\mvnw.cmd -B verify -P docker-tests` for integration tests. Frontend (workdir `frontend`) → `npm run lint && npm run test && npm run build`.
- Set `$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"` before Maven. Windows PowerShell 5.1 (no `&&`).

---

### Task 1: Backend — admin problem read endpoints (list + detail)

**Files:**
- Create: `backend/src/main/java/com/onlinejudge/backend/api/dto/AdminProblemSummaryResponse.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/api/dto/AdminProblemDetailResponse.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/repository/ProblemTestCaseCount.java`
- Modify: `backend/src/main/java/com/onlinejudge/backend/repository/ProblemSpecifications.java`
- Modify: `backend/src/main/java/com/onlinejudge/backend/repository/TestCaseRepository.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/service/AdminProblemQueryService.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/service/AdminProblemQueryServiceImpl.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/api/AdminProblemQueryController.java`
- Test: `backend/src/test/java/com/onlinejudge/backend/service/AdminProblemQueryServiceImplTest.java`

**Interfaces:**
- Consumes: `Problem`, `ProblemRepository` (`JpaSpecificationExecutor`), `TestCaseRepository`, `ProblemSpecifications`, `PagedResponse`, `ResourceNotFoundException`.
- Produces: `AdminProblemSummaryResponse.from(Problem, int)`, `AdminProblemDetailResponse.from(Problem, int)`, `AdminProblemQueryService.list(Boolean, String, int, int)` and `.get(Long)`.

- [ ] **Step 1: Write the failing service unit test**

`backend/src/test/java/com/onlinejudge/backend/service/AdminProblemQueryServiceImplTest.java`:

```java
package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.ProblemTestCaseCount;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.enums.Difficulty;

@ExtendWith(MockitoExtension.class)
class AdminProblemQueryServiceImplTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    private AdminProblemQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminProblemQueryServiceImpl(problemRepository, testCaseRepository);
    }

    @Test
    void listIncludesUnpublishedAndAttachesTestCaseCountsInOneQuery() {
        Problem published = problem(1L, true);
        Problem draft = problem(2L, false);
        when(problemRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(published, draft)));
        ProblemTestCaseCount count = count(1L, 3L);
        when(testCaseRepository.countByProblemIds(any())).thenReturn(List.of(count));

        var page = service.list(null, null, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).published()).isTrue();
        assertThat(page.content().get(0).testCaseCount()).isEqualTo(3);
        assertThat(page.content().get(1).published()).isFalse();
        assertThat(page.content().get(1).testCaseCount()).isZero();
    }

    @Test
    void getReturnsDetailWithCount() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem(1L, false)));
        when(testCaseRepository.countByProblemId(1L)).thenReturn(2L);

        var detail = service.get(1L);

        assertThat(detail.published()).isFalse();
        assertThat(detail.testCaseCount()).isEqualTo(2);
    }

    @Test
    void missingProblemIs404() {
        when(problemRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Problem problem(Long id, boolean published) {
        Problem problem = new Problem("slug-" + id, "Title " + id, "statement", Difficulty.EASY,
                1000, 65536, null);
        problem.setPublished(published);
        org.springframework.test.util.ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }

    private ProblemTestCaseCount count(Long problemId, long total) {
        return new ProblemTestCaseCount() {
            @Override
            public Long getProblemId() {
                return problemId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
```

- [ ] **Step 2: Run it and confirm it fails to compile (types missing)**

Run (repo root, after `$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"`): `.\mvnw.cmd -B -pl backend test -Dtest=AdminProblemQueryServiceImplTest`
Expected: FAIL — `AdminProblemQueryServiceImpl` / `ProblemTestCaseCount` cannot be found.

- [ ] **Step 3: Create the projection and response DTOs**

`backend/src/main/java/com/onlinejudge/backend/repository/ProblemTestCaseCount.java`:

```java
package com.onlinejudge.backend.repository;

/** Projection for a per-problem test-case count in one grouped query. */
public interface ProblemTestCaseCount {

    Long getProblemId();

    long getTotal();
}
```

`backend/src/main/java/com/onlinejudge/backend/api/dto/AdminProblemSummaryResponse.java`:

```java
package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

/** Admin list-row (PRD §7.7): unlike the public summary, includes unpublished problems. */
public record AdminProblemSummaryResponse(
        Long id,
        String slug,
        String title,
        Difficulty difficulty,
        boolean published,
        int testCaseCount,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminProblemSummaryResponse from(Problem problem, int testCaseCount) {
        return new AdminProblemSummaryResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDifficulty(), problem.isPublished(), testCaseCount,
                problem.getTags().stream().map(Tag::getName).sorted().toList(),
                problem.getCreatedAt(), problem.getUpdatedAt());
    }
}
```

`backend/src/main/java/com/onlinejudge/backend/api/dto/AdminProblemDetailResponse.java`:

```java
package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

/** Admin problem detail for editing (PRD §7.7): includes {@code published} and authorship. */
public record AdminProblemDetailResponse(
        Long id,
        String slug,
        String title,
        String statement,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitKb,
        boolean published,
        String createdBy,
        int testCaseCount,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminProblemDetailResponse from(Problem problem, int testCaseCount) {
        return new AdminProblemDetailResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getStatement(), problem.getDifficulty(), problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(), problem.isPublished(),
                problem.getCreatedBy() == null ? null : problem.getCreatedBy().getUsername(),
                testCaseCount,
                problem.getTags().stream().map(Tag::getName).sorted().toList(),
                problem.getCreatedAt(), problem.getUpdatedAt());
    }
}
```

- [ ] **Step 4: Add the specification and the batched count query**

Append to `ProblemSpecifications.java` (before the private `escapeLike`):

```java
    public static Specification<Problem> hasPublished(boolean published) {
        return (root, query, cb) -> cb.equal(root.get("published"), published);
    }
```

Add to `TestCaseRepository.java`:

```java
    @Query("""
            select t.problem.id as problemId, count(t) as total
            from TestCase t
            where t.problem.id in :problemIds
            group by t.problem.id
            """)
    List<ProblemTestCaseCount> countByProblemIds(Collection<Long> problemIds);
```

Add imports to `TestCaseRepository.java`:

```java
import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
```

- [ ] **Step 5: Implement the service and controller**

`backend/src/main/java/com/onlinejudge/backend/service/AdminProblemQueryService.java`:

```java
package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.AdminProblemDetailResponse;
import com.onlinejudge.backend.api.dto.AdminProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;

/** Admin read side of the problem catalog (PRD §7.7): includes unpublished problems. */
public interface AdminProblemQueryService {

    PagedResponse<AdminProblemSummaryResponse> list(Boolean published, String search, int page, int size);

    AdminProblemDetailResponse get(Long id);
}
```

`backend/src/main/java/com/onlinejudge/backend/service/AdminProblemQueryServiceImpl.java`:

```java
package com.onlinejudge.backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.AdminProblemDetailResponse;
import com.onlinejudge.backend.api.dto.AdminProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.ProblemSpecifications;
import com.onlinejudge.backend.repository.ProblemTestCaseCount;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.common.entity.Problem;

/**
 * Authorization is enforced here as well as at the controller (AGENTS.md §10 defense in
 * depth). The list is intentionally unfiltered by publication so admins can see drafts.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemQueryServiceImpl implements AdminProblemQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public AdminProblemQueryServiceImpl(ProblemRepository problemRepository, TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Override
    public PagedResponse<AdminProblemSummaryResponse> list(Boolean published, String search, int page, int size) {
        Specification<Problem> spec = (root, query, cb) -> cb.conjunction();
        if (published != null) {
            spec = spec.and(ProblemSpecifications.hasPublished(published));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(ProblemSpecifications.titleOrSlugContains(search));
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Page<Problem> problems = problemRepository.findAll(spec, pageRequest);
        Map<Long, Long> counts = countsFor(problems.getContent());
        return PagedResponse.of(problems,
                problem -> AdminProblemSummaryResponse.from(problem,
                        counts.getOrDefault(problem.getId(), 0L).intValue()));
    }

    @Override
    public AdminProblemDetailResponse get(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + id));
        return AdminProblemDetailResponse.from(problem, (int) testCaseRepository.countByProblemId(id));
    }

    private Map<Long, Long> countsFor(List<Problem> problems) {
        if (problems.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = problems.stream().map(Problem::getId).toList();
        return testCaseRepository.countByProblemIds(ids).stream()
                .collect(Collectors.toMap(ProblemTestCaseCount::getProblemId, ProblemTestCaseCount::getTotal,
                        (left, right) -> left));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
```

`backend/src/main/java/com/onlinejudge/backend/api/AdminProblemQueryController.java`:

```java
package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.AdminProblemDetailResponse;
import com.onlinejudge.backend.api.dto.AdminProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.service.AdminProblemQueryService;

/** Admin problem read endpoints (PRD §7.7, §15); includes unpublished problems. */
@RestController
@RequestMapping("/api/v1/admin/problems")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemQueryController {

    private final AdminProblemQueryService adminProblemQueryService;

    public AdminProblemQueryController(AdminProblemQueryService adminProblemQueryService) {
        this.adminProblemQueryService = adminProblemQueryService;
    }

    @GetMapping
    public PagedResponse<AdminProblemSummaryResponse> list(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminProblemQueryService.list(published, search, page, size);
    }

    @GetMapping("/{id}")
    public AdminProblemDetailResponse get(@PathVariable Long id) {
        return adminProblemQueryService.get(id);
    }
}
```

- [ ] **Step 6: Run the unit test to verify it passes**

Run: `.\mvnw.cmd -B -pl backend test -Dtest=AdminProblemQueryServiceImplTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Run the backend gate**

Run: `.\mvnw.cmd -B verify`
Expected: compiles, all unit tests pass, Checkstyle + SpotBugs clean.

- [ ] **Step 8: Stage (do not commit) and suggest a message**

```
git add backend/src
```
Suggested message: `feat(admin): add admin problem read endpoints (list includes unpublished)`

---

### Task 2: Backend — admin submissions list

**Files:**
- Create: `backend/src/main/java/com/onlinejudge/backend/repository/SubmissionSpecifications.java`
- Modify: `backend/src/main/java/com/onlinejudge/backend/repository/SubmissionRepository.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/service/AdminSubmissionService.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/service/AdminSubmissionServiceImpl.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/api/AdminSubmissionController.java`
- Test: `backend/src/test/java/com/onlinejudge/backend/service/AdminSubmissionServiceImplTest.java`

**Interfaces:**
- Consumes: `Submission`, `SubmissionSummaryResponse`, `SubmissionStatus`, `Verdict`, `PagedResponse`.
- Produces: `SubmissionRepository extends JpaRepository<Submission, Long>, JpaSpecificationExecutor<Submission>`; `AdminSubmissionService.list(Long, Long, SubmissionStatus, Verdict, int, int)`.

- [ ] **Step 1: Write the failing service unit test**

`backend/src/test/java/com/onlinejudge/backend/service/AdminSubmissionServiceImplTest.java`:

```java
package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class AdminSubmissionServiceImplTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private AdminSubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSubmissionServiceImpl(submissionRepository);
    }

    @Test
    void listMapsSubmissionsToSummariesAcrossAllUsers() {
        Submission submission = new Submission(new User("bob", "b@x.com", "h"),
                problem(), new Language("Java 21", "Main.java", "javac", "java", "oj-java21", java.math.BigDecimal.ONE),
                "class Main {}", null);
        when(submissionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(submission)));

        var page = service.list(null, null, SubmissionStatus.COMPLETED, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).problemSlug()).isEqualTo("slug");
    }

    private Problem problem() {
        return new Problem("slug", "Title", "statement", Difficulty.EASY, 1000, 65536, null);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails to compile**

Run: `.\mvnw.cmd -B -pl backend test -Dtest=AdminSubmissionServiceImplTest`
Expected: FAIL — `AdminSubmissionServiceImpl` cannot be found.

- [ ] **Step 3: Make `SubmissionRepository` specification-capable and add `SubmissionSpecifications`**

`SubmissionRepository.java` — change the type declaration:

```java
public interface SubmissionRepository extends JpaRepository<Submission, Long>, JpaSpecificationExecutor<Submission> {
```

Add import:

```java
import org.springframework.data.jpa.domain.Specification;
```
(Only if the compiler requires it; the interface needs `org.springframework.data.jpa.repository.JpaSpecificationExecutor`.)

`backend/src/main/java/com/onlinejudge/backend/repository/SubmissionSpecifications.java`:

```java
package com.onlinejudge.backend.repository;

import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Composable filters for the admin submissions browser (PRD §7.7, §15). */
public final class SubmissionSpecifications {

    private SubmissionSpecifications() {
    }

    public static Specification<Submission> hasProblem(Long problemId) {
        return (root, query, cb) -> cb.equal(root.get("problem").get("id"), problemId);
    }

    public static Specification<Submission> hasUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Submission> hasStatus(SubmissionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Submission> hasVerdict(Verdict verdict) {
        return (root, query, cb) -> cb.equal(root.get("verdict"), verdict);
    }
}
```

- [ ] **Step 4: Implement the service and controller**

`backend/src/main/java/com/onlinejudge/backend/service/AdminSubmissionService.java`:

```java
package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Admin read side across all users' submissions (PRD §7.7, §15). */
public interface AdminSubmissionService {

    PagedResponse<SubmissionSummaryResponse> list(Long problemId, Long userId, SubmissionStatus status,
                                                  Verdict verdict, int page, int size);
}
```

`backend/src/main/java/com/onlinejudge/backend/service/AdminSubmissionServiceImpl.java`:

```java
package com.onlinejudge.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.SubmissionSpecifications;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Authorization is enforced here as well as at the controller (AGENTS.md §10). */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SubmissionRepository submissionRepository;

    public AdminSubmissionServiceImpl(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Override
    public PagedResponse<SubmissionSummaryResponse> list(Long problemId, Long userId, SubmissionStatus status,
                                                         Verdict verdict, int page, int size) {
        Specification<Submission> spec = (root, query, cb) -> cb.conjunction();
        if (problemId != null) {
            spec = spec.and(SubmissionSpecifications.hasProblem(problemId));
        }
        if (userId != null) {
            spec = spec.and(SubmissionSpecifications.hasUser(userId));
        }
        if (status != null) {
            spec = spec.and(SubmissionSpecifications.hasStatus(status));
        }
        if (verdict != null) {
            spec = spec.and(SubmissionSpecifications.hasVerdict(verdict));
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "submittedAt", "id"));
        Page<Submission> submissions = submissionRepository.findAll(spec, pageRequest);
        return PagedResponse.of(submissions, SubmissionSummaryResponse::from);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
```

`backend/src/main/java/com/onlinejudge/backend/api/AdminSubmissionController.java`:

```java
package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.backend.service.AdminSubmissionService;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Admin submissions browser (PRD §7.7, §15): lists submissions across all users. */
@RestController
@RequestMapping("/api/v1/admin/submissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionController {

    private final AdminSubmissionService adminSubmissionService;

    public AdminSubmissionController(AdminSubmissionService adminSubmissionService) {
        this.adminSubmissionService = adminSubmissionService;
    }

    @GetMapping
    public PagedResponse<SubmissionSummaryResponse> list(
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) Verdict verdict,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminSubmissionService.list(problemId, userId, status, verdict, page, size);
    }
}
```

- [ ] **Step 5: Run the unit test to verify it passes**

Run: `.\mvnw.cmd -B -pl backend test -Dtest=AdminSubmissionServiceImplTest`
Expected: PASS.

- [ ] **Step 6: Run the backend gate**

Run: `.\mvnw.cmd -B verify`
Expected: passes.

- [ ] **Step 7: Stage (do not commit) and suggest a message**

```
git add backend/src
```
Suggested message: `feat(admin): add admin submissions browser endpoint`

---

### Task 3: Backend — queue-status endpoint

**Files:**
- Create: `backend/src/main/java/com/onlinejudge/backend/api/dto/QueueStatusResponse.java`
- Modify: `backend/src/main/java/com/onlinejudge/backend/repository/ExecutionJobRepository.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/service/QueueStatusService.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/service/QueueStatusServiceImpl.java`
- Create: `backend/src/main/java/com/onlinejudge/backend/api/AdminSystemController.java`
- Test: `backend/src/test/java/com/onlinejudge/backend/service/QueueStatusServiceImplTest.java`

**Interfaces:**
- Consumes: `ExecutionJob`, `SubmissionStatus`, `SubmissionStatus.isTerminal()`.
- Produces: `QueueStatusResponse` (+ nested `StaleJob`); `QueueStatusService.current()`.

- [ ] **Step 1: Write the failing service unit test (boundary: lease at/before now is stale)**

`backend/src/test/java/com/onlinejudge/backend/service/QueueStatusServiceImplTest.java`:

```java
package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class QueueStatusServiceImplTest {

    @Mock
    private ExecutionJobRepository executionJobRepository;

    private QueueStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QueueStatusServiceImpl(executionJobRepository);
    }

    @Test
    void reportsCountsAndStaleJobs() {
        when(executionJobRepository.countBySubmissionStatusIn(any())).thenReturn(4L);
        when(executionJobRepository.countActiveLeases(any(), any())).thenReturn(2L);
        when(executionJobRepository.countStaleLeases(any(), any())).thenReturn(1L);
        when(executionJobRepository.countRetried()).thenReturn(3L);
        when(executionJobRepository.findOldestQueuedCreatedAt(any()))
                .thenReturn(Instant.now().minusSeconds(30));
        ExecutionJob job = staleJob();
        when(executionJobRepository.findStaleLeases(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(job));

        var status = service.current();

        assertThat(status.queued()).isEqualTo(4);
        assertThat(status.activeLease()).isEqualTo(2);
        assertThat(status.staleLease()).isEqualTo(1);
        assertThat(status.retried()).isEqualTo(3);
        assertThat(status.oldestQueuedAgeSeconds()).isNotNull();
        assertThat(status.stale()).hasSize(1);
        assertThat(status.stale().get(0).lockedBy()).isEqualTo("worker-1");
        assertThat(status.stale().get(0).status()).isEqualTo(SubmissionStatus.RUNNING);
    }

    @Test
    void oldestAgeIsNullWhenNothingIsQueued() {
        when(executionJobRepository.findOldestQueuedCreatedAt(any())).thenReturn(null);
        when(executionJobRepository.findStaleLeases(any(), any(), any(Pageable.class))).thenReturn(List.of());

        var status = service.current();

        assertThat(status.oldestQueuedAgeSeconds()).isNull();
        assertThat(status.stale()).isEmpty();
    }

    private ExecutionJob staleJob() {
        User user = new User("u", "u@x.com", "h");
        Problem problem = new Problem("slug", "Title", "s", Difficulty.EASY, 1000, 65536, null);
        Submission submission = new Submission(user, problem,
                new Language("Java 21", "Main.java", "javac", "java", "oj-java21", java.math.BigDecimal.ONE),
                "code", null);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.RUNNING);
        ExecutionJob job = new ExecutionJob(submission);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "lockedBy", "worker-1");
        org.springframework.test.util.ReflectionTestUtils.setField(job, "lockedAt", Instant.now().minusSeconds(120));
        org.springframework.test.util.ReflectionTestUtils.setField(job, "leaseExpiresAt", Instant.now().minusSeconds(60));
        return job;
    }
}
```

> Note: `ExecutionJob` exposes no public lease-mutation method (the worker's `JobClaimService` performs the conditional UPDATE via its own repository). Setting the three private fields with `ReflectionTestUtils` in this unit fixture is therefore correct; do **not** add production setters just for the test.

- [ ] **Step 2: Run it and confirm it fails to compile**

Run: `.\mvnw.cmd -B -pl backend test -Dtest=QueueStatusServiceImplTest`
Expected: FAIL — `QueueStatusServiceImpl` / repository methods missing.

- [ ] **Step 3: Create the response DTO**

`backend/src/main/java/com/onlinejudge/backend/api/dto/QueueStatusResponse.java`:

```java
package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Admin queue-health snapshot (PRD §7.7). Derived solely from the database
 * ({@code execution_jobs} + {@code submissions}) — it is NOT the RabbitMQ broker's
 * queue depth (AGENTS.md §11/§14: do not oversell what is measured).
 */
public record QueueStatusResponse(
        long queued,
        long activeLease,
        long staleLease,
        long retried,
        Long oldestQueuedAgeSeconds,
        List<StaleJob> stale) {

    /** A job whose lease has expired while its submission is not terminal. */
    public record StaleJob(
            Long submissionId,
            SubmissionStatus status,
            String lockedBy,
            Instant leaseExpiresAt,
            int retryCount,
            long ageSeconds) {
    }
}
```

- [ ] **Step 4: Add the repository queries**

`ExecutionJobRepository.java` — add imports and methods:

```java
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.onlinejudge.common.enums.SubmissionStatus;
```

```java
    @Query("select count(j) from ExecutionJob j where j.submission.status in :statuses")
    long countBySubmissionStatusIn(@Param("statuses") Collection<SubmissionStatus> statuses);

    @Query("""
            select count(j) from ExecutionJob j
            where j.leaseExpiresAt > :now and j.submission.status not in :terminal
            """)
    long countActiveLeases(@Param("now") Instant now, @Param("terminal") Collection<SubmissionStatus> terminal);

    @Query("""
            select count(j) from ExecutionJob j
            where j.leaseExpiresAt <= :now and j.submission.status not in :terminal
            """)
    long countStaleLeases(@Param("now") Instant now, @Param("terminal") Collection<SubmissionStatus> terminal);

    @Query("select count(j) from ExecutionJob j where j.retryCount > 0")
    long countRetried();

    @Query("select min(j.createdAt) from ExecutionJob j where j.submission.status in :statuses")
    Instant findOldestQueuedCreatedAt(@Param("statuses") Collection<SubmissionStatus> statuses);

    @Query("""
            select j from ExecutionJob j
            where j.leaseExpiresAt <= :now and j.submission.status not in :terminal
            order by j.leaseExpiresAt asc
            """)
    List<ExecutionJob> findStaleLeases(@Param("now") Instant now,
                                       @Param("terminal") Collection<SubmissionStatus> terminal,
                                       Pageable pageable);
```

- [ ] **Step 5: Implement the service and controller**

`backend/src/main/java/com/onlinejudge/backend/service/QueueStatusService.java`:

```java
package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.QueueStatusResponse;

/** Admin queue-health read (PRD §7.7); database-derived, not broker depth. */
public interface QueueStatusService {

    QueueStatusResponse current();
}
```

`backend/src/main/java/com/onlinejudge/backend/service/QueueStatusServiceImpl.java`:

```java
package com.onlinejudge.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.QueueStatusResponse;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Authorization is enforced here as well as at the controller (AGENTS.md §10). Values are
 * derived from the lease table only; the broker's queue depth is deliberately not read.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class QueueStatusServiceImpl implements QueueStatusService {

    private static final int STALE_JOB_LIMIT = 50;
    private static final List<SubmissionStatus> QUEUED =
            List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.QUEUED);
    private static final List<SubmissionStatus> TERMINAL = Arrays.stream(SubmissionStatus.values())
            .filter(SubmissionStatus::isTerminal)
            .toList();

    private final ExecutionJobRepository executionJobRepository;

    public QueueStatusServiceImpl(ExecutionJobRepository executionJobRepository) {
        this.executionJobRepository = executionJobRepository;
    }

    @Override
    public QueueStatusResponse current() {
        Instant now = Instant.now();
        long queued = executionJobRepository.countBySubmissionStatusIn(QUEUED);
        long active = executionJobRepository.countActiveLeases(now, TERMINAL);
        long stale = executionJobRepository.countStaleLeases(now, TERMINAL);
        long retried = executionJobRepository.countRetried();
        Instant oldest = executionJobRepository.findOldestQueuedCreatedAt(QUEUED);
        Long oldestAge = oldest == null ? null : Duration.between(oldest, now).toSeconds();

        List<QueueStatusResponse.StaleJob> staleJobs = executionJobRepository
                .findStaleLeases(now, TERMINAL, PageRequest.of(0, STALE_JOB_LIMIT)).stream()
                .map(job -> toStaleJob(job, now))
                .toList();

        return new QueueStatusResponse(queued, active, stale, retried, oldestAge, staleJobs);
    }

    private static QueueStatusResponse.StaleJob toStaleJob(ExecutionJob job, Instant now) {
        return new QueueStatusResponse.StaleJob(
                job.getSubmission().getId(),
                job.getSubmission().getStatus(),
                job.getLockedBy(),
                job.getLeaseExpiresAt(),
                job.getRetryCount(),
                Duration.between(job.getLeaseExpiresAt(), now).toSeconds());
    }
}
```

`backend/src/main/java/com/onlinejudge/backend/api/AdminSystemController.java`:

```java
package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.QueueStatusResponse;
import com.onlinejudge.backend.service.QueueStatusService;

/** Admin system endpoints (PRD §7.7). */
@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final QueueStatusService queueStatusService;

    public AdminSystemController(QueueStatusService queueStatusService) {
        this.queueStatusService = queueStatusService;
    }

    @GetMapping("/queue-status")
    public QueueStatusResponse queueStatus() {
        return queueStatusService.current();
    }
}
```

- [ ] **Step 6: Run the unit test to verify it passes**

Run: `.\mvnw.cmd -B -pl backend test -Dtest=QueueStatusServiceImplTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Run the backend gate**

Run: `.\mvnw.cmd -B verify`
Expected: passes.

- [ ] **Step 8: Stage (do not commit) and suggest a message**

```
git add backend/src
```
Suggested message: `feat(admin): add DB-derived queue-status endpoint`

---

### Task 4: Backend — integration tests for all four admin endpoints

**Files:**
- Test: `backend/src/test/java/com/onlinejudge/backend/api/AdminReadEndpointsIntegrationTest.java`

**Interfaces:**
- Consumes: all four endpoints from Tasks 1–3; test helpers mirrored from `ProblemManagementIntegrationTest` (`adminToken`, `userToken`, `register`, `login`).
- Produces: nothing (test only).

- [ ] **Step 1: Write the integration test**

`backend/src/test/java/com/onlinejudge/backend/api/AdminReadEndpointsIntegrationTest.java`:

```java
package com.onlinejudge.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.User;

/** Phase 12 DoD: admin read endpoints are readable by admins and blocked for everyone else. */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminReadEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void adminCanListUnpublishedProblemsAndNonAdminCannot() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String user = userToken("usr" + stamp);
        String title = "Draft " + stamp;

        createProblem(admin, title);
        // Not published yet: visible to admin list, absent from the public list.
        mockMvc.perform(get("/api/v1/admin/problems").param("search", title)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].published").value(false))
                .andExpect(jsonPath("$.content[0].testCaseCount").value(0));
        mockMvc.perform(get("/api/v1/problems").param("search", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/problems").param("search", title)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/problems").param("search", title))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanFetchProblemDetailIncludingPublishedFlag() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        JsonNode created = createProblem(admin, "Detail " + stamp);
        long id = created.get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/problems/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.createdBy").isNotEmpty());
    }

    @Test
    void adminSubmissionsBrowserListsAllAndIsListable() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);

        mockMvc.perform(get("/api/v1/admin/submissions").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void queueStatusReturnsCountersAndIsAdminOnly() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String user = userToken("usr" + stamp);

        mockMvc.perform(get("/api/v1/admin/system/queue-status").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").isNumber())
                .andExpect(jsonPath("$.activeLease").isNumber())
                .andExpect(jsonPath("$.staleLease").isNumber())
                .andExpect(jsonPath("$.stale").isArray());

        mockMvc.perform(get("/api/v1/admin/system/queue-status").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private JsonNode createProblem(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title, "statement", "s", "difficulty", "EASY"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String adminToken(String username) throws Exception {
        register(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.grantRole(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        userRepository.saveAndFlush(user);
        return login(username);
    }

    private String userToken(String username) throws Exception {
        register(username);
        return login(username);
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "email", username + "@example.com",
                                "password", "password123"))))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run the integration test**

Run (Docker running): `.\mvnw.cmd -B verify -P docker-tests -Dtest=AdminReadEndpointsIntegrationTest`
Expected: PASS (4 tests).

- [ ] **Step 3: Run the full Docker test profile**

Run: `.\mvnw.cmd -B verify -P docker-tests`
Expected: all backend + worker tests pass.

- [ ] **Step 4: Stage (do not commit) and suggest a message**

```
git add backend/src
```
Suggested message: `test(admin): cover admin read endpoints with Testcontainers`

---

### Task 5: Frontend — admin guard, API module, problem list + create

**Files:**
- Create: `frontend/src/auth/RequireAdmin.tsx`
- Create: `frontend/src/api/admin.ts`
- Create: `frontend/src/pages/admin/AdminProblemListPage.tsx`
- Create: `frontend/src/pages/admin/AdminProblemCreatePage.tsx`
- Test: `frontend/src/auth/RequireAdmin.test.tsx`
- Test: `frontend/src/pages/admin/AdminProblemCreatePage.test.tsx`
- Modify: `frontend/src/components/NavBar.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/index.css`

**Interfaces:**
- Consumes: `apiFetch` (Phase 11), `useAuth`, `PagedResponse`, `Difficulty`, `Spinner`, `ErrorBanner`, `Pagination`.
- Produces: `RequireAdmin`; `api/admin.ts` functions/types listed below; routes `/admin/problems` and `/admin/problems/new`.

- [ ] **Step 1: Write the failing `RequireAdmin` test**

`frontend/src/auth/RequireAdmin.test.tsx`:

```tsx
import { describe, expect, it, vi, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { RequireAdmin } from './RequireAdmin'
import { renderWithProviders } from '../test/renderWithProviders'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function tokenWithRoles(roles: string[]): string {
  return `h.${encodeSegment({ sub: 'u', uid: 1, roles })}.s`
}

describe('RequireAdmin', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('blocks a non-admin', async () => {
    localStorage.setItem('oj.refreshToken', 'r')
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ accessToken: tokenWithRoles(['ROLE_USER']), expiresIn: 900 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin" element={<RequireAdmin><div>admin content</div></RequireAdmin>} />
        </Routes>
      </AuthProvider>,
      { route: '/admin' },
    )
    await waitFor(() => expect(screen.getByText(/forbidden/i)).toBeInTheDocument())
  })

  it('admits an admin', async () => {
    localStorage.setItem('oj.refreshToken', 'r')
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ accessToken: tokenWithRoles(['ROLE_ADMIN']), expiresIn: 900 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin" element={<RequireAdmin><div>admin content</div></RequireAdmin>} />
        </Routes>
      </AuthProvider>,
      { route: '/admin' },
    )
    await waitFor(() => expect(screen.getByText('admin content')).toBeInTheDocument())
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run (workdir `frontend`): `npm run test -- src/auth/RequireAdmin.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `RequireAdmin` and `api/admin.ts`**

`frontend/src/auth/RequireAdmin.tsx`:

```tsx
import type { ReactElement } from 'react'
import { useAuth } from './AuthContext'

export function RequireAdmin({ children }: { children: ReactElement }) {
  const { user } = useAuth()
  if (!user?.roles.includes('ROLE_ADMIN')) {
    return (
      <section>
        <h1>Forbidden</h1>
        <p>This area is for administrators only.</p>
      </section>
    )
  }
  return children
}
```

`frontend/src/api/admin.ts`:

```ts
import { apiFetch } from './client'
import type {
  Difficulty,
  PagedResponse,
  SubmissionStatus,
  SubmissionSummary,
  Verdict,
} from './types'

export interface AdminProblemSummary {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
  published: boolean
  testCaseCount: number
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminProblemDetail {
  id: number
  slug: string
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs: number
  memoryLimitKb: number
  published: boolean
  createdBy: string | null
  testCaseCount: number
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminProblemFilters {
  published?: boolean
  search?: string
  page?: number
  size?: number
}

export interface CreateProblemBody {
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs?: number
  memoryLimitKb?: number
  tags?: string[]
}

export interface UpdateProblemBody {
  title?: string
  statement?: string
  difficulty?: Difficulty
  timeLimitMs?: number
  memoryLimitKb?: number
  tags?: string[]
  published?: boolean
}

export interface TestCase {
  id: number
  input: string
  expectedOutput: string
  isSample: boolean
  order: number
  points: number
}

export interface TestCaseInput {
  input: string
  expectedOutput: string
  isSample?: boolean
  order?: number
  points?: number
}

export interface AdminSubmissionFilters {
  problemId?: number
  userId?: number
  status?: SubmissionStatus
  verdict?: Verdict
  page?: number
  size?: number
}

export interface StaleJob {
  submissionId: number
  status: SubmissionStatus
  lockedBy: string | null
  leaseExpiresAt: string
  retryCount: number
  ageSeconds: number
}

export interface QueueStatus {
  queued: number
  activeLease: number
  staleLease: number
  retried: number
  oldestQueuedAgeSeconds: number | null
  stale: StaleJob[]
}

function pagedQuery(params: Record<string, string | number | boolean | undefined>): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  return query.toString()
}

export function fetchAdminProblems(filters: AdminProblemFilters): Promise<PagedResponse<AdminProblemSummary>> {
  return apiFetch(`/api/v1/admin/problems?${pagedQuery({ ...filters })}`)
}

export function fetchAdminProblem(id: number): Promise<AdminProblemDetail> {
  return apiFetch(`/api/v1/admin/problems/${id}`)
}

export function createProblem(body: CreateProblemBody): Promise<{ id: number; slug: string }> {
  return apiFetch('/api/v1/admin/problems', { method: 'POST', body: JSON.stringify(body) })
}

export function updateProblem(id: number, body: UpdateProblemBody): Promise<AdminProblemDetail> {
  return apiFetch(`/api/v1/admin/problems/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function fetchTestCases(problemId: number): Promise<TestCase[]> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases`)
}

export function addTestCase(problemId: number, body: TestCaseInput): Promise<TestCase> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateTestCase(
  problemId: number,
  testCaseId: number,
  body: Partial<TestCaseInput>,
): Promise<TestCase> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases/${testCaseId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function deleteTestCase(problemId: number, testCaseId: number): Promise<void> {
  return apiFetch(`/api/v1/admin/problems/${problemId}/test-cases/${testCaseId}`, {
    method: 'DELETE',
  })
}

export function fetchAdminSubmissions(
  filters: AdminSubmissionFilters,
): Promise<PagedResponse<SubmissionSummary>> {
  return apiFetch(`/api/v1/admin/submissions?${pagedQuery({ ...filters })}`)
}

export function fetchQueueStatus(): Promise<QueueStatus> {
  return apiFetch('/api/v1/admin/system/queue-status')
}
```

- [ ] **Step 4: Run the guard test to verify it passes**

Run: `npm run test -- src/auth/RequireAdmin.test.tsx`
Expected: PASS (2 tests).

- [ ] **Step 5: Write the failing create-page test**

`frontend/src/pages/admin/AdminProblemCreatePage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminProblemCreatePage } from './AdminProblemCreatePage'
import { renderWithProviders } from '../../test/renderWithProviders'

describe('AdminProblemCreatePage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('posts the problem and navigates to its edit page', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('/admin/problems') && init?.method === 'POST') {
        return new Response(JSON.stringify({ id: 5, slug: 'new-problem' }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin/problems/new" element={<AdminProblemCreatePage />} />
          <Route path="/admin/problems/:id" element={<div>edit page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/admin/problems/new' },
    )

    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'New Problem' } })
    fireEvent.change(screen.getByLabelText('Statement'), { target: { value: 'Statement body' } })
    fireEvent.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => expect(screen.getByText('edit page')).toBeInTheDocument())
    const post = fetchMock.mock.calls.find((call) => call[1]?.method === 'POST')
    expect(post).toBeTruthy()
    expect(JSON.parse(String(post![1]!.body))).toMatchObject({ title: 'New Problem' })
  })
})
```

- [ ] **Step 6: Run it to verify it fails**

Run: `npm run test -- src/pages/admin/AdminProblemCreatePage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 7: Implement the list and create pages; wire NavBar, routes, styles**

`frontend/src/pages/admin/AdminProblemListPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAdminProblems } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Pagination } from '../../components/Pagination'
import { Spinner } from '../../components/Spinner'

export function AdminProblemListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const [search, setSearch] = useState(params.get('search') ?? '')

  const filters = useMemo(
    () => ({
      search: params.get('search') ?? undefined,
      published: params.get('published') ? params.get('published') === 'true' : undefined,
      page: Number(params.get('page') ?? '0'),
      size: 20,
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['adminProblems', filters],
    queryFn: () => fetchAdminProblems(filters),
  })

  function apply(patch: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(patch)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    setParams(next)
  }

  return (
    <section>
      <h1>Admin · Problems</h1>
      <div className="filters">
        <label>
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label>
          Published
          <select value={params.get('published') ?? ''}
                  onChange={(e) => apply({ published: e.target.value, page: '0' })}>
            <option value="">All</option>
            <option value="true">Published</option>
            <option value="false">Draft</option>
          </select>
        </label>
        <button type="button" onClick={() => apply({ search, page: '0' })}>Search</button>
        <Link to="/admin/problems/new">New problem</Link>
      </div>

      {query.isPending && <Spinner label="Loading problems" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && (
        <>
          <table className="problem-table">
            <thead>
              <tr><th>Title</th><th>Difficulty</th><th>Status</th><th>Tests</th><th>Updated</th></tr>
            </thead>
            <tbody>
              {query.data.content.map((problem) => (
                <tr key={problem.id}>
                  <td>
                    <button type="button" className="linklike"
                            onClick={() => navigate(`/admin/problems/${problem.id}`)}>
                      {problem.title}
                    </button>
                  </td>
                  <td>{problem.difficulty}</td>
                  <td>{problem.published ? 'Published' : 'Draft'}</td>
                  <td>{problem.testCaseCount}</td>
                  <td>{new Date(problem.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={filters.page} totalPages={query.data.totalPages}
                      onPageChange={(page) => apply({ page: String(page) })} />
        </>
      )}
    </section>
  )
}
```

`frontend/src/pages/admin/AdminProblemCreatePage.tsx`:

```tsx
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { createProblem } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import type { Difficulty } from '../../api/types'

export function AdminProblemCreatePage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [statement, setStatement] = useState('')
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY')
  const [tags, setTags] = useState('')

  const mutation = useMutation({
    mutationFn: () =>
      createProblem({
        title,
        statement,
        difficulty,
        tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
      }),
    onSuccess: (created) => navigate(`/admin/problems/${created.id}`),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <section>
      <h1>New problem</h1>
      {mutation.isError && <ErrorBanner error={mutation.error} />}
      <form onSubmit={onSubmit}>
        <label>Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
        </label>
        <label>Statement
          <textarea value={statement} onChange={(e) => setStatement(e.target.value)} required rows={8} />
        </label>
        <label>Difficulty
          <select value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </label>
        <label>Tags (comma-separated)
          <input value={tags} onChange={(e) => setTags(e.target.value)} />
        </label>
        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating…' : 'Create'}
        </button>
      </form>
    </section>
  )
}
```

`frontend/src/components/NavBar.tsx` — after the `My Submissions` link, add (inside the authenticated branch):

```tsx
import { useAuth } from '../auth/AuthContext'
```
Already imported. Add:

```tsx
      {status === 'authenticated' && user?.roles.includes('ROLE_ADMIN') && (
        <Link to="/admin/problems">Admin</Link>
      )}
```

`frontend/src/App.tsx` — add imports and routes (inside the `Layout` route group):

```tsx
import { RequireAdmin } from './auth/RequireAdmin'
import { AdminProblemListPage } from './pages/admin/AdminProblemListPage'
import { AdminProblemCreatePage } from './pages/admin/AdminProblemCreatePage'
```

```tsx
        <Route
          path="admin/problems"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminProblemListPage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
        <Route
          path="admin/problems/new"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminProblemCreatePage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
```

Append to `frontend/src/index.css`:

```css
textarea {
  display: block;
  width: 100%;
  max-width: 640px;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-text);
  font-family: var(--font-mono);
}
```

- [ ] **Step 8: Run the tests and the full gate**

Run: `npm run test -- src/auth/RequireAdmin.test.tsx src/pages/admin/AdminProblemCreatePage.test.tsx && npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 9: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add admin guard, problem list, and create page`

---

### Task 6: Frontend — admin problem edit (metadata, test cases, publish)

**Files:**
- Create: `frontend/src/pages/admin/AdminProblemEditPage.tsx`
- Create: `frontend/src/components/admin/TestCaseEditor.tsx`
- Test: `frontend/src/pages/admin/AdminProblemEditPage.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `fetchAdminProblem`, `updateProblem`, `fetchTestCases`, `addTestCase`, `updateTestCase`, `deleteTestCase` (Task 5).
- Produces: `TestCaseEditor({ problemId, testCases })`; route `/admin/problems/:id`.

- [ ] **Step 1: Write the failing edit-page publish test**

`frontend/src/pages/admin/AdminProblemEditPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminProblemEditPage } from './AdminProblemEditPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const DETAIL = {
  id: 5, slug: 'two-sum', title: 'Two Sum', statement: 'Add.', difficulty: 'EASY',
  timeLimitMs: 2000, memoryLimitKb: 262144, published: false, createdBy: 'admin',
  testCaseCount: 1, tags: ['arrays'], createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('AdminProblemEditPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('publishes via PUT with published=true', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (init?.method === 'PUT') {
        return new Response(JSON.stringify({ ...DETAIL, published: true }), {
          status: 200, headers: { 'Content-Type': 'application/json' },
        })
      }
      if (url.includes('/test-cases')) {
        return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify(DETAIL), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/admin/problems/:id" element={<AdminProblemEditPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/admin/problems/5' },
    )

    fireEvent.click(await screen.findByRole('button', { name: /publish/i }))
    await waitFor(() => {
      const put = fetchMock.mock.calls.find((call) => call[1]?.method === 'PUT')
      expect(put).toBeTruthy()
      expect(JSON.parse(String(put![1]!.body))).toEqual({ published: true })
    })
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- src/pages/admin/AdminProblemEditPage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `TestCaseEditor` and `AdminProblemEditPage`**

`frontend/src/components/admin/TestCaseEditor.tsx`:

```tsx
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { addTestCase, deleteTestCase } from '../../api/admin'
import type { TestCase, TestCaseInput } from '../../api/admin'
import { ErrorBanner } from '../ErrorBanner'

export function TestCaseEditor({ problemId, testCases }: { problemId: number; testCases: TestCase[] }) {
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [expectedOutput, setExpectedOutput] = useState('')
  const [isSample, setIsSample] = useState(false)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['adminTestCases', problemId] })
    queryClient.invalidateQueries({ queryKey: ['adminProblem', problemId] })
  }

  const add = useMutation({
    mutationFn: (body: TestCaseInput) => addTestCase(problemId, body),
    onSuccess: () => {
      setInput('')
      setExpectedOutput('')
      setIsSample(false)
      invalidate()
    },
  })

  const remove = useMutation({
    mutationFn: (testCaseId: number) => deleteTestCase(problemId, testCaseId),
    onSuccess: invalidate,
  })

  return (
    <div>
      <table className="problem-table">
        <thead>
          <tr><th>#</th><th>Sample</th><th>Input</th><th>Expected</th><th /></tr>
        </thead>
        <tbody>
          {testCases.map((testCase) => (
            <tr key={testCase.id}>
              <td>{testCase.order}</td>
              <td>{testCase.isSample ? 'yes' : 'no'}</td>
              <td><code>{testCase.input}</code></td>
              <td><code>{testCase.expectedOutput}</code></td>
              <td>
                <button type="button" onClick={() => remove.mutate(testCase.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {(add.isError || remove.isError) && <ErrorBanner error={add.error ?? remove.error} />}

      <form onSubmit={(event) => {
        event.preventDefault()
        add.mutate({ input, expectedOutput, isSample, order: testCases.length, points: 1 })
      }}>
        <label>Input
          <textarea value={input} onChange={(e) => setInput(e.target.value)} required rows={3} />
        </label>
        <label>Expected output
          <textarea value={expectedOutput} onChange={(e) => setExpectedOutput(e.target.value)} required rows={3} />
        </label>
        <label>
          <input type="checkbox" checked={isSample} onChange={(e) => setIsSample(e.target.checked)} />
          {' '}Sample (visible to solvers)
        </label>
        <button type="submit" disabled={add.isPending}>Add test case</button>
      </form>
    </div>
  )
}
```

`frontend/src/pages/admin/AdminProblemEditPage.tsx`:

```tsx
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchAdminProblem, fetchTestCases, updateProblem } from '../../api/admin'
import type { Difficulty, UpdateProblemBody } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Spinner } from '../../components/Spinner'
import { TestCaseEditor } from '../../components/admin/TestCaseEditor'

export function AdminProblemEditPage() {
  const { id = '' } = useParams()
  const problemId = Number(id)
  const queryClient = useQueryClient()

  const detail = useQuery({ queryKey: ['adminProblem', problemId], queryFn: () => fetchAdminProblem(problemId) })
  const testCases = useQuery({ queryKey: ['adminTestCases', problemId], queryFn: () => fetchTestCases(problemId) })

  const [title, setTitle] = useState('')
  const [statement, setStatement] = useState('')
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY')
  const [timeLimitMs, setTimeLimitMs] = useState(2000)
  const [memoryLimitKb, setMemoryLimitKb] = useState(262144)
  const [tags, setTags] = useState('')

  useEffect(() => {
    if (detail.data) {
      setTitle(detail.data.title)
      setStatement(detail.data.statement)
      setDifficulty(detail.data.difficulty)
      setTimeLimitMs(detail.data.timeLimitMs)
      setMemoryLimitKb(detail.data.memoryLimitKb)
      setTags(detail.data.tags.join(', '))
    }
  }, [detail.data])

  const mutation = useMutation({
    mutationFn: (body: UpdateProblemBody) => updateProblem(problemId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminProblem', problemId] })
      queryClient.invalidateQueries({ queryKey: ['adminProblems'] })
      queryClient.invalidateQueries({ queryKey: ['problem', detail.data?.slug] })
      queryClient.invalidateQueries({ queryKey: ['problems'] })
    },
  })

  if (detail.isPending) return <Spinner label="Loading problem" />
  if (detail.isError) return <ErrorBanner error={detail.error} />
  const problem = detail.data

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({
      title,
      statement,
      difficulty,
      timeLimitMs,
      memoryLimitKb,
      tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
    })
  }

  return (
    <section>
      <h1>Edit · {problem.title}</h1>
      <p>
        Status: <strong>{problem.published ? 'Published' : 'Draft'}</strong> ·{' '}
        <Link to={`/problems/${problem.slug}`}>public view</Link>
      </p>
      {mutation.isError && <ErrorBanner error={mutation.error} />}
      <button type="button" onClick={() => mutation.mutate({ published: !problem.published })}>
        {problem.published ? 'Unpublish' : 'Publish'}
      </button>

      <h2>Metadata</h2>
      <form onSubmit={onSubmit}>
        <label>Title<input value={title} onChange={(e) => setTitle(e.target.value)} required /></label>
        <label>Statement<textarea value={statement} onChange={(e) => setStatement(e.target.value)} rows={8} required /></label>
        <label>Difficulty
          <select value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </label>
        <label>Time limit (ms)<input type="number" value={timeLimitMs} onChange={(e) => setTimeLimitMs(Number(e.target.value))} /></label>
        <label>Memory limit (KB)<input type="number" value={memoryLimitKb} onChange={(e) => setMemoryLimitKb(Number(e.target.value))} /></label>
        <label>Tags<input value={tags} onChange={(e) => setTags(e.target.value)} /></label>
        <button type="submit" disabled={mutation.isPending}>Save metadata</button>
      </form>

      <h2>Test cases</h2>
      {testCases.isPending && <Spinner label="Loading test cases" />}
      {testCases.isError && <ErrorBanner error={testCases.error} />}
      {testCases.isSuccess && <TestCaseEditor problemId={problemId} testCases={testCases.data} />}
    </section>
  )
}
```

- [ ] **Step 4: Wire the route**

In `frontend/src/App.tsx`, add the import and route:

```tsx
import { AdminProblemEditPage } from './pages/admin/AdminProblemEditPage'
```

```tsx
        <Route
          path="admin/problems/:id"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminProblemEditPage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
```

> Route ordering: react-router v6/v7 ranks `admin/problems/new` (static) above `admin/problems/:id`, so no shadowing.

- [ ] **Step 5: Run the tests and the full gate**

Run: `npm run test -- src/pages/admin/AdminProblemEditPage.test.tsx && npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 6: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add admin problem edit with test-case authoring and publish toggle`

---

### Task 7: Frontend — submissions browser + queue-status view

**Files:**
- Create: `frontend/src/pages/admin/AdminSubmissionsPage.tsx`
- Test: `frontend/src/pages/admin/AdminSubmissionsPage.test.tsx`
- Create: `frontend/src/pages/admin/AdminQueueStatusPage.tsx`
- Test: `frontend/src/pages/admin/AdminQueueStatusPage.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `fetchAdminSubmissions`, `fetchQueueStatus` (Task 5); `VerdictBadge`, `Pagination`, `Spinner`, `ErrorBanner`.
- Produces: routes `/admin/submissions`, `/admin/queue`.

- [ ] **Step 1: Write the failing submissions-page test**

`frontend/src/pages/admin/AdminSubmissionsPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminSubmissionsPage } from './AdminSubmissionsPage'
import { renderWithProviders } from '../../test/renderWithProviders'

const PAGE = {
  content: [{
    id: 9, problemId: 1, problemSlug: 'two-sum', languageName: 'Python 3.12',
    status: 'COMPLETED', verdict: 'ACCEPTED', timeUsedMs: 10, memoryUsedKb: 2048,
    submittedAt: '2026-01-01T00:00:00Z',
  }],
  page: 0, size: 20, totalElements: 1, totalPages: 1,
}

describe('AdminSubmissionsPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders submissions across users', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify(PAGE), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes><Route path="/admin/submissions" element={<AdminSubmissionsPage />} /></Routes>
      </AuthProvider>,
      { route: '/admin/submissions' },
    )
    expect(await screen.findByText('two-sum')).toBeInTheDocument()
    expect(screen.getByText('Accepted')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- src/pages/admin/AdminSubmissionsPage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement both pages**

`frontend/src/pages/admin/AdminSubmissionsPage.tsx`:

```tsx
import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAdminSubmissions } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Pagination } from '../../components/Pagination'
import { Spinner } from '../../components/Spinner'
import { VerdictBadge } from '../../components/VerdictBadge'
import type { SubmissionStatus, Verdict } from '../../api/types'

const STATUSES: SubmissionStatus[] = [
  'SUBMITTED', 'QUEUED', 'PICKED_UP', 'COMPILING', 'RUNNING', 'EVALUATING',
  'COMPLETED', 'COMPILATION_ERROR', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR', 'SYSTEM_ERROR',
]
const VERDICTS: Verdict[] = [
  'ACCEPTED', 'WRONG_ANSWER', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED',
  'COMPILATION_ERROR', 'RUNTIME_ERROR', 'SYSTEM_ERROR',
]

export function AdminSubmissionsPage() {
  const [params, setParams] = useSearchParams()
  const filters = useMemo(
    () => ({
      problemId: params.get('problemId') ? Number(params.get('problemId')) : undefined,
      userId: params.get('userId') ? Number(params.get('userId')) : undefined,
      status: (params.get('status') as SubmissionStatus | null) ?? undefined,
      verdict: (params.get('verdict') as Verdict | null) ?? undefined,
      page: Number(params.get('page') ?? '0'),
      size: 20,
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['adminSubmissions', filters],
    queryFn: () => fetchAdminSubmissions(filters),
  })

  function apply(patch: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(patch)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    setParams(next)
  }

  return (
    <section>
      <h1>Admin · Submissions</h1>
      <div className="filters">
        <label>Problem ID
          <input value={params.get('problemId') ?? ''}
                 onChange={(e) => apply({ problemId: e.target.value, page: '0' })} />
        </label>
        <label>User ID
          <input value={params.get('userId') ?? ''}
                 onChange={(e) => apply({ userId: e.target.value, page: '0' })} />
        </label>
        <label>Status
          <select value={params.get('status') ?? ''} onChange={(e) => apply({ status: e.target.value, page: '0' })}>
            <option value="">All</option>
            {STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
          </select>
        </label>
        <label>Verdict
          <select value={params.get('verdict') ?? ''} onChange={(e) => apply({ verdict: e.target.value, page: '0' })}>
            <option value="">All</option>
            {VERDICTS.map((verdict) => <option key={verdict} value={verdict}>{verdict}</option>)}
          </select>
        </label>
      </div>

      {query.isPending && <Spinner label="Loading submissions" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No submissions found.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <table className="problem-table">
            <thead><tr><th>#</th><th>Problem</th><th>Language</th><th>Verdict</th><th>Time</th></tr></thead>
            <tbody>
              {query.data.content.map((submission) => (
                <tr key={submission.id}>
                  <td><Link to={`/submissions/${submission.id}`}>{submission.id}</Link></td>
                  <td>{submission.problemSlug}</td>
                  <td>{submission.languageName}</td>
                  <td><VerdictBadge verdict={submission.verdict} /></td>
                  <td>{submission.timeUsedMs ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={filters.page} totalPages={query.data.totalPages}
                      onPageChange={(page) => apply({ page: String(page) })} />
        </>
      )}
    </section>
  )
}
```

`frontend/src/pages/admin/AdminQueueStatusPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query'
import { fetchQueueStatus } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Spinner } from '../../components/Spinner'

export function AdminQueueStatusPage() {
  const query = useQuery({
    queryKey: ['adminQueueStatus'],
    queryFn: fetchQueueStatus,
    refetchInterval: 5000,
  })

  if (query.isPending) return <Spinner label="Loading queue status" />
  if (query.isError) return <ErrorBanner error={query.error} />

  const status = query.data
  return (
    <section>
      <h1>Admin · Queue</h1>
      <p className="problem-meta">Database-derived lease state (not the broker queue depth).</p>
      <ul className="counter-list">
        <li>Queued: {status.queued}</li>
        <li>Active leases: {status.activeLease}</li>
        <li>Stale leases: {status.staleLease}</li>
        <li>Retried jobs: {status.retried}</li>
        <li>Oldest queued: {status.oldestQueuedAgeSeconds === null ? '—' : `${status.oldestQueuedAgeSeconds}s`}</li>
      </ul>

      <h2>Stale jobs</h2>
      {status.stale.length === 0 ? (
        <p>No stale jobs.</p>
      ) : (
        <table className="problem-table">
          <thead><tr><th>Submission</th><th>Status</th><th>Worker</th><th>Overdue (s)</th><th>Retries</th></tr></thead>
          <tbody>
            {status.stale.map((job) => (
              <tr key={job.submissionId}>
                <td>{job.submissionId}</td>
                <td>{job.status}</td>
                <td>{job.lockedBy ?? '—'}</td>
                <td>{job.ageSeconds}</td>
                <td>{job.retryCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
```

- [ ] **Step 4: Write the failing queue-page test**

`frontend/src/pages/admin/AdminQueueStatusPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { AdminQueueStatusPage } from './AdminQueueStatusPage'
import { renderWithProviders } from '../../test/renderWithProviders'

describe('AdminQueueStatusPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders counters and stale jobs', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({
        queued: 3, activeLease: 1, staleLease: 1, retried: 2, oldestQueuedAgeSeconds: 12,
        stale: [{ submissionId: 77, status: 'RUNNING', lockedBy: 'w-1', leaseExpiresAt: '2026-01-01T00:00:00Z', retryCount: 1, ageSeconds: 45 }],
      }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    renderWithProviders(
      <AuthProvider>
        <Routes><Route path="/admin/queue" element={<AdminQueueStatusPage />} /></Routes>
      </AuthProvider>,
      { route: '/admin/queue' },
    )
    expect(await screen.findByText(/Queued: 3/)).toBeInTheDocument()
    expect(screen.getByText('77')).toBeInTheDocument()
  })
})
```

- [ ] **Step 5: Run both tests to verify they pass**

Run: `npm run test -- src/pages/admin`
Expected: PASS.

- [ ] **Step 6: Wire the routes and styles**

In `frontend/src/App.tsx`, add imports and routes:

```tsx
import { AdminSubmissionsPage } from './pages/admin/AdminSubmissionsPage'
import { AdminQueueStatusPage } from './pages/admin/AdminQueueStatusPage'
```

```tsx
        <Route path="admin/submissions" element={
          <RequireAuth><RequireAdmin><AdminSubmissionsPage /></RequireAdmin></RequireAuth>
        } />
        <Route path="admin/queue" element={
          <RequireAuth><RequireAdmin><AdminQueueStatusPage /></RequireAdmin></RequireAuth>
        } />
```

Append to `frontend/src/index.css`:

```css
.counter-list {
  display: flex;
  gap: var(--space-4);
  list-style: none;
  padding: 0;
  flex-wrap: wrap;
}
```

- [ ] **Step 7: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 8: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add admin submissions browser and queue-status view`

---

### Task 8: Live verification + docs

**Files:**
- Create: `frontend/scripts/verify-phase12-live.mjs`
- Modify: `PRD.md` (§7.7 / Phase 12)
- Modify: `README.md`
- Modify: `C:\Users\Atharva\AppData\Local\Temp\opencode\verify-phase11.ps1` → copy to `verify-phase12.ps1` with the admin seeding step added.

**Interfaces:**
- Consumes: the running backend at `BASE_URL` (default `http://localhost:8080`) with an admin user provisioned by the wrapper (`ADMIN_USER` env).
- Produces: exit 0 on success, non-zero on first failed step; `PASS`/`FAIL` per step.

- [ ] **Step 1: Create the admin journey script**

`frontend/scripts/verify-phase12-live.mjs`:

```js
// Exercises the admin Phase 12 flow against a running backend. The wrapper must
// provision ADMIN_USER (registered + ROLE_ADMIN) before invoking this.
// Usage: BASE_URL=http://localhost:8080 ADMIN_USER=admin123 node scripts/verify-phase12-live.mjs
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'
const ADMIN_USER = process.env.ADMIN_USER
const PASSWORD = 'secret123'

let failed = false
const pass = (msg) => console.log(`PASS: ${msg}`)
const fail = (msg, error) => { failed = true; console.error(`FAIL: ${msg} -> ${error}`) }

async function step(name, fn) {
  try {
    const detail = await fn()
    pass(name + (detail ? ` (${detail})` : ''))
    return detail
  } catch (error) {
    fail(name, error?.message ?? error)
    throw error
  }
}

async function json(path, init) {
  const response = await fetch(`${BASE_URL}${path}`, init)
  const text = await response.text()
  const body = text ? JSON.parse(text) : undefined
  if (!response.ok) throw new Error(`${response.status} ${body?.title ?? ''} ${body?.detail ?? ''}`)
  return body
}

function auth(token, extra = {}) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...extra }
}

async function main() {
  if (!ADMIN_USER) throw new Error('ADMIN_USER env is required (wrapper provisions it)')
  const tokens = await step('admin login', () => json('/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ADMIN_USER, password: PASSWORD }),
  }))
  const admin = tokens.accessToken

  const regularName = `phase12u${Date.now()}`
  const regular = await step('register regular user', async () => {
    await json('/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: regularName, email: `${regularName}@example.com`, password: PASSWORD }) })
    const r = await json('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: regularName, password: PASSWORD }) })
    return r.accessToken
  })

  await step('non-admin gets 403 on admin list', async () => {
    const response = await fetch(`${BASE_URL}/api/v1/admin/problems`, { headers: { Authorization: `Bearer ${regular}` } })
    if (response.status !== 403) throw new Error(`expected 403, got ${response.status}`)
    return '403'
  })

  const stamp = Date.now()
  const created = await step('create problem', () => json('/api/v1/admin/problems', auth(admin, {
    method: 'POST', body: JSON.stringify({ title: `Phase12 ${stamp}`, statement: 's', difficulty: 'EASY' }),
  })))

  await step('add hidden test case', () => json(`/api/v1/admin/problems/${created.id}/test-cases`, auth(admin, {
    method: 'POST', body: JSON.stringify({ input: '1', expectedOutput: '1', isSample: false }),
  })))

  await step('add sample test case', () => json(`/api/v1/admin/problems/${created.id}/test-cases`, auth(admin, {
    method: 'POST', body: JSON.stringify({ input: '2', expectedOutput: '2', isSample: true }),
  })))

  await step('publish', () => json(`/api/v1/admin/problems/${created.id}`, auth(admin, {
    method: 'PUT', body: JSON.stringify({ published: true }),
  })))

  await step('admin list shows published problem', async () => {
    const page = await json(`/api/v1/admin/problems?search=Phase12%20${stamp}`, auth(admin))
    if (!page.content?.some((p) => p.id === created.id && p.published)) throw new Error('not found / not published')
    return `${page.totalElements} row(s)`
  })

  await step('admin detail shows published', async () => {
    const detail = await json(`/api/v1/admin/problems/${created.id}`, auth(admin))
    if (detail.published !== true) throw new Error('published flag false')
    return detail.slug
  })

  await step('public list shows published problem', async () => {
    const page = await json(`/api/v1/problems?search=Phase12%20${stamp}`)
    if (!page.content?.some((p) => p.id === created.id)) throw new Error('absent from public list')
    return 'visible'
  })

  await step('admin submissions browser', () => json('/api/v1/admin/submissions?page=0&size=5', auth(admin)))

  await step('queue status', async () => {
    const status = await json('/api/v1/admin/system/queue-status', auth(admin))
    if (typeof status.queued !== 'number' || !Array.isArray(status.stale)) throw new Error('bad shape')
    return `queued=${status.queued}`
  })

  if (failed) {
    console.error('\nRESULT: FAIL')
    process.exit(1)
  }
  console.log('\nRESULT: PASS')
}

main().catch(() => {
  console.error('\nRESULT: FAIL')
  process.exit(1)
})
```

- [ ] **Step 2: Build the wrapper with admin provisioning**

Copy `C:\Users\Atharva\AppData\Local\Temp\opencode\verify-phase11.ps1` to `verify-phase12.ps1`. After the backend is up and before running the node script:
1. Register the admin through the API (`POST /api/v1/auth/register` with username `phase12admin<timestamp>`, password `secret123`).
2. Promote it in Postgres: `docker exec <pg> psql -U onlinejudge -d onlinejudge -c "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = '<name>' AND r.name = 'ROLE_ADMIN' ON CONFLICT DO NOTHING;"` (read `V1__init_schema.sql`/`V3__seed_roles.sql` to confirm table/column names before finalizing).
3. Run `node frontend/scripts/verify-phase12-live.mjs` with `BASE_URL=http://localhost:8080` and `ADMIN_USER=<name>`.
4. Print `PHASE 12 LIVE: PASS`/`FAIL`; keep the existing try/finally teardown.

- [ ] **Step 3: Run the live verification**

Run: `powershell -ExecutionPolicy Bypass -File C:\Users\Atharva\AppData\Local\Temp\opencode\verify-phase12.ps1`
Expected: final line `PHASE 12 LIVE: PASS`, no `FAIL:` lines.

- [ ] **Step 4: Update `PRD.md`**

In §7.7 and the Phase 12 section: mark Complete; record the four new admin read endpoints; note that queue-status is **database-derived (lease state), not RabbitMQ broker depth**; note the admin area is `ROLE_ADMIN`-guarded client-side for UX and enforced server-side.

- [ ] **Step 5: Update `README.md`**

Document the admin area: routes under `/admin`, how to grant the admin role locally, and `node scripts/verify-phase12-live.mjs` (with `BASE_URL` / `ADMIN_USER`).

- [ ] **Step 6: Run both gates one last time**

Run (repo root): `.\mvnw.cmd -B verify` then `.\mvnw.cmd -B verify -P docker-tests`
Run (workdir `frontend`): `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 7: Stage (do not commit) and suggest a message**

```
git add frontend PRD.md README.md backend/src
```
Suggested message: `test(admin): add Phase 12 live verification and update docs`

---

## Self-Review

**Spec coverage:**
- §4.1 four endpoints → Tasks 1 (two problem), 2 (submissions), 3 (queue) + Task 4 integration. §4.2 queue derivation → Task 3. §4.3 layering → Tasks 1–3.
- §5.1 guarding → Task 5. §5.2 routes → Tasks 5–7. §5.3 module structure → Tasks 5–7. §5.4 invalidation → Tasks 6. §5.5 test-case UX → Task 6.
- §6 error handling → Phase 11 `ErrorBanner` reused throughout. §7 security → controller+service `@PreAuthorize` (Tasks 1–3) + Task 4 401/403 tests.
- §8.1 backend tests → Tasks 1–4. §8.2 frontend tests → Tasks 5–7. §8.3 live → Task 8.
- §9 docs → Task 8. No schema change; no new deps.

**Placeholder scan:** No "TBD"/vague steps. Two environment-dependent items are explicit: the `ExecutionJob` fixture must use the real claim API (Task 3 Step 1 note), and the psql grant must be confirmed against the migration (Task 8 Step 2).

**Type consistency:** `AdminProblemQueryService.list(Boolean, String, int, int)`, `AdminSubmissionService.list(Long, Long, SubmissionStatus, Verdict, int, int)`, `QueueStatusService.current()`, the `api/admin.ts` function names (`fetchAdminProblems`, `fetchAdminProblem`, `createProblem`, `updateProblem`, `fetchTestCases`, `addTestCase`, `deleteTestCase`, `fetchAdminSubmissions`, `fetchQueueStatus`), and the response record field names are defined once and referenced consistently across tasks.
