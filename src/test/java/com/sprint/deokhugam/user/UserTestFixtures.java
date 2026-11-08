package com.sprint.deokhugam.user;

import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** JPA를 실행하지 않는 테스트에서 식별자와 감사 필드를 채우는 픽스처. */
public final class UserTestFixtures {
  public static final LocalDate DATE = LocalDate.of(2026, 11, 4);
  public static final Instant CREATED_AT = Instant.parse("2026-11-04T01:00:00Z");

  private UserTestFixtures() {}

  public static User user(UUID id) {
    return user(id, DATE);
  }

  public static User user(UUID id, LocalDate date) {
    User user = User.create("테스터", Email.to(id + "@example.com"), Password.to("encoded"));
    persisted(user, id);
    user.initializeRankingStats(date);
    return user;
  }

  public static User persisted(User user, UUID id) {
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
    ReflectionTestUtils.setField(user, "updatedAt", CREATED_AT);
    return user;
  }
}
