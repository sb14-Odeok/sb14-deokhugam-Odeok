package com.sprint.deokhugam.user.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.user.domain.ranking.Period;
import com.sprint.deokhugam.user.domain.ranking.UserActivityMetrics;
import com.sprint.deokhugam.user.domain.ranking.UserRankingStats;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UserRankingStatsConverterTest {
  private final UserRankingStatsConverter converter =
      new UserRankingStatsConverter(JsonMapper.builder().findAndAddModules().build());

  @Test
  void 초기_통계의_null_공개일과_기간_날짜를_왕복_보존한다() {
    UserRankingStats stats = UserRankingStats.empty(LocalDate.of(2026, 11, 4));
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(stats)))
        .isEqualTo(stats);
  }

  @Test
  void 마감된_통계의_지표와_공개일을_왕복_보존한다() {
    LocalDate date = LocalDate.of(2026, 11, 4);
    UserRankingStats empty = UserRankingStats.empty(date);
    UserRankingStats stats =
        new UserRankingStats(
                empty
                    .current()
                    .withPeriod(new Period.Daily(date, new UserActivityMetrics(1, 2, 3, 4))),
                empty.published(),
                null)
            .close(date.plusDays(1));
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(stats)))
        .isEqualTo(stats);
  }

  @Test
  void null은_양방향으로_null을_유지한다() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void 손상된_JSON은_내부_오류와_원인을_남긴다() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("{broken"))
        .isInstanceOf(BusinessException.class)
        .hasCauseInstanceOf(tools.jackson.core.JacksonException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
  }
}
