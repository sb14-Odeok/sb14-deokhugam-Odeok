package com.sprint.deokhugam.book.domain;

import com.sprint.deokhugam.common.entity.BaseSoftDeletableEntity;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@Table(name = "books")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseSoftDeletableEntity {

  public static final int TITLE_MAX_LENGTH = 255;
  public static final int AUTHOR_MAX_LENGTH = 255;
  public static final int PUBLISHER_MAX_LENGTH = 255;

  @Column(nullable = false, length = TITLE_MAX_LENGTH)
  private String title;

  @Column(nullable = false, length = AUTHOR_MAX_LENGTH)
  private String author;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(nullable = false, length = PUBLISHER_MAX_LENGTH)
  private String publisher;

  @Column(name = "published_date", nullable = false)
  private LocalDate publishedDate;

  // DB에는 IsbnConverter가 숫자 13자리 문자열로 넣는다
  @Column(unique = true, updatable = false, length = Isbn.LENGTH)
  private Isbn isbn;

  @Column(name = "thumbnail_url", length = 2048)
  private String thumbnailUrl;

  @Column(name = "review_count", nullable = false)
  private int reviewCount;

  @Column(name = "rating_sum", nullable = false)
  private long ratingSum;

  @Column(nullable = false)
  private double rating;

  // 동시에 같은 도서를 고치면 나중에 커밋한 쪽이 실패해 앞의 변경을 덮어쓰지 않는다
  @Version
  @ColumnDefault("0")
  @Column(nullable = false)
  private long version;

  private Book(
      String title,
      String author,
      String description,
      String publisher,
      LocalDate publishedDate,
      Isbn isbn,
      String thumbnailUrl) {
    validateInfo(title, author, publisher);
    this.title = title;
    this.author = author;
    this.description = description;
    this.publisher = publisher;
    this.publishedDate = publishedDate;
    this.isbn = isbn;
    this.thumbnailUrl = thumbnailUrl;
  }

  public static Book create(
      String title,
      String author,
      String description,
      String publisher,
      LocalDate publishedDate,
      Isbn isbn,
      String thumbnailUrl) {
    return new Book(title, author, description, publisher, publishedDate, isbn, thumbnailUrl);
  }

  public void update(
      String title, String author, String description, String publisher, LocalDate publishedDate) {
    validateInfo(title, author, publisher);
    this.title = title;
    this.author = author;
    this.description = description;
    this.publisher = publisher;
    this.publishedDate = publishedDate;
  }

  public void changeThumbnailUrl(String thumbnailUrl) {
    this.thumbnailUrl = thumbnailUrl;
  }

  public void addReview(int score) {
    reviewCount++;
    ratingSum += score;
    recalculateRating();
  }

  public void removeReview(int score) {
    if (reviewCount == 0) {
      throw new IllegalStateException("리뷰 수가 0이라 더 뺄 수 없습니다.");
    }
    reviewCount--;
    ratingSum -= score;
    recalculateRating();
  }

  public void changeReviewScore(int oldScore, int newScore) {
    ratingSum += newScore - oldScore;
    recalculateRating();
  }

  private void recalculateRating() {
    rating = reviewCount == 0 ? 0 : (double) ratingSum / reviewCount;
  }

  private static void validateInfo(String title, String author, String publisher) {
    validateLength(title, TITLE_MAX_LENGTH, "title");
    validateLength(author, AUTHOR_MAX_LENGTH, "author");
    validateLength(publisher, PUBLISHER_MAX_LENGTH, "publisher");
  }

  private static void validateLength(String value, int maxLength, String field) {
    if (value != null && value.length() > maxLength) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT, field + " 길이 초과: " + value.length() + "/" + maxLength);
    }
  }
}
