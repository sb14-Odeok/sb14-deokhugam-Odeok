package com.sprint.deokhugam.review.application;

import com.sprint.deokhugam.book.application.provided.command.BookReviewStatsCommand;
import com.sprint.deokhugam.book.application.provided.query.BookFinder;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.domain.review.exception.ReviewErrorCode;
import com.sprint.deokhugam.notification.domain.NotifyEvent;
import com.sprint.deokhugam.review.application.dto.req.CreateReviewCommand;
import com.sprint.deokhugam.review.application.dto.req.ReviewSearchCondition;
import com.sprint.deokhugam.review.application.dto.res.ReviewLikeResponse;
import com.sprint.deokhugam.review.application.dto.res.ReviewResponse;
import com.sprint.deokhugam.review.application.required.ReviewLikeQueryPort;
import com.sprint.deokhugam.review.application.required.ReviewLikeRepositoryPort;
import com.sprint.deokhugam.review.application.required.ReviewQueryPort;
import com.sprint.deokhugam.review.application.required.ReviewRepositoryPort;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.review.domain.ReviewLike;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// api 전용 어플리케이션 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewApplicationService implements ReviewUseCase {

  private final ReviewLikeQueryPort reviewLikeQueryPort;
  private final ReviewLikeRepositoryPort reviewLikeRepositoryPort;
  private final ReviewQueryPort reviewQueryPort;
  private final ReviewRepositoryPort reviewRepositoryPort;

  private final BookReviewStatsCommand bookReviewStatsCommand;
  private final BookFinder bookFinder;
  private final UserFinder userFinder;

  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public ReviewResponse createReview(CreateReviewCommand command) {
    User user = userFinder.findByIdOrThrow(command.userId());
    Book book = bookFinder.findById(command.bookId());

    Review review = Review.create(command.rating(), command.content(), user, book);

    if (reviewRepositoryPort.existsByUserIdAndBookId(
        review.getUser().getId(), review.getBook().getId())) {
      throw new BusinessException(ReviewErrorCode.ALREADY_EXISTS);
    }

    Review saved = reviewRepositoryPort.save(review);
    bookReviewStatsCommand.addReview(saved.getBook(), saved.getRating());

    return ReviewResponse.of(saved, false);
  }

  @Override
  @Transactional(readOnly = true)
  public ReviewResponse getReviewDetail(UUID reviewId, UUID requestUserId) {
    Review review = reviewQueryPort.getByIdOrThrow(reviewId);
    boolean likedByMe = reviewLikeQueryPort.isLikedBy(reviewId, requestUserId);

    return ReviewResponse.of(review, likedByMe);
  }

  @Override
  @Transactional
  public ReviewResponse updateReview(UUID userId, UUID reviewId, String newContent, int newRating) {
    userFinder.findByIdOrThrow(userId);
    Review review = reviewRepositoryPort.getByIdOrThrow(reviewId);

    if (!review.verifyReviewUpdate(userId)) {
      throw new BusinessException(ReviewErrorCode.FORBIDDEN);
    }

    int oldRating = review.getRating();
    review.updateContentAndRating(newContent, newRating);
    bookReviewStatsCommand.changeReviewRating(review.getBook(), oldRating, newRating);

    boolean likedByMe = reviewLikeQueryPort.isLikedBy(reviewId, userId);

    return ReviewResponse.of(review, likedByMe);
  }

  @Override
  @Transactional(readOnly = true)
  public CursorPageResponse<ReviewResponse> searchReviewsByPagination(
      ReviewSearchCondition condition, UUID requestUserId) {
    List<Review> reviewList = reviewQueryPort.search(condition);
    long totalElements = reviewQueryPort.count(condition);

    List<UUID> reviewIds = reviewList.stream().map(Review::getId).toList();

    List<UUID> likeIdsByRequestId =
        reviewIds.isEmpty()
            ? List.of()
            : reviewLikeQueryPort.findReviewLikeIdsByRequestId(reviewIds, requestUserId);

    List<ReviewResponse> responses =
        reviewList.stream()
            .map(review -> ReviewResponse.of(review, likeIdsByRequestId.contains(review.getId())))
            .toList();

    return toCursorPage(responses, condition, totalElements);
  }

  // 자기 리뷰에도 좋아요 표시 가능
  @Override
  @Transactional
  public ReviewLikeResponse toggleLike(UUID reviewId, UUID requestUserId) {
    User user = userFinder.findByIdOrThrow(requestUserId);
    Review review = reviewRepositoryPort.getByIdOrThrow(reviewId);

    Optional<ReviewLike> foundReviewLike =
        reviewLikeRepositoryPort.getByUserIdAndReviewId(requestUserId, reviewId);

    ReviewLike reviewLike = toggleOrCreateReviewLike(foundReviewLike, review, user);

    // 알림 생성
    if (reviewLike.isLiked()) {
      eventPublisher.publishEvent(
          NotifyEvent.like(reviewId, review.getUser().getId(), user.getNickname()));

      eventPublisher.publishEvent(
          ActivityEvent.LikeAdded(
              UUID.randomUUID(),
              reviewId,
              review.getUser().getId(),
              user.getId(),
              review.getCreatedAt(),
              Instant.now()));
    }

    return ReviewLikeResponse.from(reviewLike);
  }

  @Override
  @Transactional
  public void softDeleteReview(UUID reviewId, UUID requestUserId) {
    userFinder.findByIdOrThrow(requestUserId);
    Review review = reviewRepositoryPort.getByIdOrThrow(reviewId);

    if (!review.verifyReviewUpdate(requestUserId)) {
      throw new BusinessException(ReviewErrorCode.FORBIDDEN);
    }

    review.softDelete();
    // 논리 삭제된 리뷰는 도서 집계에서 뺀다 (#42)
    bookReviewStatsCommand.removeReview(review.getBook(), review.getRating());
    // 알람 호출한다 - 미구현
  }

  /*
   리뷰 단일 삭제 ( 스웨거 문서 엔드포인트 용도 )
   연관 - 해당 리뷰 좋아요 + 댓글
  */
  @Override
  @Transactional
  public void hardDeleteReview(UUID reviewId, UUID requestUserId) {
    userFinder.findByIdOrThrow(requestUserId);
    Review review =
        reviewRepositoryPort
            .getByIdIncludingDeleted(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.NOT_FOUND));

    if (!review.getUser().getId().equals(requestUserId)) {
      throw new BusinessException(ReviewErrorCode.FORBIDDEN);
    }

    reviewLikeRepositoryPort.deleteAllByReviewId(reviewId);
    // 댓글 삭제 호출 - 아직 미구현

    // 논리 삭제를 거친 리뷰는 이미 집계에서 빠져 있다
    if (!review.isDeleted()) {
      bookReviewStatsCommand.removeReview(review.getBook(), review.getRating());
    }

    reviewRepositoryPort.delete(review);
  }

  private ReviewLike toggleOrCreateReviewLike(
      Optional<ReviewLike> foundReviewLike, Review review, User user) {
    ReviewLike reviewLike;
    if (foundReviewLike.isPresent()) {
      reviewLike = foundReviewLike.get();
      boolean liked = reviewLike.toggle();

      if (liked) {
        review.increaseLikeCount();
      } else {
        review.decreaseLikeCount();
      }
    } else {
      reviewLike = ReviewLike.init(review, user);
      reviewLikeRepositoryPort.save(reviewLike);
      review.increaseLikeCount();
    }
    return reviewLike;
  }

  private CursorPageResponse<ReviewResponse> toCursorPage(
      List<ReviewResponse> responses, ReviewSearchCondition condition, long totalElements) {
    return CursorPageResponse.of(
        responses,
        condition.limit(),
        response -> "rating".equals(condition.orderBy()) ? response.rating() : response.createdAt(),
        ReviewResponse::createdAt,
        totalElements);
  }
}
