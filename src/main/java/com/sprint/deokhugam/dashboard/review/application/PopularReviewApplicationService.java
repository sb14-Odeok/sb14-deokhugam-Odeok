package com.sprint.deokhugam.dashboard.review.application;

import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewDto;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewSearchCondition;
import com.sprint.deokhugam.dashboard.review.application.dto.ReviewActivity;
import com.sprint.deokhugam.dashboard.review.application.provided.PopularReviewUseCase;
import com.sprint.deokhugam.dashboard.review.application.required.PopularReviewPersistencePort;
import com.sprint.deokhugam.dashboard.review.application.required.PopularReviewSearchPort;
import com.sprint.deokhugam.dashboard.review.domain.PopularReview;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.notification.domain.NotifyEvent;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopularReviewApplicationService implements PopularReviewUseCase {

  static final int MAX_RANK = 20;

  // 알림용 상수 추가
  static final int NOTIFICATION_LIMIT = 10;

  private final PopularReviewSearchPort searchPort;
  private final PopularReviewPersistencePort persistencePort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public CursorPageResponse<PopularReviewDto> searchPopularReviewsByPagination(
      PopularReviewSearchCondition condition) {
    List<PopularReviewDto> responses = searchPort.search(condition);
    long totalElements = searchPort.count(condition);

    return CursorPageResponse.of(
        responses,
        condition.limit(),
        PopularReviewDto::rank,
        PopularReviewDto::createdAt,
        totalElements);
  }

  @Override
  @Transactional
  public void aggregatePopularReviews(LocalDate baseDate) {
    // 모든 집계 종류별 계산
    for (PopularReviewPeriod period : PopularReviewPeriod.values()) {
      aggregate(period, baseDate);
    }
  }

  private void aggregate(PopularReviewPeriod period, LocalDate baseDate) {
    PopularReviewPeriod.Range range = period.rangeOf(baseDate);
    List<ReviewActivity> activities = searchPort.findReviewActivities(range);

    Set<UUID> previousIds =
        new HashSet<>(searchPort.findReviewsForNotify(period, NOTIFICATION_LIMIT));

    // 좋아요/댓글이 하나도 없는 리뷰는 빼고, 점수 높은 순, 같으면 댓글 많은 순
    List<ReviewActivity> top =
        // 데이터의 흐름
        activities.stream()
            .filter(activity -> scoreOf(activity) > 0)
            .sorted(
                Comparator.comparingDouble(this::scoreOf)
                    .reversed()
                    .thenComparing(ReviewActivity::commentCount, Comparator.reverseOrder()))
            .limit(MAX_RANK)
            .toList();

    List<PopularReview> rankings =
        // 인덱스 뽑기
        IntStream.range(0, top.size())
            // 숫자 형태의 데이터 스트림을 객체 스트림으로 변환
            .mapToObj(index -> toPopularReview(top.get(index), period, index + 1))
            .toList();

    List<PopularReview> currentReviews =
        rankings.stream()
            .limit(NOTIFICATION_LIMIT)
            .filter(ranking -> !previousIds.contains(ranking.getReviewId()))
            .toList();

    for (PopularReview review : currentReviews) {
      eventPublisher.publishEvent(
          NotifyEvent.ranked(review.getReviewId(), period, review.getRank()));
    }

    persistencePort.replaceAll(period, rankings);
  }

  private PopularReview toPopularReview(
      ReviewActivity activity, PopularReviewPeriod period, long rank) {
    return PopularReview.create(
        activity.reviewId(), period, rank, activity.likeCount(), activity.commentCount());
  }

  private double scoreOf(ReviewActivity activity) {
    return PopularReview.calculateScore(activity.likeCount(), activity.commentCount());
  }
}
