package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
        verify(testCaseRepository, times(1)).countByProblemIds(any());
        verify(testCaseRepository, never()).countByProblemId(anyLong());
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
