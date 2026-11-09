package com.sprint.deokhugam.comment.application;

import com.sprint.deokhugam.comment.application.dto.CommentCreateRequest;
import com.sprint.deokhugam.comment.application.dto.CommentDto;
import com.sprint.deokhugam.comment.application.dto.CommentSearchCondition;
import com.sprint.deokhugam.comment.application.dto.CommentUpdateRequest;
import com.sprint.deokhugam.comment.application.provided.CommentUseCase;
import com.sprint.deokhugam.comment.application.provided.command.CommentCommand;
import com.sprint.deokhugam.comment.application.provided.query.CommentFinder;
import com.sprint.deokhugam.comment.application.required.CommentSearchPort;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.comment.domain.exception.CommentErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.notification.domain.NotifyEvent;
import com.sprint.deokhugam.review.application.provided.command.ReviewCommand;
import com.sprint.deokhugam.review.application.provided.query.ReviewFinder;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentApplicationService implements CommentUseCase {

  private final CommentCommand commentCommand;
  private final CommentFinder commentFinder;

  private final ReviewFinder reviewFinder;
  private final UserFinder userFinder;

  private final ReviewCommand reviewCommand;

  private final CommentSearchPort commentSearchPort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public CommentDto create(CommentCreateRequest createRequest) {
    Review review = reviewFinder.getByIdForUpdateOrThrow(createRequest.reviewId());
    User user = userFinder.findByIdOrThrow(createRequest.userId());

    Comment comment = Comment.create(review, user, createRequest.content());
    Comment savedComment = commentCommand.createComment(comment);
    reviewCommand.increaseReviewCommentCount(review.getId());

    eventPublisher.publishEvent(
        NotifyEvent.comment(
            createRequest.reviewId(), review.getUser().getId(), user.getNickname()));

    eventPublisher.publishEvent(
        ActivityEvent.CommentAdded(
            UUID.randomUUID(),
            createRequest.reviewId(),
            review.getUser().getId(),
            user.getId(),
            review.getCreatedAt(),
            Instant.now()));

    return CommentDto.from(savedComment);
  }

  @Override
  @Transactional
  public CommentDto update(UUID commentId, UUID requestUserId, CommentUpdateRequest updateRequest) {
    Comment comment = getVerifiedComment(commentId, requestUserId);
    Comment updatedComment = commentCommand.updateComment(comment.getId(), updateRequest);

    return CommentDto.from(updatedComment);
  }

  @Override
  @Transactional
  public void softDelete(UUID commentId, UUID userId) {
    Comment comment = getVerifiedComment(commentId, userId);

    comment.softDelete();
    decreaseCommentCount(comment.getReview());
  }

  @Override
  @Transactional
  public void hardDelete(UUID commentId, UUID userId) {
    // 논리 삭제된 댓글까지 찾는다
    Comment comment = commentFinder.findByIdIncludingDeleted(commentId);
    verifyWriter(comment, userId);

    if (!comment.isDeleted()) {
      decreaseCommentCount(comment.getReview());
    }

    commentCommand.hardDelete(comment.getId());
  }

  private Comment getVerifiedComment(UUID commentId, UUID userId) {
    Comment comment = commentFinder.findById(commentId);
    verifyWriter(comment, userId);
    return comment;
  }

  private void verifyWriter(Comment comment, UUID userId) {
    if (!comment.isWrittenBy(userId)) {
      throw new BusinessException(CommentErrorCode.FORBIDDEN);
    }
  }

  private void decreaseCommentCount(Review review) {
    if (review.getCommentCount() > 0) {
      reviewCommand.decreaseReviewCommentCount(review.getId());
    }
  }

  @Override
  @Transactional
  public void deleteCommentsByUser(UUID userId) {
    if (userId == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    List<Comment> comments = commentFinder.findAllByUserId(userId);
    if (!comments.isEmpty()) {
      for (Comment comment : comments) {
        if (!comment.isDeleted()) {
          decreaseCommentCount(comment.getReview());
        }
      }

      commentCommand.deleteAllByUserId(userId);
    }
  }

  @Override
  @Transactional
  public void deleteCommentsByReviews(List<UUID> reviewIds) {
    if (reviewIds == null || reviewIds.isEmpty()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
    commentCommand.deleteAllByReviewIds(reviewIds);
  }

  @Override
  @Transactional(readOnly = true)
  public CursorPageResponse<CommentDto> searchCommentsByPagination(
      CommentSearchCondition condition) {
    reviewFinder.requireActiveById(condition.reviewId());

    List<Comment> commentList = commentSearchPort.search(condition);
    long totalElements = commentSearchPort.count(condition);

    List<CommentDto> responses = commentList.stream().map(CommentDto::from).toList();

    return toCursorPage(responses, condition, totalElements);
  }

  private CursorPageResponse<CommentDto> toCursorPage(
      List<CommentDto> responses, CommentSearchCondition condition, long totalElements) {
    return CursorPageResponse.of(
        responses, condition.limit(), CommentDto::createdAt, CommentDto::createdAt, totalElements);
  }
}
