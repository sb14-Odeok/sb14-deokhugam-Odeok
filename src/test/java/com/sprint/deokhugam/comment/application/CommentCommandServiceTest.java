package com.sprint.deokhugam.comment.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.sprint.deokhugam.comment.application.dto.CommentCreateRequest;
import com.sprint.deokhugam.comment.application.dto.CommentDto;
import com.sprint.deokhugam.comment.application.dto.CommentUpdateRequest;
import com.sprint.deokhugam.comment.application.provided.command.CommentCommand;
import com.sprint.deokhugam.comment.application.provided.query.CommentFinder;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.comment.exception.CommentErrorCode;
import com.sprint.deokhugam.domain.review.exception.ReviewErrorCode;
import com.sprint.deokhugam.review.application.provided.query.ReviewFinder;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
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
public class CommentCommandServiceTest {

  @Mock private CommentCommand commentCommand;
  @Mock private CommentFinder commentFinder;
  @Mock private UserFinder userFinder;
  @Mock private ReviewFinder reviewFinder;
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

    given(reviewFinder.getByIdOrThrow(reviewId)).willReturn(review);
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
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.NOT_FOUND);
            });
  }

  @Test
  @DisplayName("댓글 등록 실패 - 리뷰를 찾지 못한 경우")
  void createComment_fail_reviewNotFound() {
    // Given
    CommentCreateRequest createRequest =
        new CommentCreateRequest(reviewId, userId, "testCreateContent");

    given(reviewFinder.getByIdOrThrow(reviewId))
        .willThrow(new BusinessException(ReviewErrorCode.NOT_FOUND));

    // When & Then
    assertThatThrownBy(() -> commentApplicationService.create(createRequest))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.getErrorCode()).isEqualTo(ReviewErrorCode.NOT_FOUND);
            });
  }

  @Test
  @DisplayName("댓글 수정 성공")
  void updateComment_success() {
    // Given
    given(user.getId()).willReturn(userId);
    given(review.getId()).willReturn(reviewId);

    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");
    Comment comment = Comment.create(review, user, updateRequest.content());

    Comment spyComment = spy(comment);
    given(spyComment.getId()).willReturn(commentId);

    given(commentFinder.findById(commentId)).willReturn(spyComment);
    given(commentCommand.updateComment(eq(commentId), any(CommentUpdateRequest.class)))
        .willReturn(comment);

    // When
    CommentDto result = commentApplicationService.update(commentId, userId, updateRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.content()).isEqualTo("testUpdatedContent");
    verify(commentFinder).findById(commentId);
    verify(commentCommand).updateComment(eq(commentId), any(CommentUpdateRequest.class));
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
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.getErrorCode()).isEqualTo(CommentErrorCode.NOT_FOUND);
            });
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
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.getErrorCode()).isEqualTo(CommentErrorCode.FORBIDDEN);
            });
  }

  @Test
  @DisplayName("댓글 논리 삭제 성공")
  void softDelete_success() {
    // Given
    given(user.getId()).willReturn(userId);

    Comment comment = Comment.create(review, user, "testCreateContent");
    given(commentFinder.findById(commentId)).willReturn(comment);

    // When
    commentApplicationService.softDelete(commentId, userId);

    // Then
    verify(commentFinder).findById(commentId);
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
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.getErrorCode()).isEqualTo(CommentErrorCode.FORBIDDEN);
            });
  }

  @Test
  @DisplayName("댓글 물리 삭제 성공")
  void hardDelete_success() {
    // Given
    given(user.getId()).willReturn(userId);

    Comment comment = Comment.create(review, user, "testCreateContent");
    Comment spyComment = spy(comment);
    given(spyComment.getId()).willReturn(commentId);

    given(commentFinder.findByIdIncludingDeleted(commentId)).willReturn(spyComment);
    willDoNothing().given(commentCommand).hardDelete(commentId);

    // When
    commentApplicationService.hardDelete(commentId, userId);

    // Then
    verify(commentFinder).findByIdIncludingDeleted(commentId);
    verify(commentCommand).hardDelete(commentId);
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
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.getErrorCode()).isEqualTo(CommentErrorCode.FORBIDDEN);
            });
  }

  @Test
  @DisplayName("유저 ID로 댓글 일괄 삭제 성공")
  void deleteCommentsByUser_success() {
    // Given
    Comment comment = Comment.create(review, user, "testCreateContent");
    given(commentFinder.findAllByUserId(userId)).willReturn(List.of(comment));

    // When
    commentApplicationService.deleteCommentsByUser(userId);

    // Then
    verify(commentFinder).findAllByUserId(userId);
    verify(commentCommand).deleteAllByUserId(userId);
  }
}
