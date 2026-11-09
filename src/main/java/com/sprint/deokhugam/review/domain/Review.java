package com.sprint.deokhugam.review.domain;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.entity.BaseSoftDeletableEntity;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.review.domain.exception.ReviewErrorCode;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(
    name = "reviews",
    indexes = {
      @Index(
          name = "idx_reviews_created_at_id",
          columnList = "created_at DESC, id DESC",
          options = "WHERE deleted_at IS NULL"),
      @Index(
          name = "idx_reviews_book_created_at_id",
          columnList = "book_id, created_at DESC, id DESC"),
      @Index(
          name = "idx_reviews_user_created_at_id",
          columnList = "user_id, created_at DESC, id DESC"),
      @Index(
          name = "idx_reviews_rating_created_at_id",
          columnList = "rating DESC, created_at DESC, id DESC",
          options = "WHERE deleted_at IS NULL")
    })
public class Review extends BaseSoftDeletableEntity {

  @Column(name = "rating", nullable = false)
  Integer rating;

  @Column(name = "content", nullable = false)
  String content;

  @Builder.Default
  @Column(name = "like_count", nullable = false)
  Integer likeCount = 0;

  @Builder.Default
  @Column(name = "comment_count", nullable = false)
  Integer commentCount = 0;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false)
  Book book;

  public static Review create(Integer rate, String content, User user, Book book) {
    validateRatingAndContent(rate, content);
    return builder().rating(rate).content(content).user(user).book(book).build();
  }

  public void updateContentAndRating(String content, int rating) {
    validateRatingAndContent(rating, content);
    this.content = content;
    this.rating = rating;
  }

  public boolean verifyReviewUpdate(UUID userId) {
    return this.getUser().getId().equals(userId);
  }

  public void increaseLikeCount() {
    likeCount++;
  }

  public void decreaseLikeCount() {
    validateLikeCountCanDecrease();
    likeCount--;
  }

  private static void validateRatingAndContent(Integer rating, String content) {
    if (Objects.isNull(rating) || rating < 1 || rating > 5) {
      throw new BusinessException(ReviewErrorCode.INVALID_RATING);
    }
    if (Objects.isNull(content) || content.length() > 200) {
      throw new BusinessException(ReviewErrorCode.INVALID_CONTENT);
    }
  }

  private void validateLikeCountCanDecrease() {
    if (likeCount <= 0) throw new BusinessException(ReviewErrorCode.LIKE_COUNT_UNDERFLOW);
  }
}
