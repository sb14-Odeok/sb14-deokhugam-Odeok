package com.sprint.deokhugam.dashboard.book.domain;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.entity.BaseEntity;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
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
    name = "popular_books",
    uniqueConstraints = @UniqueConstraint(columnNames = {"period", "base_date", "book_id"}),
    indexes = @Index(name = "idx_popular_books_book_id", columnList = "book_id"))
public class PopularBook extends BaseEntity {

  private static final double REVIEW_COUNT_WEIGHT = 0.4;
  private static final double AVERAGE_RATING_WEIGHT = 0.6;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false)
  Book book;

  @Enumerated(EnumType.STRING)
  @Column(name = "period", nullable = false)
  RankingPeriod period;

  @Column(name = "base_date", nullable = false)
  LocalDate baseDate;

  @Column(name = "review_count", nullable = false)
  long reviewCount;

  @Column(name = "average_rating", nullable = false)
  double averageRating;

  @Column(name = "score", nullable = false)
  double score;

  @Column(name = "rank", nullable = false)
  long rank;

  public static PopularBook create(
      Book book,
      RankingPeriod period,
      LocalDate baseDate,
      long reviewCount,
      double averageRating,
      long rank) {
    validateRanking(book, period, baseDate, rank);
    double score = calculateScore(reviewCount, averageRating);

    return builder()
        .book(book)
        .period(period)
        .baseDate(baseDate)
        .reviewCount(reviewCount)
        .averageRating(averageRating)
        .score(score)
        .rank(rank)
        .build();
  }

  public static double calculateScore(long reviewCount, double averageRating) {
    validateReviewStats(reviewCount, averageRating);
    return reviewCount * REVIEW_COUNT_WEIGHT + averageRating * AVERAGE_RATING_WEIGHT;
  }

  private static void validateRanking(
      Book book, RankingPeriod period, LocalDate baseDate, long rank) {
    if (book == null || period == null || baseDate == null || rank < 1) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT, "인기 도서의 도서, 기간, 기준일은 필수이며 순위는 1 이상이어야 합니다.");
    }
  }

  private static void validateReviewStats(long reviewCount, double averageRating) {
    if (reviewCount < 1 || averageRating < 1 || averageRating > 5) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT, "인기 도서의 리뷰 수는 1 이상, 평균 평점은 1~5 사이여야 합니다.");
    }
  }
}
