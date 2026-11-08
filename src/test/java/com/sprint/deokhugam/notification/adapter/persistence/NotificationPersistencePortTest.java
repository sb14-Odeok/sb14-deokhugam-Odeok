package com.sprint.deokhugam.notification.adapter.persistence;

import static com.sprint.deokhugam.notification.NotificationTestFixtures.newBook;
import static com.sprint.deokhugam.notification.NotificationTestFixtures.newUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.notification.application.dto.NotificationSearchCond;
import com.sprint.deokhugam.notification.application.required.NotificationPersistencePort;
import com.sprint.deokhugam.notification.domain.Notification;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL의 UUID 비교와 벌크 삭제를 검증한다. 각 테스트 데이터는 롤백한다. */
@SpringBootTest
@ActiveProfiles("test")
@Tag("postgres")
@Transactional
class NotificationPersistencePortTest {
  private static final Instant TIME = Instant.parse("2026-11-08T00:00:00Z");
  @Autowired NotificationPersistencePort port;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  private User user;
  private Review review;

  @BeforeEach
  void setUp() {
    user = persist(newUser());
    Book book = persist(newBook());
    review = persist(Review.create(4, "테스트 리뷰", user, book));
  }

  private <T> T persist(T value) {
    em.persist(value);
    return value;
  }

  private Notification notification(User receiver, Review target, Instant createdAt) {
    Notification notification =
        port.save(Notification.createLike(receiver, target, target.getContent(), "작성자"));
    em.flush();
    jdbc.update(
        "update notifications set created_at = ? where id = ?",
        Timestamp.from(createdAt),
        notification.getId());
    return notification;
  }

  private Notification notification(Instant createdAt) {
    return notification(user, review, createdAt);
  }

  @Test
  void 저장한_알림의_연관관계와_감사필드_스냅샷을_다시_읽는다() {
    Notification saved = notification(TIME);
    UUID id = saved.getId();
    em.clear();
    Notification found = port.findById(id).orElseThrow();
    assertThat(found.getUser().getId()).isEqualTo(user.getId());
    assertThat(found.getReview().getId()).isEqualTo(review.getId());
    assertThat(found.getReviewContent()).isEqualTo("테스트 리뷰");
    assertThat(found.getMessage()).isEqualTo("[작성자]님이 나의 리뷰를 좋아합니다.");
    assertThat(found.getCreatedAt()).isEqualTo(TIME);
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(found.isConfirmed()).isFalse();
    assertThat(port.findById(UUID.randomUUID())).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(Direction.class)
  void 같은시각_UUID_타이브레이커와_시간_경계로_페이지를_중복없이_조회한다(Direction direction) {
    Notification old = notification(TIME.minusSeconds(1));
    List<UUID> tied = new ArrayList<>();
    for (int i = 0; i < 3; i++) tied.add(notification(TIME).getId());
    Notification recent = notification(TIME.plusSeconds(1));
    Notification deleted = notification(TIME);
    deleted.softDelete();
    User other = persist(newUser());
    notification(other, review, TIME);
    em.flush();
    em.clear();

    // PostgreSQL UUID 순서는 문자열의 16진수 순서와 일치한다. Java UUID.compareTo는 부호가 다르다.
    tied.sort(Comparator.comparing(UUID::toString));
    List<UUID> expected = new ArrayList<>();
    expected.add(old.getId());
    expected.addAll(tied);
    expected.add(recent.getId());
    if (direction == Direction.DESC) expected = expected.reversed();
    List<UUID> actual = new ArrayList<>();
    UUID cursor = null;
    Instant after = null;
    for (int page = 0; page < 3; page++) {
      List<Notification> rows =
          port.findAll(new NotificationSearchCond(user.getId(), direction, cursor, after, 2));
      assertThat(rows).hasSize(page < 2 ? 3 : 1); // limit + 1
      List<Notification> content = rows.stream().limit(2).toList();
      actual.addAll(content.stream().map(Notification::getId).toList());
      cursor = content.getLast().getId();
      after = content.getLast().getCreatedAt();
    }
    assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    assertThat(port.findAll(new NotificationSearchCond(user.getId(), direction, cursor, after, 2)))
        .isEmpty();
    assertThat(port.countByUser(user.getId())).isEqualTo(5);
    assertThat(port.countByUser(UUID.randomUUID())).isZero();
  }

  @Test
  void 사용자별_전체조회는_다른_수신자의_알림을_포함하지_않는다() {
    Notification own = notification(TIME);
    User other = persist(newUser());
    notification(other, review, TIME);
    em.clear();
    assertThat(port.findAllByUser(user.getId()))
        .extracting(Notification::getId)
        .containsExactly(own.getId());
  }

  @Test
  void 정리대상은_삭제시각_기준_이하만_오래된순으로_제한한다() {
    Notification first = notification(TIME);
    Notification boundary = notification(TIME);
    Notification recent = notification(TIME);
    Notification active = notification(TIME);
    jdbc.update(
        "update notifications set deleted_at = ? where id = ?",
        Timestamp.from(TIME.minusSeconds(1)),
        first.getId());
    jdbc.update(
        "update notifications set deleted_at = ? where id = ?",
        Timestamp.from(TIME),
        boundary.getId());
    jdbc.update(
        "update notifications set deleted_at = ? where id = ?",
        Timestamp.from(TIME.plusSeconds(1)),
        recent.getId());
    em.clear();
    assertThat(port.findCleanupIds(TIME, 1)).containsExactly(first.getId());
    assertThat(port.findCleanupIds(TIME, 10)).containsExactly(first.getId(), boundary.getId());
    assertThat(port.findById(active.getId())).isPresent();
  }

  @Test
  void 읽음_정리는_확인시각_경계까지_삭제하고_미확인과_최근알림은_보존한다() {
    Notification old = notification(TIME);
    Notification boundary = notification(TIME);
    Notification recent = notification(TIME);
    Notification unread = notification(TIME);
    for (Notification item : List.of(old, boundary, recent)) {
      Instant confirmedAt =
          item == old ? TIME.minusSeconds(1) : item == boundary ? TIME : TIME.plusSeconds(1);
      jdbc.update(
          "update notifications set confirmed = true, confirmed_at = ? where id = ?",
          Timestamp.from(confirmedAt),
          item.getId());
    }
    em.clear();
    port.deleteConfirmed(TIME);
    em.clear();
    assertThat(port.findById(old.getId())).isEmpty();
    assertThat(port.findById(boundary.getId())).isEmpty();
    assertThat(port.findById(recent.getId())).isPresent();
    assertThat(port.findById(unread.getId())).isPresent();
  }

  @Test
  void 사용자_삭제는_다른_수신자_알림을_보존한다() {
    Notification own = notification(TIME);
    User other = persist(newUser());
    Notification kept = notification(other, review, TIME);
    port.deleteByUser(user.getId());
    em.clear();
    assertThat(port.findById(own.getId())).isEmpty();
    assertThat(port.findById(kept.getId())).isPresent();
  }

  @Test
  void 리뷰_삭제는_그_리뷰의_모든_수신자_알림만_삭제한다() {
    Notification own = notification(TIME);
    User other = persist(newUser());
    Notification sameReview = notification(other, review, TIME);
    Review otherReview = persist(Review.create(3, "다른 리뷰", user, review.getBook()));
    Notification kept = notification(user, otherReview, TIME);
    port.deleteByReview(review.getId());
    em.clear();
    assertThat(port.findById(own.getId())).isEmpty();
    assertThat(port.findById(sameReview.getId())).isEmpty();
    assertThat(port.findById(kept.getId())).isPresent();
  }

  @Test
  void 리뷰목록_벌크삭제와_빈목록은_지정하지않은_리뷰에_영향이_없다() {
    Notification first = notification(TIME);
    Review secondReview = persist(Review.create(3, "두번째 리뷰", user, review.getBook()));
    Review thirdReview = persist(Review.create(3, "세번째 리뷰", user, review.getBook()));
    Notification second = notification(user, secondReview, TIME);
    Notification kept = notification(user, thirdReview, TIME);
    port.deleteByReviewIds(List.of());
    em.clear();
    assertThat(port.findById(first.getId())).isPresent();
    port.deleteByReviewIds(List.of(review.getId(), secondReview.getId()));
    em.clear();
    assertThat(port.findById(first.getId())).isEmpty();
    assertThat(port.findById(second.getId())).isEmpty();
    assertThat(port.findById(kept.getId())).isPresent();
  }
}
