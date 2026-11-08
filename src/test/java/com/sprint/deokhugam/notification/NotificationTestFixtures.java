package com.sprint.deokhugam.notification;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.entity.BaseUpdatableEntity;
import com.sprint.deokhugam.notification.domain.Notification;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** 단위 테스트에서만 식별자와 감사 필드를 직접 지정한다. */
public final class NotificationTestFixtures {
  public static final Instant CREATED_AT = Instant.parse("2026-11-08T00:00:00Z");

  private NotificationTestFixtures() {}

  public static User newUser() {
    return User.create(
        "알림수신자", Email.to(UUID.randomUUID() + "@example.com"), Password.to("encoded"));
  }

  public static Book newBook() {
    return Book.create("알림 테스트 도서", "저자", "설명", "출판사", LocalDate.of(2026, 1, 1), null, null);
  }

  public static User user() {
    return identified(newUser());
  }

  public static Review review(User user) {
    return identified(Review.create(4, "알림 생성 당시 리뷰", user, newBook()));
  }

  public static Notification notification(User user, Review review) {
    return identified(Notification.createLike(user, review, review.getContent(), "작성자"));
  }

  public static <T extends BaseUpdatableEntity> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(entity, "createdAt", CREATED_AT);
    ReflectionTestUtils.setField(entity, "updatedAt", CREATED_AT);
    return entity;
  }
}
