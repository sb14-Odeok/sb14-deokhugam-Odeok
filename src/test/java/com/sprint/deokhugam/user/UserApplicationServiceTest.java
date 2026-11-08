package com.sprint.deokhugam.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sprint.deokhugam.comment.application.provided.CommentUseCase;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCleaner;
import com.sprint.deokhugam.review.application.ReviewCleanupUseCase;
import com.sprint.deokhugam.review.application.provided.command.ReviewLikeCommand;
import com.sprint.deokhugam.user.application.UserApplicationService;
import com.sprint.deokhugam.user.application.provided.command.UserCommand;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 호출 순서와 오류 전파 검증. 실제 DB 롤백은 이 단위 테스트의 검증 범위가 아니다. */
class UserApplicationServiceTest {

  private final UserCommand command = mock(UserCommand.class);
  private final NotificationCleaner notifications = mock(NotificationCleaner.class);
  private final CommentUseCase comments = mock(CommentUseCase.class);
  private final ReviewCleanupUseCase reviews = mock(ReviewCleanupUseCase.class);
  private final ReviewLikeCommand likes = mock(ReviewLikeCommand.class);
  private final UserApplicationService service =
      new UserApplicationService(
          mock(UserFinder.class), command, notifications, comments, reviews, likes);
  private final UUID id = UUID.randomUUID();

  @Test
  void 연관_데이터를_먼저_정리하고_유저를_마지막에_삭제한다() {
    service.cleanupUserWithRelatedData(id);
    InOrder order = inOrder(notifications, likes, comments, reviews, command);
    order.verify(notifications).deleteByUser(id);
    order.verify(likes).deleteLikesByUserId(id);
    order.verify(comments).deleteCommentsByUser(id);
    order.verify(reviews).deleteReviewsWithRelatedDataByUserId(id);
    order.verify(command).delete(id);
  }

  @Test
  void 다른_리뷰에_남긴_좋아요_정리에_실패하면_유저를_삭제하지_않는다() {
    IllegalStateException failure = new IllegalStateException("좋아요 정리 실패");
    when(likes.deleteLikesByUserId(id)).thenThrow(failure);
    assertThatThrownBy(() -> service.cleanupUserWithRelatedData(id)).isSameAs(failure);
    verifyNoInteractions(comments, reviews, command);
  }

  @Test
  void 리뷰_정리에_실패하면_유저_삭제로_진행하지_않는다() {
    IllegalStateException failure = new IllegalStateException("리뷰 정리 실패");
    when(reviews.deleteReviewsWithRelatedDataByUserId(id)).thenThrow(failure);
    assertThatThrownBy(() -> service.cleanupUserWithRelatedData(id)).isSameAs(failure);
    verify(command, never()).delete(any());
  }
}
