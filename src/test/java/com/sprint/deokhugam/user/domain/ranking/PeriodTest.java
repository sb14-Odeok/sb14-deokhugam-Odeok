package com.sprint.deokhugam.user.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PeriodTest {
  @ParameterizedTest
  @CsvSource({"2026-11-03,false", "2026-11-04,true", "2026-11-05,false"})
  void 일간은_동일한_날짜만_포함한다(LocalDate target, boolean expected) {
    assertThat(
            new Period.Daily(LocalDate.of(2026, 11, 4), UserActivityMetrics.EMPTY).covers(target))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"2026-11-01,false", "2026-11-02,true", "2026-11-08,true", "2026-11-09,false"})
  void 주간은_월요일_이상_다음_월요일_미만이다(LocalDate target, boolean expected) {
    Period.Weekly weekly = new Period.Weekly(LocalDate.of(2026, 11, 4), UserActivityMetrics.EMPTY);
    assertThat(weekly.week()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(weekly.covers(target)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"2026-10-31,false", "2026-11-01,true", "2026-11-30,true", "2026-12-01,false"})
  void 월간은_1일_이상_다음달_1일_미만이다(LocalDate target, boolean expected) {
    Period.Monthly monthly =
        new Period.Monthly(LocalDate.of(2026, 11, 4), UserActivityMetrics.EMPTY);
    assertThat(monthly.month()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(monthly.covers(target)).isEqualTo(expected);
  }

  @Test
  void 지표_교체는_기간_식별자와_기존_객체를_보존한다() {
    Period original = new Period.Daily(LocalDate.of(2026, 11, 4), UserActivityMetrics.EMPTY);
    Period changed = original.withMetrics(new UserActivityMetrics(1, 0, 0, 0));
    assertThat(changed)
        .isEqualTo(
            new Period.Daily(LocalDate.of(2026, 11, 4), new UserActivityMetrics(1, 0, 0, 0)));
    assertThat(original.metrics()).isEqualTo(UserActivityMetrics.EMPTY);
  }
}
