package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.onlinejudge.backend.api.dto.CreateProblemRequest;
import com.onlinejudge.backend.api.dto.CreateTestCaseRequest;
import com.onlinejudge.backend.api.dto.UpdateProblemRequest;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ResourceInUseException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.TagRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;

@ExtendWith(MockitoExtension.class)
class ProblemAdminServiceImplTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserRepository userRepository;

    private ProblemAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProblemAdminServiceImpl(problemRepository, testCaseRepository, tagRepository, userRepository);
    }

    @Test
    void createGeneratesCollisionFreeSlugAndAppliesDefaults() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User("admin", "a@x.com", "h")));
        when(problemRepository.existsBySlug("two-sum")).thenReturn(true);
        when(problemRepository.existsBySlug("two-sum-2")).thenReturn(false);
        when(tagRepository.findByNameIgnoreCase("arrays")).thenReturn(Optional.of(new Tag("arrays")));
        when(tagRepository.findByNameIgnoreCase("math")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateProblemRequest("Two Sum", "Statement", Difficulty.EASY,
                null, null, List.of("arrays", "Arrays", "math")), "admin");

        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).saveAndFlush(saved.capture());
        Problem problem = saved.getValue();
        assertThat(response.slug()).isEqualTo("two-sum-2");
        assertThat(problem.getTimeLimitMs()).isEqualTo(2000);
        assertThat(problem.getMemoryLimitKb()).isEqualTo(262_144);
        assertThat(problem.getTags()).extracting(Tag::getName).containsExactlyInAnyOrder("arrays", "math");
        assertThat(problem.isPublished()).isFalse();
    }

    @Test
    void publishingWithoutTestCasesIsRejected() {
        Problem problem = unpublishedProblem(1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.countByProblemId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.update(1L, new UpdateProblemRequest(null, null, null, null, null,
                null, true)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(problem.isPublished()).isFalse();
    }

    @Test
    void publishingWithTestCasesSucceeds() {
        Problem problem = unpublishedProblem(1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.countByProblemId(1L)).thenReturn(3L);

        service.update(1L, new UpdateProblemRequest(null, null, null, null, null, null, true));

        assertThat(problem.isPublished()).isTrue();
    }

    @Test
    void partialUpdateLeavesOmittedFieldsUntouched() {
        Problem problem = unpublishedProblem(1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        service.update(1L, new UpdateProblemRequest("Renamed", null, null, null, null, null, null));

        assertThat(problem.getTitle()).isEqualTo("Renamed");
        assertThat(problem.getStatement()).isEqualTo("statement");
        assertThat(problem.getTimeLimitMs()).isEqualTo(1000);
    }

    @Test
    void unpublishIsSoftDelete() {
        Problem problem = unpublishedProblem(1L);
        problem.setPublished(true);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        service.unpublish(1L);

        assertThat(problem.isPublished()).isFalse();
        verify(problemRepository).save(problem);
    }

    @Test
    void missingProblemIs404() {
        when(problemRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unpublish(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addTestCaseAppliesDefaults() {
        Problem problem = unpublishedProblem(1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.saveAndFlush(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.addTestCase(1L, new CreateTestCaseRequest("in", "out", null, null, null));

        assertThat(response.isSample()).isFalse();
        assertThat(response.order()).isZero();
        assertThat(response.points()).isEqualTo(1);
    }

    @Test
    void testCaseOfAnotherProblemIs404() {
        Problem owner = persistedProblem(1L);
        TestCase testCase = new TestCase("in", "out", false, 0, 1);
        owner.addTestCase(testCase);
        when(testCaseRepository.findById(5L)).thenReturn(Optional.of(testCase));

        assertThatThrownBy(() -> service.deleteTestCase(2L, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingTestcaseWithJudgedHistoryIsConflict() {
        Problem owner = persistedProblem(1L);
        TestCase testCase = new TestCase("in", "out", false, 0, 1);
        owner.addTestCase(testCase);
        when(testCaseRepository.findById(5L)).thenReturn(Optional.of(testCase));
        doThrow(new DataIntegrityViolationException("fk")).when(testCaseRepository).flush();

        assertThatThrownBy(() -> service.deleteTestCase(1L, 5L))
                .isInstanceOf(ResourceInUseException.class);
    }

    private Problem unpublishedProblem(Long id) {
        return new Problem("slug-" + id, "Title " + id, "statement", Difficulty.EASY, 1000, 65536, null);
    }

    /** Test fixture with a database-assigned id, as the unit under test would see it. */
    private Problem persistedProblem(Long id) {
        Problem problem = unpublishedProblem(id);
        org.springframework.test.util.ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }
}
