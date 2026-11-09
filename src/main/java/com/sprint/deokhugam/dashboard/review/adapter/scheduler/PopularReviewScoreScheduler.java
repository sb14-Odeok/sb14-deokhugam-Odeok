package com.sprint.deokhugam.dashboard.review.adapter.scheduler;

import com.sprint.deokhugam.dashboard.review.application.provided.PopularReviewUseCase;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularReviewScoreScheduler {

  private final PopularReviewUseCase popularReviewUseCase;

  // 매일 0시 0분 0초에 실행
  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void calculatePopularReviews() {
    LocalDate baseDate = LocalDate.now(PopularReviewPeriod.ZONE);

    // 기간마다 트랜잭션이 나뉘어서, 한 기간이 실패해도 나머지 기간은 저장
    for (PopularReviewPeriod period : PopularReviewPeriod.values()) {
      try {
        popularReviewUseCase.aggregatePopularReviews(period, baseDate);
      } catch (Exception e) {
        log.error("인기 리뷰 집계 실패 --- period = {}, baseDate = {}", period, baseDate, e);
      }
    }
  }
}
