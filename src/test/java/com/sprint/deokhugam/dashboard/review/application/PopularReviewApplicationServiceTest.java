package com.sprint.deokhugam.dashboard.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewDto;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewSearchCondition;
import com.sprint.deokhugam.dashboard.review.application.dto.ReviewActivity;
import com.sprint.deokhugam.dashboard.review.application.required.PopularReviewPersistencePort;
import com.sprint.deokhugam.dashboard.review.application.required.PopularReviewSearchPort;
import com.sprint.deokhugam.dashboard.review.domain.PopularReview;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
public class PopularReviewApplicationServiceTest {

  private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 2);
  private static final Instant CREATED_AT = Instant.parse("2026-11-01T15:00:00Z");

  private static final PopularReviewSearchCondition CONDITION =
      new PopularReviewSearchCondition(
          PopularReviewPeriod.DAILY, Sort.Direction.DESC, null, null, 2);

  @Mock private PopularReviewSearchPort searchPort;
  @Mock private PopularReviewPersistencePort persistencePort;
  // 알림 기능이 추가됨
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PopularReviewApplicationService popularReviewApplicationService;

  // replaceAll로 저장하려던 순위 목록을 붙잡아 둔다.
  @Captor private ArgumentCaptor<List<PopularReview>> rankingsCaptor;

  private PopularReviewDto popularReviewDto(long rank) {
    return new PopularReviewDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "testTitle",
        null,
        UUID.randomUUID(),
        "testNickname",
        "testReviewContent",
        4.0,
        PopularReviewPeriod.DAILY,
        CREATED_AT,
        rank,
        1.0,
        1L,
        1L);
  }

  // 집계 후 DAILY 기간에 저장된 순위 목록을 꺼낸다
  private List<PopularReview> savedDailyRankings() {
    verify(persistencePort).replaceAll(eq(PopularReviewPeriod.DAILY), rankingsCaptor.capture());
    return rankingsCaptor.getValue();
  }

  @Test
  @DisplayName("인기 리뷰 조회 - limit보다 1개 더 오면 다음 페이지가 있고, 커서는 마지막 순위")
  void searchPopularReviews_hasNext() {
    // Given: limit는 2인데 3개가 옴
    given(searchPort.search(CONDITION))
        .willReturn(List.of(popularReviewDto(1), popularReviewDto(2), popularReviewDto(3)));
    given(searchPort.count(CONDITION)).willReturn(5L);

    // When
    CursorPageResponse<PopularReviewDto> result =
        popularReviewApplicationService.searchPopularReviewsByPagination(CONDITION);

    // Then
    assertThat(result.content()).hasSize(2);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo("2");
    assertThat(result.nextAfter()).isEqualTo(CREATED_AT);
    assertThat(result.totalElements()).isEqualTo(5L);
  }

  @Test
  @DisplayName("인기 리뷰 조회 - 마지막 페이지면 다음 커서가 없음")
  void searchPopularReviews_lastPage() {
    // Given: limit는 2인데 1개만 옴
    given(searchPort.search(CONDITION)).willReturn(List.of(popularReviewDto(1)));
    given(searchPort.count(CONDITION)).willReturn(1L);

    // When
    CursorPageResponse<PopularReviewDto> result =
        popularReviewApplicationService.searchPopularReviewsByPagination(CONDITION);

    // Then
    assertThat(result.content()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  @DisplayName("인기 리뷰 집계 - 요청한 기간만 집계해서 저장")
  void aggregatePopularReviews_onlyRequestedPeriod() {
    // Given
    given(searchPort.findReviewActivities(any())).willReturn(List.of());

    // When
    popularReviewApplicationService.aggregatePopularReviews(PopularReviewPeriod.WEEKLY, BASE_DATE);

    // Then
    verify(searchPort).findReviewActivities(PopularReviewPeriod.WEEKLY.rangeOf(BASE_DATE));
    verify(persistencePort).replaceAll(eq(PopularReviewPeriod.WEEKLY), anyList());
    verifyNoMoreInteractions(persistencePort);
  }

  @Test
  @DisplayName("인기 리뷰 집계 - 점수가 높은 순으로 1위부터 순위를 매김")
  void aggregatePopularReviews_rankByScore() {
    // Given
    ReviewActivity low = new ReviewActivity(UUID.randomUUID(), 1L, 0L); // 0.3점
    ReviewActivity high = new ReviewActivity(UUID.randomUUID(), 1L, 5L); // 3.8점
    ReviewActivity middle = new ReviewActivity(UUID.randomUUID(), 1L, 2L); // 1.7점
    given(searchPort.findReviewActivities(any())).willReturn(List.of(low, high, middle));

    // When
    popularReviewApplicationService.aggregatePopularReviews(PopularReviewPeriod.DAILY, BASE_DATE);

    // Then
    List<PopularReview> rankings = savedDailyRankings();
    assertThat(rankings.get(0).getReviewId()).isEqualTo(high.reviewId());
    assertThat(rankings.get(0).getRank()).isEqualTo(1L);
    assertThat(rankings.get(0).getScore()).isEqualTo(3.8);
    assertThat(rankings.get(1).getReviewId()).isEqualTo(middle.reviewId());
    assertThat(rankings.get(2).getReviewId()).isEqualTo(low.reviewId());
  }

  @Test
  @DisplayName("인기 리뷰 집계 - 점수가 같으면 댓글이 많은 리뷰가 앞 순위")
  void aggregatePopularReviews_sameScore_moreCommentsFirst() {
    // Given: 둘 다 2.1점 (좋아요 7개 = 2.1, 댓글 3개 = 2.1)
    ReviewActivity manyLikes = new ReviewActivity(UUID.randomUUID(), 7L, 0L);
    ReviewActivity manyComments = new ReviewActivity(UUID.randomUUID(), 0L, 3L);
    given(searchPort.findReviewActivities(any())).willReturn(List.of(manyLikes, manyComments));

    // When
    popularReviewApplicationService.aggregatePopularReviews(PopularReviewPeriod.DAILY, BASE_DATE);

    // Then
    List<PopularReview> rankings = savedDailyRankings();
    assertThat(rankings.get(0).getReviewId()).isEqualTo(manyComments.reviewId());
    assertThat(rankings.get(1).getReviewId()).isEqualTo(manyLikes.reviewId());
  }

  @Test
  @DisplayName("인기 리뷰 집계 - 좋아요와 댓글이 하나도 없는 리뷰는 순위에서 제외")
  void aggregatePopularReviews_excludeNoActivity() {
    // Given
    ReviewActivity active = new ReviewActivity(UUID.randomUUID(), 1L, 0L);
    ReviewActivity inactive = new ReviewActivity(UUID.randomUUID(), 0L, 0L);
    given(searchPort.findReviewActivities(any())).willReturn(List.of(active, inactive));

    // When
    popularReviewApplicationService.aggregatePopularReviews(PopularReviewPeriod.DAILY, BASE_DATE);

    // Then
    List<PopularReview> rankings = savedDailyRankings();
    assertThat(rankings).hasSize(1);
    assertThat(rankings.get(0).getReviewId()).isEqualTo(active.reviewId());
  }

  @Test
  @DisplayName("인기 리뷰 집계 - 기간별로 최대 20개까지만 저장")
  void aggregatePopularReviews_max20() {
    // Given: 활동 있는 리뷰 25개
    List<ReviewActivity> activities = new ArrayList<>();
    for (long commentCount = 1; commentCount <= 25; commentCount++) {
      activities.add(new ReviewActivity(UUID.randomUUID(), 0L, commentCount));
    }
    given(searchPort.findReviewActivities(any())).willReturn(activities);

    // When
    popularReviewApplicationService.aggregatePopularReviews(PopularReviewPeriod.DAILY, BASE_DATE);

    // Then: 댓글 25개짜리가 1위, 댓글 6개짜리가 20위
    List<PopularReview> rankings = savedDailyRankings();
    assertThat(rankings).hasSize(20);
    assertThat(rankings.get(0).getCommentCount()).isEqualTo(25L);
    assertThat(rankings.get(19).getCommentCount()).isEqualTo(6L);
  }
}
