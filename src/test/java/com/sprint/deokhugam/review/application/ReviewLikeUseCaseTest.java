package com.sprint.deokhugam.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.review.exception.ReviewErrorCode;
import com.sprint.deokhugam.review.application.dto.res.ReviewLikeResponse;
import com.sprint.deokhugam.review.application.required.ReviewLikeRepositoryPort;
import com.sprint.deokhugam.review.application.required.ReviewRepositoryPort;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.review.domain.ReviewLike;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewLikeUseCaseTest {

  @Mock ReviewLikeRepositoryPort reviewLikeRepositoryPort;
  @Mock ReviewRepositoryPort reviewRepositoryPort;
  @Mock UserFinder userFinder;
  @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

  @InjectMocks ReviewApplicationService reviewApplicationService;

  private UUID userId;
  private UUID reviewId;
  private User user;
  private Review review;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    reviewId = UUID.randomUUID();

    user = User.create("jh", Email.to("sdadsa@naver.com"), Password.to("password"));

    Book book = Book.create("제목", "저자", null, null, null, null, null);

    review = Review.create(4, "좋아요", user, book);

    ReflectionTestUtils.setField(user, "id", userId);
    ReflectionTestUtils.setField(review, "id", reviewId);
  }

  @Test
  void 리뷰좋아요_새로_생성될경우() {
    // given
    when(userFinder.findByIdOrThrow(userId)).thenReturn(user);
    when(reviewRepositoryPort.getByIdOrThrow(reviewId)).thenReturn(review);
    when(reviewLikeRepositoryPort.getByUserIdAndReviewId(userId, reviewId))
        .thenReturn(Optional.empty());

    // when
    ReviewLikeResponse response = reviewApplicationService.toggleLike(reviewId, userId);

    // then
    assertThat(response.liked()).isTrue();
    assertThat(response.reviewId()).isEqualTo(reviewId);
    assertThat(response.userId()).isEqualTo(userId);
    assertThat(review.getLikeCount()).isEqualTo(1);
    verify(reviewLikeRepositoryPort).save(any(ReviewLike.class));
  }

  @Test
  void 리뷰좋아요_토클확인_감소확인() {
    // given
    ReviewLike existing = ReviewLike.init(review, user);
    review.increaseLikeCount();

    when(userFinder.findByIdOrThrow(userId)).thenReturn(user);
    when(reviewRepositoryPort.getByIdOrThrow(reviewId)).thenReturn(review);
    when(reviewLikeRepositoryPort.getByUserIdAndReviewId(userId, reviewId))
        .thenReturn(Optional.of(existing));

    // when
    ReviewLikeResponse response = reviewApplicationService.toggleLike(reviewId, userId);

    // then
    assertThat(response.liked()).isFalse();
    assertThat(existing.isLiked()).isFalse();
    assertThat(review.getLikeCount()).isZero();
    verify(reviewLikeRepositoryPort, never()).save(any(ReviewLike.class));
  }

  @Test
  void 리뷰좋아요_감소_음수실퍠() {
    BusinessException exception = assertThrows(BusinessException.class, review::decreaseLikeCount);

    assertThat(exception.getErrorCode()).isEqualTo(ReviewErrorCode.LIKE_COUNT_UNDERFLOW);
    assertThat(review.getLikeCount()).isZero();
  }
}
