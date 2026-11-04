package com.sprint.deokhugam.review.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.deokhugam.book.application.provided.command.BookReviewStatsCommand;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.review.exception.ReviewErrorCode;
import com.sprint.deokhugam.review.application.required.ReviewRepositoryPort;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReviewApplicationServiceSoftDeleteTest {

  @Mock UserFinder userFinder;
  @Mock ReviewRepositoryPort reviewRepositoryPort;
  @Mock BookReviewStatsCommand bookReviewStatsCommand;
  @InjectMocks ReviewApplicationService service;

  private UUID userId;
  private UUID reviewId;
  private UUID bookId;
  private Book book;
  private User user;
  private Review review;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    reviewId = UUID.randomUUID();
    bookId = UUID.randomUUID();
    user = mock(User.class);
    book = mock(Book.class);
    review = Review.create(3, "내용", user, book);

    when(user.getId()).thenReturn(userId);
  }

  @Test
  void 리뷰_논리삭제_작성자가아니면실패() {
    // given
    UUID requestUserId = UUID.randomUUID();
    when(reviewRepositoryPort.getByIdOrThrow(reviewId)).thenReturn(review);

    // when
    BusinessException exception =
        assertThrows(
            BusinessException.class, () -> service.softDeleteReview(reviewId, requestUserId));

    // then
    assertThat(exception.getErrorCode()).isEqualTo(ReviewErrorCode.FORBIDDEN);
    assertThat(review.isDeleted()).isFalse();
    verify(bookReviewStatsCommand, never()).removeReview(book, 3);
  }

  @Test
  void 리뷰_논리삭제_성공() {
    // given
    when(userFinder.findByIdOrThrow(userId)).thenReturn(user);
    when(reviewRepositoryPort.getByIdOrThrow(reviewId)).thenReturn(review);

    // when
    service.softDeleteReview(reviewId, userId);

    // then
    assertThat(review.isDeleted()).isTrue();
    verify(bookReviewStatsCommand).removeReview(book, 3);
  }
}
