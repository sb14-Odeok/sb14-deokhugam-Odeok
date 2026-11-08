package com.sprint.deokhugam.notification.application.dto;

import static org.assertj.core.api.Assertions.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort.Direction;

class NotificationSearchCondTest {
  private final UUID userId = UUID.randomUUID();
  private final UUID cursor = UUID.randomUUID();
  private final Instant after = Instant.parse("2026-11-08T00:00:00Z");

  @Test
  void 기본_정렬은_내림차순이며_첫_페이지는_커서가_없다() {
    assertThat(NotificationSearchCond.to(userId, null, null, null, 20))
        .isEqualTo(new NotificationSearchCond(userId, Direction.DESC, null, null, 20));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASC", "asc", "DESC", "desc"})
  void 정렬방향과_UUID_커서를_변환한다(String direction) {
    assertThat(NotificationSearchCond.to(userId, direction, cursor.toString(), after, 5))
        .isEqualTo(
            new NotificationSearchCond(userId, Direction.fromString(direction), cursor, after, 5));
  }

  @Test
  void 사용자_ID가_필수다() {
    assertInvalid(() -> new NotificationSearchCond(null, Direction.DESC, null, null, 20));
  }

  @Test
  void 커서와_기준시각은_둘다_있거나_둘다_없어야_한다() {
    assertInvalid(() -> NotificationSearchCond.to(userId, null, cursor.toString(), null, 20));
    assertInvalid(() -> NotificationSearchCond.to(userId, null, null, after, 20));
  }

  @Test
  void 잘못된_UUID와_정렬방향은_변환할수없다() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> NotificationSearchCond.to(userId, "sideways", null, null, 20));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> NotificationSearchCond.to(userId, null, "invalid", after, 20));
  }

  private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
  }
}
