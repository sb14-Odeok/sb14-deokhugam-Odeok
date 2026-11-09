package com.sprint.deokhugam.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.user.application.dto.query.PowerUserSearchCond;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort.Direction;

class PowerUserCursorTest {
  private static final String ID = "00000000-0000-0000-0000-000000000001";

  @ParameterizedTest
  @EnumSource(RankingPeriod.class)
  void 기간_공개일_정수점수_UUID를_손실없이_복원한다(RankingPeriod period) {
    PowerUserCursor cursor =
        new PowerUserCursor(period, LocalDate.of(2026, 11, 5), Long.MAX_VALUE, UUID.fromString(ID));
    assertThat(PowerUserCursor.decode(cursor.encode())).isEqualTo(cursor);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "ALL_TIME|2026-11-05|1",
        "UNKNOWN|2026-11-05|1|" + ID,
        "DAILY|invalid-date|1|" + ID,
        "DAILY|2026-11-05|NaN|" + ID,
        "DAILY|2026-11-05|-1|" + ID,
        "DAILY|2026-11-05|1|bad-uuid",
        "DAILY|2026-11-05|1|" + ID + "|extra"
      })
  void 잘못된_커서_형식은_거부한다(String encoded) {
    assertThatThrownBy(() -> PowerUserCursor.decode(encoded)).isInstanceOf(BusinessException.class);
  }

  @Test
  void 최초_페이지는_커서가_없어도_된다() {
    assertThat(PowerUserCursor.decode(null)).isNull();
    PowerUserSearchCond condition = new PowerUserSearchCond(null, null, null, null, 20);
    assertThat(condition.period()).isEqualTo(RankingPeriod.ALL_TIME);
    assertThat(condition.direction()).isEqualTo(Direction.ASC);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 101})
  void 페이지_크기는_1에서_100까지다(int limit) {
    assertThatThrownBy(() -> new PowerUserSearchCond(null, null, null, null, limit))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void 커서와_보조_정렬키는_함께_있어야_한다() {
    assertThatThrownBy(() -> new PowerUserSearchCond(null, null, "cursor", null, 20))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new PowerUserSearchCond(null, null, null, Instant.now(), 20))
        .isInstanceOf(BusinessException.class);
  }
}
