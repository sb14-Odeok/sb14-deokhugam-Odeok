package com.sprint.deokhugam.dashboard.review.adapter.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.sprint.deokhugam.dashboard.review.application.provided.PopularReviewUseCase;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PopularReviewScoreSchedulerTest {

  @Mock private PopularReviewUseCase popularReviewUseCase;

  @InjectMocks private PopularReviewScoreScheduler popularReviewScoreScheduler;

  @Test
  @DisplayName("모든 기간을 각각 집계한다")
  void calculatePopularReviews_allPeriods() {
    // When
    popularReviewScoreScheduler.calculatePopularReviews();

    // Then
    for (PopularReviewPeriod period : PopularReviewPeriod.values()) {
      verify(popularReviewUseCase).aggregatePopularReviews(eq(period), any(LocalDate.class));
    }
  }

  @Test
  @DisplayName("한 기간이 실패해도 나머지 기간은 계속 집계한다")
  void calculatePopularReviews_continueAfterFailure() {
    // Given
    willThrow(new RuntimeException("집계 실패"))
        .given(popularReviewUseCase)
        .aggregatePopularReviews(eq(PopularReviewPeriod.DAILY), any(LocalDate.class));

    // When
    popularReviewScoreScheduler.calculatePopularReviews();

    // Then
    for (PopularReviewPeriod period : PopularReviewPeriod.values()) {
      verify(popularReviewUseCase).aggregatePopularReviews(eq(period), any(LocalDate.class));
    }
  }
}
