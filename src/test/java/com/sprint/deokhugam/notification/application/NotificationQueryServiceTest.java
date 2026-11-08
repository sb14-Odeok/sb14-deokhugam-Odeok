package com.sprint.deokhugam.notification.application;

import static com.sprint.deokhugam.notification.NotificationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.notification.application.dto.NotificationDto;
import com.sprint.deokhugam.notification.application.dto.NotificationSearchCond;
import com.sprint.deokhugam.notification.application.required.NotificationPersistencePort;
import com.sprint.deokhugam.notification.domain.Notification;
import com.sprint.deokhugam.notification.domain.exception.NotificationErrorCode;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

class NotificationQueryServiceTest {
  private final NotificationPersistencePort port = mock(NotificationPersistencePort.class);
  private final NotificationQueryService service = new NotificationQueryService(port);
  private final User user = user();
  private final Review review = review(user);

  @Test
  void 활성_알림을_조회한다() {
    Notification notification = notification(user, review);
    when(port.findById(notification.getId())).thenReturn(Optional.of(notification));
    assertThat(service.findById(notification.getId())).isSameAs(notification);
  }

  @Test
  void 없거나_삭제된_알림은_동일한_찾을수없음_예외다() {
    UUID absent = UUID.randomUUID();
    Notification deleted = notification(user, review);
    deleted.softDelete();
    when(port.findById(absent)).thenReturn(Optional.empty());
    when(port.findById(deleted.getId())).thenReturn(Optional.of(deleted));
    for (UUID id : List.of(absent, deleted.getId())) {
      assertThatThrownBy(() -> service.findById(id))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.NOT_FOUND));
    }
  }

  @Test
  void 초과_조회한_마지막_행을_빼고_응답의_마지막_행으로_커서를_만든다() {
    Notification first = notification(user, review);
    Notification second = notification(user, review);
    Notification extra = notification(user, review);
    first.confirm();
    NotificationSearchCond cond =
        new NotificationSearchCond(user.getId(), Direction.DESC, null, null, 2);
    when(port.findAll(cond)).thenReturn(List.of(first, second, extra));
    when(port.countByUser(user.getId())).thenReturn(7L);
    CursorPageResponse<NotificationDto> page = service.findAllByUser(cond);
    assertThat(page.content())
        .extracting(NotificationDto::id)
        .containsExactly(first.getId(), second.getId());
    assertThat(page.content().getFirst())
        .isEqualTo(
            NotificationDto.to(
                first.getId(),
                user.getId(),
                review.getId(),
                first.getReviewContent(),
                first.getMessage(),
                true,
                CREATED_AT,
                CREATED_AT));
    assertThat(page.hasNext()).isTrue();
    assertThat(page.nextCursor()).isEqualTo(second.getId().toString());
    assertThat(page.nextAfter()).isEqualTo(second.getCreatedAt());
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(7);
  }

  @Test
  void 빈_결과는_다음_커서가_없는_빈_페이지다() {
    NotificationSearchCond cond = NotificationSearchCond.to(user.getId(), null, null, null, 20);
    when(port.findAll(cond)).thenReturn(List.of());
    when(port.countByUser(user.getId())).thenReturn(0L);
    CursorPageResponse<NotificationDto> page = service.findAllByUser(cond);
    assertThat(page.content()).isEmpty();
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
    assertThat(page.nextAfter()).isNull();
    assertThat(page.totalElements()).isZero();
  }

  @Test
  void 사용자별_알림수를_반환한다() {
    when(port.countByUser(user.getId())).thenReturn(4L);
    assertThat(service.countByUser(user.getId())).isEqualTo(4);
  }
}
