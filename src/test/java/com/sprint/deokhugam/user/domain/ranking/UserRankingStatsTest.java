package com.sprint.deokhugam.user.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.user.domain.ranking.Period.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 정상 마감과 달력 경계 검증. 보류 중인 누락일 복구와 당일 마감 정책은 고정하지 않는다. */
class UserRankingStatsTest {
  private static final UserActivityMetrics METRICS = new UserActivityMetrics(1, 2, 3, 4);

  private UserRankingStats accumulated(LocalDate date) {
    UserRankingStats empty = UserRankingStats.empty(date);
    PeriodStats current =
        new PeriodStats(
            new Daily(date, METRICS), new Weekly(date, METRICS),
            new Monthly(date, METRICS), new AllTime(METRICS));
    return new UserRankingStats(current, empty.published(), null);
  }

  @Test
  void 초기_통계는_오늘과_이전_완료_기간을_구분한다() {
    LocalDate today = LocalDate.of(2026, 11, 4);
    UserRankingStats stats = UserRankingStats.empty(today);
    assertThat(stats.current().daily().date()).isEqualTo(today);
    assertThat(stats.current().weekly().week()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(stats.current().monthly().month()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(stats.published().daily().date()).isEqualTo(today.minusDays(1));
    assertThat(stats.published().weekly().week()).isEqualTo(LocalDate.of(2026, 10, 26));
    assertThat(stats.published().monthly().month()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(stats.lastPublished()).isNull();
  }

  @Test
  void 일간_마감은_어제를_공개하고_오늘_일간만_초기화한다() {
    LocalDate yesterday = LocalDate.of(2026, 11, 4);
    UserRankingStats original = accumulated(yesterday);
    UserRankingStats closed = original.close(yesterday.plusDays(1));
    assertThat(closed.published().daily().metrics()).isEqualTo(METRICS);
    assertThat(closed.current().daily().metrics()).isEqualTo(UserActivityMetrics.EMPTY);
    assertThat(closed.current().weekly()).isEqualTo(original.current().weekly());
    assertThat(closed.current().monthly()).isEqualTo(original.current().monthly());
    assertThat(closed.published().allTime().metrics()).isEqualTo(METRICS);
    assertThat(closed.current().allTime().metrics()).isEqualTo(METRICS);
    assertThat(closed.lastPublished()).isEqualTo(yesterday.plusDays(1));
    assertThat(original.current().daily().metrics()).isEqualTo(METRICS);
  }

  @Test
  void 월요일에는_지난주를_공개하고_이번주를_초기화한다() {
    UserRankingStats closed =
        accumulated(LocalDate.of(2026, 11, 1)).close(LocalDate.of(2026, 11, 2));
    assertThat(closed.published().weekly().week()).isEqualTo(LocalDate.of(2026, 10, 26));
    assertThat(closed.published().weekly().metrics()).isEqualTo(METRICS);
    assertThat(closed.current().weekly().week()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(closed.current().weekly().metrics()).isEqualTo(UserActivityMetrics.EMPTY);
    assertThat(closed.current().monthly().metrics()).isEqualTo(METRICS);
  }

  @Test
  void 매월_1일에는_지난달을_공개하고_이번달을_초기화한다() {
    UserRankingStats closed =
        accumulated(LocalDate.of(2026, 10, 31)).close(LocalDate.of(2026, 11, 1));
    assertThat(closed.published().monthly().month()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(closed.published().monthly().metrics()).isEqualTo(METRICS);
    assertThat(closed.current().monthly().month()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(closed.current().monthly().metrics()).isEqualTo(UserActivityMetrics.EMPTY);
    assertThat(closed.current().weekly().metrics()).isEqualTo(METRICS);
  }

  @Test
  void 월요일이면서_1일이면_주간과_월간을_함께_마감한다() {
    UserRankingStats closed =
        accumulated(LocalDate.of(2026, 5, 31)).close(LocalDate.of(2026, 6, 1));
    assertThat(closed.published().daily().metrics()).isEqualTo(METRICS);
    assertThat(closed.published().weekly().metrics()).isEqualTo(METRICS);
    assertThat(closed.published().monthly().metrics()).isEqualTo(METRICS);
    assertThat(closed.current().weekly().metrics()).isEqualTo(UserActivityMetrics.EMPTY);
    assertThat(closed.current().monthly().metrics()).isEqualTo(UserActivityMetrics.EMPTY);
  }

  @Test
  void 이미_완료한_날짜의_재마감은_통계를_변경하지_않는다() {
    LocalDate today = LocalDate.of(2026, 11, 5);
    UserRankingStats closed = accumulated(today.minusDays(1)).close(today);
    assertThat(closed.close(today)).isSameAs(closed);
  }

  @Test
  void 연도_경계에서도_지난달과_새해_일간을_분리한다() {
    UserRankingStats closed =
        accumulated(LocalDate.of(2026, 12, 31)).close(LocalDate.of(2027, 1, 1));
    assertThat(closed.published().monthly().month()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(closed.current().monthly().month()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(closed.current().daily().date()).isEqualTo(LocalDate.of(2027, 1, 1));
  }
}
