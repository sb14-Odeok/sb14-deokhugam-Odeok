package com.sprint.deokhugam.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.deokhugam.book.application.provided.command.BookReviewStatsCommand;
import com.sprint.deokhugam.book.application.provided.query.BookFinder;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.comment.application.CommentCommandService;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.review.exception.ReviewErrorCode;
import com.sprint.deokhugam.review.application.dto.req.CreateReviewCommand;
import com.sprint.deokhugam.review.application.dto.res.ReviewResponse;
import com.sprint.deokhugam.review.application.required.ReviewLikeQueryPort;
import com.sprint.deokhugam.review.application.required.ReviewLikeRepositoryPort;
import com.sprint.deokhugam.review.application.required.ReviewQueryPort;
import com.sprint.deokhugam.review.application.required.ReviewRepositoryPort;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.User;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

// 단위테스트 용
@Slf4j
class ReviewCommandServiceTest {

  private ReviewRepositoryPort reviewRepositoryPort;
  private ReviewLikeRepositoryPort reviewLikeRepositoryPort;
  private CommentCommandService commentCommandService;
  private BookReviewStatsCommand bookReviewStatsCommand;
  private ReviewCleanupService reviewCleanupService;
  private ReviewApplicationService reviewApplicationService;
  private UUID userId;
  private UUID bookId;
  private Book book;
  private Review review;

  @BeforeEach
  void setUp() {
    reviewRepositoryPort = mock(ReviewRepositoryPort.class);
    bookReviewStatsCommand = mock(BookReviewStatsCommand.class);
    commentCommandService = mock(CommentCommandService.class);
    reviewLikeRepositoryPort = mock(ReviewLikeRepositoryPort.class);
    reviewCleanupService =
        new ReviewCleanupService(
            reviewRepositoryPort, reviewLikeRepositoryPort, commentCommandService);

    BookFinder bookFinder = mock(BookFinder.class);
    UserFinder userFinder = mock(UserFinder.class);
    reviewApplicationService =
        new ReviewApplicationService(
            mock(ReviewLikeQueryPort.class),
            reviewLikeRepositoryPort,
            mock(ReviewQueryPort.class),
            reviewRepositoryPort,
            bookReviewStatsCommand,
            bookFinder,
            userFinder,
            mock(ApplicationEventPublisher.class));

    userId = UUID.randomUUID();
    bookId = UUID.randomUUID();
    User user = mock(User.class);
    book = mock(Book.class);
    when(user.getId()).thenReturn(userId);
    when(book.getId()).thenReturn(bookId);
    when(userFinder.findByIdOrThrow(userId)).thenReturn(user);
    when(bookFinder.findById(bookId)).thenReturn(book);
    review = Review.create(5, "굳", user, book);
  }

  @Test
  void 리뷰저장_호출확인() {
    when(reviewRepositoryPort.save(any(Review.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ReviewResponse savedReview =
        reviewApplicationService.createReview(new CreateReviewCommand(userId, bookId, 5, "굳"));

    assertThat(savedReview.content()).isEqualTo(review.getContent());
    assertThat(savedReview.rating()).isEqualTo(5);
    verify(reviewRepositoryPort).existsByUserIdAndBookId(userId, bookId);
    verify(reviewRepositoryPort).save(any(Review.class));
    verify(bookReviewStatsCommand).addReview(book, 5);
  }

  @Test
  void 유저와도서가_중복된아이가있으면_실행안함() {
    when(reviewRepositoryPort.existsByUserIdAndBookId(userId, bookId)).thenReturn(true);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () ->
                reviewApplicationService.createReview(
                    new CreateReviewCommand(userId, bookId, 5, "굳")));

    assertThat(exception.getErrorCode()).isEqualTo(ReviewErrorCode.ALREADY_EXISTS);
    verify(reviewRepositoryPort, never()).save(any(Review.class));
    verify(bookReviewStatsCommand, never()).addReview(book, 5);
  }

  @Test
  void 리뷰_수정_정상동작확인() {
    UUID reviewId = UUID.randomUUID();
    when(reviewRepositoryPort.getByIdOrThrow(reviewId)).thenReturn(review);
    log.info("리뷰 id - {}, 리뷰 내용 - {}", review.getId(), review.getContent());

    ReviewResponse updated = reviewApplicationService.updateReview(userId, reviewId, "수정한 내용", 4);

    assertThat(review.getContent()).isEqualTo("수정한 내용");
    assertThat(updated.content()).isEqualTo("수정한 내용");
    assertThat(updated.rating()).isEqualTo(4);
    verify(reviewRepositoryPort).getByIdOrThrow(reviewId);
    verify(bookReviewStatsCommand).changeReviewRating(book, 5, 4);
  }

  @Test
  void 리뷰_주인작성자_아니면실패() {
    UUID reviewId = UUID.randomUUID();
    when(reviewRepositoryPort.getByIdOrThrow(reviewId)).thenReturn(review);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> reviewApplicationService.updateReview(UUID.randomUUID(), reviewId, "수정한 내용", 4));

    assertThat(exception.getErrorCode()).isEqualTo(ReviewErrorCode.FORBIDDEN);
    assertThat(review.getContent()).isEqualTo("굳");
    assertThat(review.getRating()).isEqualTo(5);
    verify(bookReviewStatsCommand, never()).changeReviewRating(book, 5, 4);
  }

  @Test
  void 도서_물리삭제_리뷰정리_좋아요_댓글_리뷰순서로지운다() {
    List<UUID> reviewIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(reviewRepositoryPort.getIdsByBookId(bookId)).thenReturn(reviewIds);
    when(reviewRepositoryPort.deleteAllByIds(reviewIds)).thenReturn(2L);

    long deleted = reviewCleanupService.deleteReviewsWithRelatedDataByBook(book);

    assertThat(deleted).isEqualTo(2L);
    InOrder inOrder =
        inOrder(reviewLikeRepositoryPort, commentCommandService, reviewRepositoryPort);
    inOrder.verify(reviewLikeRepositoryPort).deleteAllByReviewIds(reviewIds);
    inOrder.verify(commentCommandService).deleteAllByReviewIds(reviewIds);
    inOrder.verify(reviewRepositoryPort).deleteAllByIds(reviewIds);
  }

  @Test
  void 도서_물리삭제_리뷰가없으면_아무것도지우지않는다() {
    when(reviewRepositoryPort.getIdsByBookId(bookId)).thenReturn(List.of());

    long deleted = reviewCleanupService.deleteReviewsWithRelatedDataByBook(book);

    assertThat(deleted).isZero();
    verify(reviewLikeRepositoryPort, never()).deleteAllByReviewIds(List.of());
    verify(commentCommandService, never()).deleteAllByReviewIds(List.of());
    verify(reviewRepositoryPort, never()).deleteAllByIds(List.of());
  }
}
