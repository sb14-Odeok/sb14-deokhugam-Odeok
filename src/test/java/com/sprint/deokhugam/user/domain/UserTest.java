package com.sprint.deokhugam.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.user.UserTestFixtures;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import com.sprint.deokhugam.user.domain.ranking.Period;
import com.sprint.deokhugam.user.domain.ranking.PeriodStats;
import com.sprint.deokhugam.user.domain.ranking.UserActivityMetrics;
import com.sprint.deokhugam.user.domain.ranking.UserRankingStats;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {
  @Test
  void 같은_닉네임으로_수정하면_기존_값을_유지한다() {
    User user = UserTestFixtures.user(UUID.randomUUID());
    assertThatThrownBy(() -> user.updateNickname("테스터"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
    assertThat(user.getNickname()).isEqualTo("테스터");
  }

  @Test
  void 통계를_두번_초기화하지_못한다() {
    User user = UserTestFixtures.user(UUID.randomUUID());
    assertThatThrownBy(() -> user.initializeRankingStats(UserTestFixtures.DATE))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void 현재_통계가_달라도_점수_컬럼은_공개_통계에서_계산한다() {
    User user = UserTestFixtures.user(UUID.randomUUID());
    var date = UserTestFixtures.DATE;
    PeriodStats current =
        PeriodStats.empty(date)
            .withPeriod(new Period.AllTime(new UserActivityMetrics(100, 100, 100, 100)));
    PeriodStats published =
        new PeriodStats(
            new Period.Daily(date.minusDays(1), new UserActivityMetrics(1, 0, 0, 0)),
            new Period.Weekly(date.minusWeeks(1), new UserActivityMetrics(0, 1, 0, 0)),
            new Period.Monthly(date.minusMonths(1), new UserActivityMetrics(0, 0, 1, 0)),
            new Period.AllTime(new UserActivityMetrics(0, 0, 0, 1)));
    user.updateRankingStats(new UserRankingStats(current, published, date));
    assertThat(user.getDailyScore()).isEqualTo(15);
    assertThat(user.getWeeklyScore()).isEqualTo(35);
    assertThat(user.getMonthlyScore()).isEqualTo(20);
    assertThat(user.getAllTimeScore()).isEqualTo(30);
    assertThat(user.getRankingPublishedOn()).isEqualTo(date);
  }

  @Test
  void 논리_삭제는_삭제_시각을_기록하고_재삭제를_거부한다() {
    User user = UserTestFixtures.user(UUID.randomUUID());
    user.softDelete();
    var deletedAt = user.getDeletedAt();
    assertThat(user.isDeleted()).isTrue();
    assertThatThrownBy(user::softDelete).isInstanceOf(IllegalStateException.class);
    assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
  }
}
