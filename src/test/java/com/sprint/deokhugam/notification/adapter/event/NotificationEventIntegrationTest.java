package com.sprint.deokhugam.notification.adapter.event;

import static com.sprint.deokhugam.notification.NotificationTestFixtures.newBook;
import static com.sprint.deokhugam.notification.NotificationTestFixtures.newUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCommand;
import com.sprint.deokhugam.notification.application.required.NotificationPersistencePort;
import com.sprint.deokhugam.notification.domain.Notification;
import com.sprint.deokhugam.notification.domain.NotificationType;
import com.sprint.deokhugam.notification.domain.NotifyEvent;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 커밋 후 리스너와 REQUIRES_NEW의 실제 저장을 확인하므로 테스트 자체에 @Transactional을 붙이지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@Tag("postgres")
class NotificationEventIntegrationTest {
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired NotificationPersistencePort port;
  @Autowired NotificationCommand command;
  @Autowired EntityManager em;
  private TransactionTemplate tx;
  private UUID userId;
  private UUID reviewId;
  private UUID bookId;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        status -> {
          User user = newUser();
          Book book = newBook();
          em.persist(user);
          em.persist(book);
          Review review = Review.create(4, "이벤트 당시 리뷰", user, book);
          em.persist(review);
          em.flush();
          userId = user.getId();
          bookId = book.getId();
          reviewId = review.getId();
        });
  }

  @AfterEach
  void cleanUp() {
    if (reviewId == null) return;
    tx.executeWithoutResult(
        status -> {
          port.deleteByReview(reviewId);
          em.createQuery("delete from Review r where r.id = :id")
              .setParameter("id", reviewId)
              .executeUpdate();
          em.createQuery("delete from Book b where b.id = :id")
              .setParameter("id", bookId)
              .executeUpdate();
          em.createQuery("delete from User u where u.id = :id")
              .setParameter("id", userId)
              .executeUpdate();
        });
  }

  private NotifyEvent event(NotificationType type) {
    return switch (type) {
      case LIKE -> NotifyEvent.like(reviewId, UUID.randomUUID(), "좋아요작성자");
      case COMMENT -> NotifyEvent.comment(reviewId, UUID.randomUUID(), "댓글작성자");
      case RANKED -> NotifyEvent.ranked(reviewId, PopularReviewPeriod.DAILY, 2);
    };
  }

  private List<Notification> notifications() {
    return tx.execute(status -> port.findAllByUser(userId));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void 커밋_전에는_저장하지않고_커밋_후_새_트랜잭션으로_리뷰작성자에게_저장한다(NotificationType type) {
    tx.executeWithoutResult(
        status -> {
          publisher.publishEvent(event(type));
          assertThat(port.countByUser(userId)).isZero();
        });
    List<Notification> saved = notifications();
    assertThat(saved).hasSize(1);
    Notification notification = saved.getFirst();
    assertThat(notification.getType()).isEqualTo(type);
    assertThat(notification.getUser().getId()).isEqualTo(userId);
    assertThat(notification.getReview().getId()).isEqualTo(reviewId);
    assertThat(notification.getReviewContent()).isEqualTo("이벤트 당시 리뷰");
    assertThat(notification.getMessage())
        .isEqualTo(
            switch (type) {
              case LIKE -> "[좋아요작성자]님이 나의 리뷰를 좋아합니다.";
              case COMMENT -> "[댓글작성자]님이 나의 리뷰에 댓글을 남겼습니다.";
              case RANKED -> "나의 리뷰가 일간 인기 리뷰 2위에 진입했습니다.";
            });
    assertThat(notification.getCreatedAt()).isNotNull();
  }

  @Test
  void 원본_트랜잭션이_롤백되면_알림은_생성하지_않는다() {
    tx.executeWithoutResult(
        status -> {
          publisher.publishEvent(event(NotificationType.LIKE));
          status.setRollbackOnly();
        });
    assertThat(notifications()).isEmpty();
  }

  @Test
  void 트랜잭션_밖에서_발행한_이벤트는_커밋_후_리스너가_처리하지_않는다() {
    publisher.publishEvent(event(NotificationType.COMMENT));
    assertThat(notifications()).isEmpty();
  }

  @Test
  void 읽음_처리는_트랜잭션_종료후에도_DB에_남는다() {
    tx.executeWithoutResult(status -> publisher.publishEvent(event(NotificationType.LIKE)));
    UUID id = notifications().getFirst().getId();
    assertThat(command.confirm(id, userId).confirmed()).isTrue();
    Notification saved = notifications().getFirst();
    assertThat(saved.isConfirmed()).isTrue();
    assertThat(saved.getConfirmedAt()).isNotNull();
  }
}
