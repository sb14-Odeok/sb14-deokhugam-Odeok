package com.sprint.deokhugam.user.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserActivityMetricsTest {
  @ParameterizedTest
  @CsvSource({"0,0,0,0,0", "1,0,0,0,15", "0,1,0,0,35", "0,0,1,0,20", "0,0,0,1,30", "1,2,1,2,165"})
  void 네_지표에_가중치를_적용한_100배_정수_점수를_계산한다(
      long receivedLikes, long receivedComments, long likes, long comments, long expected) {
    assertThat(
            new UserActivityMetrics(receivedLikes, receivedComments, likes, comments).scoreUnits())
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"-1,0,0,0", "0,-1,0,0", "0,0,-1,0", "0,0,0,-1"})
  void 음수인_지표는_허용하지_않는다(long a, long b, long c, long d) {
    assertThatThrownBy(() -> new UserActivityMetrics(a, b, c, d))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void 지표_변경은_새_객체를_만들고_기존_값을_변경하지_않는다() {
    UserActivityMetrics before = new UserActivityMetrics(1, 2, 3, 4);
    assertThat(before.changeReviewLikeCount(1)).isEqualTo(new UserActivityMetrics(2, 2, 3, 4));
    assertThat(before.changeReviewCommentCount(1)).isEqualTo(new UserActivityMetrics(1, 3, 3, 4));
    assertThat(before.changeParticipatedLikeCount(1))
        .isEqualTo(new UserActivityMetrics(1, 2, 4, 4));
    assertThat(before.changeParticipatedCommentCount(1))
        .isEqualTo(new UserActivityMetrics(1, 2, 3, 5));
    assertThat(before).isEqualTo(new UserActivityMetrics(1, 2, 3, 4));
  }
}
