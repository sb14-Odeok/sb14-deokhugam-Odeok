package com.sprint.deokhugam.comment.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.sprint.deokhugam.comment.application.dto.CommentCreateRequest;
import com.sprint.deokhugam.comment.application.dto.CommentDto;
import com.sprint.deokhugam.comment.application.dto.CommentUpdateRequest;
import com.sprint.deokhugam.comment.application.provided.command.CommentCommand;
import com.sprint.deokhugam.comment.application.provided.query.CommentFinder;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.domain.comment.exception.CommentErrorCode;
import com.sprint.deokhugam.notification.domain.NotifyEvent;
import com.sprint.deokhugam.review.application.provided.command.ReviewCommand;
import com.sprint.deokhugam.review.application.provided.query.ReviewFinder;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.review.domain.exception.ReviewErrorCode;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class CommentApplicationServiceTest {

  @Mock private CommentCommand commentCommand;
  @Mock private CommentFinder commentFinder;
  @Mock private UserFinder userFinder;
  @Mock private ReviewFinder reviewFinder;
  @Mock private ReviewCommand reviewCommand;
  @Mock private Review review;
  @Mock private User user;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private CommentApplicationService commentApplicationService;

  private UUID commentId;
  private UUID reviewId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    commentId = UUID.randomUUID();
    reviewId = UUID.randomUUID();
    userId = UUID.randomUUID();
  }

  @Test
  @DisplayName("댓글 등록 성공")
  void createComment_success() {
    // Given
    CommentCreateRequest createRequest =
        new CommentCreateRequest(reviewId, userId, "testCreateContent");

    given(reviewFinder.getByIdForUpdateOrThrow(reviewId)).willReturn(review);
    given(review.getId()).willReturn(reviewId);
    given(userFinder.findByIdOrThrow(userId)).willReturn(user);
    given(review.getUser()).willReturn(user);

    Comment comment = Comment.create(review, user, createRequest.content());
    given(commentCommand.createComment(any(Comment.class))).willReturn(comment);

    // When
    CommentDto result = commentApplicationService.create(createRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.content()).isEqualTo("testCreateContent");
    verify(commentCommand).createComment(any(Comment.class));
    verify(reviewCommand).increaseReviewCommentCount(reviewId);
    verify(eventPublisher).publishEvent(any(NotifyEvent.class));
    verify(eventPublisher).publishEvent(any(ActivityEvent.class));
  }

  @Test
  @DisplayName("댓글 등록 실패 - 유저를 찾지 못한 경우")
  void createComment_fail_userNotFound() {
    // Given
    CommentCreateRequest createRequest =
        new CommentCreateRequest(reviewId, userId, "testCreateContent");

    given(userFinder.findByIdOrThrow(userId))
        .willThrow(new BusinessException(UserErrorCode.NOT_FOUND));

    // When & Then
    assertThatThrownBy(() -> commentApplicationService.create(createRequest))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("댓글 등록 실패 - 리뷰를 찾지 못한 경우")
  void createComment_fail_reviewNotFound() {
    // Given
    CommentCreateRequest createRequest =
        new CommentCreateRequest(reviewId, userId, "testCreateContent");

    given(reviewFinder.getByIdForUpdateOrThrow(reviewId))
        .willThrow(new BusinessException(ReviewErrorCode.NOT_FOUND));

    // When & Then
    assertThatThrownBy(() -> commentApplicationService.create(createRequest))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ReviewErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("댓글 수정 성공")
  void updateComment_success() {
    // Given
    given(user.getId()).willReturn(userId);
    given(review.getId()).willReturn(reviewId);

    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");
    Comment comment = spy(Comment.create(review, user, "testOriginContent"));
    Comment updatedComment = Comment.create(review, user, updateRequest.content());

    given(comment.getId()).willReturn(commentId);
    given(commentFinder.findById(commentId)).willReturn(comment);
    given(commentCommand.updateComment(commentId, updateRequest)).willReturn(updatedComment);

    // When
    CommentDto result = commentApplicationService.update(commentId, userId, updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.content()).isEqualTo("testUpdatedContent");
    verify(commentFinder).findById(commentId);
    verify(commentCommand).updateComment(commentId, updateRequest);
  }

  @Test
  @DisplayName("댓글 수정 실패 - 댓글을 찾지 못하는 경우")
  void updateComment_fail_commentNotFound() {
    // Given
    CommentUpdateRequest commentUpdateRequest = new CommentUpdateRequest("testUpdatedContent");

    given(commentFinder.findById(commentId))
        .willThrow(new BusinessException(CommentErrorCode.NOT_FOUND));

    // When & Then
    assertThatThrownBy(
            () -> commentApplicationService.update(commentId, userId, commentUpdateRequest))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("댓글 수정 실패 - 작성자가 아닌 타인이 요청할 경우")
  void updateComment_fail_forbidden() {
    // Given
    UUID otherUserId = UUID.randomUUID();

    given(user.getId()).willReturn(userId);

    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");
    Comment comment = Comment.create(review, user, "testOriginContent");

    given(commentFinder.findById(commentId)).willReturn(comment);

    // When & Then
    assertThatThrownBy(
            () -> commentApplicationService.update(commentId, otherUserId, updateRequest))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.FORBIDDEN);

    verify(commentCommand, never()).updateComment(any(), any());
  }

  @Test
  @DisplayName("댓글 논리 삭제 성공")
  void softDelete_success() {
    // Given
    given(user.getId()).willReturn(userId);
    given(review.getCommentCount()).willReturn(1);
    given(review.getId()).willReturn(reviewId);

    Comment comment = Comment.create(review, user, "testCreateContent");
    given(commentFinder.findById(commentId)).willReturn(comment);

    // When
    commentApplicationService.softDelete(commentId, userId);

    // Then
    assertThat(comment.isDeleted()).isTrue();
    verify(reviewCommand).decreaseReviewCommentCount(reviewId);
  }

  @Test
  @DisplayName("댓글 논리 삭제 성공 - 리뷰의 댓글 수가 0이면 줄이지 않는다")
  void softDelete_success_commentCountZero() {
    // Given
    given(user.getId()).willReturn(userId);
    given(review.getCommentCount()).willReturn(0);

    Comment comment = Comment.create(review, user, "testCreateContent");
    given(commentFinder.findById(commentId)).willReturn(comment);

    // When
    commentApplicationService.softDelete(commentId, userId);

    // Then
    assertThat(comment.isDeleted()).isTrue();
    verify(reviewCommand, never()).decreaseReviewCommentCount(any());
  }

  @Test
  @DisplayName("댓글 논리 삭제 실패 - 작성자가 아닌 타인이 요청할 경우")
  void softDelete_fail_forbidden() {
    // Given
    UUID otherUserId = UUID.randomUUID();
    given(user.getId()).willReturn(userId);

    Comment comment = Comment.create(review, user, "testCreateComment");
    given(commentFinder.findById(commentId)).willReturn(comment);

    // When & Then
    assertThatThrownBy(() -> commentApplicationService.softDelete(commentId, otherUserId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.FORBIDDEN);

    assertThat(comment.isDeleted()).isFalse();
    verify(reviewCommand, never()).decreaseReviewCommentCount(any());
  }

  @Test
  @DisplayName("댓글 물리 삭제 성공")
  void hardDelete_success() {
    // Given
    given(user.getId()).willReturn(userId);
    given(review.getCommentCount()).willReturn(1);
    given(review.getId()).willReturn(reviewId);

    Comment comment = spy(Comment.create(review, user, "testCreateContent"));
    given(comment.getId()).willReturn(commentId);

    given(commentFinder.findByIdIncludingDeleted(commentId)).willReturn(comment);
    willDoNothing().given(commentCommand).hardDelete(commentId);

    // When
    commentApplicationService.hardDelete(commentId, userId);

    // Then
    verify(commentFinder).findByIdIncludingDeleted(commentId);
    verify(commentCommand).hardDelete(commentId);
    verify(reviewCommand).decreaseReviewCommentCount(reviewId);
  }

  @Test
  @DisplayName("댓글 물리 삭제 성공 - 이미 논리 삭제된 댓글은 댓글 수를 다시 줄이지 않는다")
  void hardDelete_success_alreadySoftDeleted() {
    // Given
    given(user.getId()).willReturn(userId);
    // 정상이면 호출되지 않는 값이라 lenient를 쓴다. 0이면 버그가 있어도 감소가 막혀서 테스트가 통과해 버린다
    lenient().when(review.getCommentCount()).thenReturn(1);

    Comment comment = spy(Comment.create(review, user, "testCreateContent"));
    comment.softDelete();
    given(comment.getId()).willReturn(commentId);

    given(commentFinder.findByIdIncludingDeleted(commentId)).willReturn(comment);

    // When
    commentApplicationService.hardDelete(commentId, userId);

    // Then
    verify(commentCommand).hardDelete(commentId);
    verify(reviewCommand, never()).decreaseReviewCommentCount(any());
  }

  @Test
  @DisplayName("댓글 물리 삭제 실패 - 작성자가 아닌 타인이 요청할 경우")
  void hardDelete_fail_forbidden() {
    // Given
    UUID otherUserId = UUID.randomUUID();
    given(user.getId()).willReturn(userId);

    Comment comment = Comment.create(review, user, "testCreateContent");
    given(commentFinder.findByIdIncludingDeleted(commentId)).willReturn(comment);

    // When & Then
    assertThatThrownBy(() -> commentApplicationService.hardDelete(commentId, otherUserId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.FORBIDDEN);

    verify(commentCommand, never()).hardDelete(any());
  }

  @Test
  @DisplayName("유저 ID로 댓글 일괄 삭제 성공")
  void deleteCommentsByUser_success() {
    // Given
    given(review.getCommentCount()).willReturn(1);
    given(review.getId()).willReturn(reviewId);

    Comment comment = Comment.create(review, user, "testCreateContent");
    given(commentFinder.findAllByUserId(userId)).willReturn(List.of(comment));

    // When
    commentApplicationService.deleteCommentsByUser(userId);

    // Then
    verify(commentFinder).findAllByUserId(userId);
    verify(commentCommand).deleteAllByUserId(userId);
    verify(reviewCommand).decreaseReviewCommentCount(reviewId);
  }

  @Test
  @DisplayName("유저 ID로 댓글 일괄 삭제 성공 - 이미 논리 삭제된 댓글은 댓글 수에서 빼지 않는다")
  void deleteCommentsByUser_success_skipSoftDeleted() {
    // Given
    given(review.getCommentCount()).willReturn(1);
    given(review.getId()).willReturn(reviewId);

    Comment activeComment = Comment.create(review, user, "testActiveContent");
    Comment deletedComment = Comment.create(review, user, "testDeletedContent");
    deletedComment.softDelete();

    given(commentFinder.findAllByUserId(userId)).willReturn(List.of(activeComment, deletedComment));

    // When
    commentApplicationService.deleteCommentsByUser(userId);

    // Then
    verify(commentCommand).deleteAllByUserId(userId);
    verify(reviewCommand).decreaseReviewCommentCount(reviewId);
  }

  @Test
  @DisplayName("유저 ID로 댓글 일괄 삭제 - 댓글이 없으면 삭제를 호출하지 않는다")
  void deleteCommentsByUser_noComments() {
    // Given
    given(commentFinder.findAllByUserId(userId)).willReturn(List.of());

    // When
    commentApplicationService.deleteCommentsByUser(userId);

    // Then
    verify(commentCommand, never()).deleteAllByUserId(any());
  }

  @Test
  @DisplayName("유저 ID로 댓글 일괄 삭제 실패 - 유저 ID가 null인 경우")
  void deleteCommentsByUser_fail_nullUserId() {
    // When & Then
    assertThatThrownBy(() -> commentApplicationService.deleteCommentsByUser(null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INVALID_INPUT);

    verify(commentCommand, never()).deleteAllByUserId(any());
  }

  @Test
  @DisplayName("리뷰 ID 목록으로 댓글 일괄 삭제 성공")
  void deleteCommentsByReviews_success() {
    // Given
    List<UUID> reviewIds = List.of(reviewId);

    // When
    commentApplicationService.deleteCommentsByReviews(reviewIds);

    // Then
    verify(commentCommand).deleteAllByReviewIds(eq(reviewIds));
  }

  @Test
  @DisplayName("리뷰 ID 목록으로 댓글 일괄 삭제 실패 - 목록이 null인 경우")
  void deleteCommentsByReviews_fail_nullReviewIds() {
    // When & Then
    assertThatThrownBy(() -> commentApplicationService.deleteCommentsByReviews(null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INVALID_INPUT);

    verify(commentCommand, never()).deleteAllByReviewIds(any());
  }

  @Test
  @DisplayName("리뷰 ID 목록으로 댓글 일괄 삭제 실패 - 목록이 비어 있는 경우")
  void deleteCommentsByReviews_fail_emptyReviewIds() {
    // When & Then
    assertThatThrownBy(() -> commentApplicationService.deleteCommentsByReviews(List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INVALID_INPUT);

    verify(commentCommand, never()).deleteAllByReviewIds(any());
  }
}
