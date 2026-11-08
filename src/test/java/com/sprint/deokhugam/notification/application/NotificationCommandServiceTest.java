package com.sprint.deokhugam.notification.application;

import static com.sprint.deokhugam.notification.NotificationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.notification.application.dto.NotificationDto;
import com.sprint.deokhugam.notification.application.required.NotificationPersistencePort;
import com.sprint.deokhugam.notification.domain.Notification;
import com.sprint.deokhugam.notification.domain.NotificationType;
import com.sprint.deokhugam.notification.domain.exception.NotificationErrorCode;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class NotificationCommandServiceTest {
  private final NotificationPersistencePort port = mock(NotificationPersistencePort.class);
  private final NotificationCommandService service = new NotificationCommandService(port);
  private final User user = user();
  private final Review review = review(user);

  @ParameterizedTest
  @EnumSource(
      value = NotificationType.class,
      names = {"LIKE", "COMMENT"})
  void 생성은_알림을_저장하고_저장된_식별자와_스냅샷을_반환한다(NotificationType type) {
    when(port.save(any()))
        .thenAnswer(invocation -> identified(invocation.getArgument(0, Notification.class)));
    NotificationDto dto =
        type == NotificationType.LIKE
            ? service.createLike(review, user, "작성자")
            : service.createComment(review, user, "작성자");
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(port).save(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo(type);
    assertThat(saved.getUser()).isSameAs(user);
    assertThat(dto)
        .isEqualTo(
            NotificationDto.to(
                saved.getId(),
                user.getId(),
                review.getId(),
                review.getContent(),
                type.format("작성자"),
                false,
                CREATED_AT,
                CREATED_AT));
  }

  @Test
  void 순위_알림은_기간과_순위를_저장한다() {
    service.createRank(review, user, PopularReviewPeriod.WEEKLY, 2);
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(port).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(NotificationType.RANKED);
    assertThat(captor.getValue().getMessage()).isEqualTo("나의 리뷰가 주간 인기 리뷰 2위에 진입했습니다.");
  }

  @Test
  void 본인_알림은_읽음으로_변경하고_응답한다() {
    Notification notification = notification(user, review);
    when(port.findById(notification.getId())).thenReturn(Optional.of(notification));
    NotificationDto dto = service.confirm(notification.getId(), user.getId());
    assertThat(notification.isConfirmed()).isTrue();
    assertThat(notification.getConfirmedAt()).isNotNull();
    assertThat(dto.confirmed()).isTrue();
    assertThat(dto.id()).isEqualTo(notification.getId());
    assertThat(dto.userId()).isEqualTo(user.getId());
    assertThat(dto.reviewId()).isEqualTo(review.getId());
    assertThat(dto.reviewContent()).isEqualTo(review.getContent());
  }

  @Test
  void 다른_사람의_알림은_변경하지_않고_거부한다() {
    Notification notification = notification(user, review);
    when(port.findById(notification.getId())).thenReturn(Optional.of(notification));
    assertThatThrownBy(() -> service.confirm(notification.getId(), UUID.randomUUID()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.FORBIDDEN));
    assertThat(notification.isConfirmed()).isFalse();
    assertThat(notification.getConfirmedAt()).isNull();
  }

  @Test
  void 없는_알림은_찾을수없음_예외를_던진다() {
    UUID id = UUID.randomUUID();
    when(port.findById(id)).thenReturn(Optional.empty());
    assertNotFound(id);
  }

  @Test
  void 삭제된_알림도_찾을수없음_예외를_던진다() {
    Notification notification = notification(user, review);
    notification.softDelete();
    when(port.findById(notification.getId())).thenReturn(Optional.of(notification));
    assertNotFound(notification.getId());
    assertThat(notification.isConfirmed()).isFalse();
  }

  private void assertNotFound(UUID id) {
    assertThatThrownBy(() -> service.confirm(id, user.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.NOT_FOUND));
  }

  @Test
  void 전체_읽음은_기존_읽음시각을_보존하고_나머지를_확인한다() {
    Notification confirmed = notification(user, review);
    Notification unread = notification(user, review);
    confirmed.confirm();
    Instant original = confirmed.getConfirmedAt();
    when(port.findAllByUser(user.getId())).thenReturn(List.of(confirmed, unread));
    service.confirmAll(user.getId());
    assertThat(confirmed.getConfirmedAt()).isEqualTo(original);
    assertThat(unread.isConfirmed()).isTrue();
    assertThat(unread.getConfirmedAt()).isNotNull();
  }

  @Test
  void 알림이_없어도_전체_읽음은_성공한다() {
    when(port.findAllByUser(user.getId())).thenReturn(List.of());
    assertThatCode(() -> service.confirmAll(user.getId())).doesNotThrowAnyException();
  }

  @Test
  void 정리_기준과_대상_식별자를_저장소에_전달한다() {
    service.deleteConfirmed(CREATED_AT);
    service.deleteByUser(user.getId());
    service.deleteByReview(review.getId());
    verify(port).deleteConfirmed(CREATED_AT);
    verify(port).deleteByUser(user.getId());
    verify(port).deleteByReview(review.getId());
  }
}
