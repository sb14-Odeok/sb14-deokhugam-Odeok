package com.sprint.deokhugam.review.adapter.persistence;

import static com.sprint.deokhugam.book.domain.QBook.book;
import static com.sprint.deokhugam.review.domain.QReview.review;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.review.application.dto.req.ReviewSearchCondition;
import com.sprint.deokhugam.review.application.dto.res.BookReviewStatsResponse;
import com.sprint.deokhugam.review.application.provided.query.ReviewAggregationRange;
import com.sprint.deokhugam.review.domain.Review;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class ReviewQuerydslRepository {
  private static final String ORDER_BY_RATING = "rating";
  private static final String ORDER_BY_CREATED_AT = "createdAt";

  private final JPAQueryFactory queryFactory;

  public List<Review> search(ReviewSearchCondition condition) {
    return queryFactory
        .selectFrom(review)
        .join(review.book)
        .fetchJoin()
        .join(review.user)
        .fetchJoin()
        .where(
            review.deletedAt.isNull(),
            keywordCondition(condition.keyword()),
            userIdCondition(condition.userId()),
            bookIdCondition(condition.bookId()),
            cursorCondition(
                condition.cursor(), condition.after(), condition.orderBy(), condition.direction()))
        .orderBy(orderSpecifiers(condition.orderBy(), condition.direction()))
        .limit(condition.limit() + 1L)
        .fetch();
  }

  public long count(ReviewSearchCondition condition) {
    Long count =
        queryFactory
            .select(review.count())
            .from(review)
            .where(
                review.deletedAt.isNull(),
                keywordCondition(condition.keyword()),
                userIdCondition(condition.userId()),
                bookIdCondition(condition.bookId()))
            .fetchOne();
    return count == null ? 0L : count;
  }

  public List<UUID> getIdsByBookId(UUID bookId) {
    return queryFactory.select(review.id).from(review).where(review.book.id.eq(bookId)).fetch();
  }

  public long deleteAllByIds(List<UUID> reviewIds) {
    return queryFactory.delete(review).where(review.id.in(reviewIds)).execute();
  }

  public List<UUID> getIdsByUserIdIncludingDeleted(UUID userId) {
    return queryFactory.select(review.id).from(review).where(review.user.id.eq(userId)).fetch();
  }

  public List<BookReviewStatsResponse> aggregateByBook(ReviewAggregationRange range) {
    if (range == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
    return queryFactory
        .select(
            Projections.constructor(
                BookReviewStatsResponse.class, book, review.id.count(), review.rating.avg()))
        .from(review)
        .join(review.book, book)
        .where(review.deletedAt.isNull(), book.deletedAt.isNull(), createdInRange(range))
        .groupBy(book)
        .fetch();
  }

  public void increaseCommentCount(UUID reviewId) {
    queryFactory
        .update(review)
        .set(review.commentCount, review.commentCount.add(1))
        .where(review.id.eq(reviewId), review.deletedAt.isNull())
        .execute();
  }

  public void decreaseCommentCount(UUID reviewId) {
    queryFactory
        .update(review)
        .set(review.commentCount, review.commentCount.subtract(1))
        .where(review.id.eq(reviewId), review.deletedAt.isNull())
        .execute();
  }

  private BooleanExpression createdInRange(ReviewAggregationRange range) {
    return switch (range) {
      case ReviewAggregationRange.Between r ->
          review.createdAt.goe(r.startAt()).and(review.createdAt.lt(r.endAt()));
      case ReviewAggregationRange.AllTime ignored -> null;
    };
  }

  /*
     like -> eq : equals  contains -> contains
     결과 :   like 검색조건   like %{검색 조건}%

     SELECT * FROM reviews
      WHERE deleted_at IS NULL
        AND ( ---------- 커서 컨디션
          (rating, created_at) < (4, '날짜')
        )
      ORDER BY rating/생성일 디렉션, rating/생성일 디렉션
      LIMIT 사이즈 + 1;
  */
  private BooleanExpression keywordCondition(String keyword) {
    if (keyword == null || keyword.isBlank()) return null;

    String value = keyword.strip();
    return review
        .user
        .nickname
        .containsIgnoreCase(value)
        .or(review.content.containsIgnoreCase(value))
        .or(review.book.title.containsIgnoreCase(value));
  }

  private BooleanExpression userIdCondition(UUID userId) {
    return userId == null ? null : review.user.id.eq(userId);
  }

  private BooleanExpression bookIdCondition(UUID bookId) {
    return bookId == null ? null : review.book.id.eq(bookId);
  }

  // 정렬 조건 - 기본값 생성일 / 내림차순
  private OrderSpecifier<?>[] orderSpecifiers(String conditionOrderBy, String conditionDirection) {
    String orderBy =
        conditionOrderBy == null ? ORDER_BY_CREATED_AT : conditionOrderBy; // 기본 정렬 조건 - 생성일

    Order direction = sortDirection(conditionDirection);
    OrderSpecifier<?> createdAtOrder = new OrderSpecifier<>(direction, review.createdAt);
    OrderSpecifier<?> ratingOrder = new OrderSpecifier<>(direction, review.rating);

    return switch (orderBy) {
      case ORDER_BY_CREATED_AT -> new OrderSpecifier<?>[] {createdAtOrder};
      case ORDER_BY_RATING -> new OrderSpecifier<?>[] {ratingOrder, createdAtOrder};
      default ->
          throw new BusinessException(
              CommonErrorCode.INVALID_INPUT, "잘못된 정렬조건 = " + conditionOrderBy);
    };
  }

  private Order sortDirection(String direction) {
    if (direction == null) return Order.DESC; // 내림차순 기본값

    return switch (direction.toUpperCase(Locale.ROOT)) {
      case "ASC" -> Order.ASC;
      case "DESC" -> Order.DESC;
      default ->
          throw new BusinessException(CommonErrorCode.INVALID_INPUT, "잘못된 정렬방향 = " + direction);
    };
  }

  private BooleanExpression cursorCondition(
      String cursor, Instant after, String orderBy, String direction) {
    if (cursor == null && after == null) return null; // 최초엔 널

    if (cursor == null || after == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "cursor와 after를 함께 입력하세요");
    }

    boolean asc = sortDirection(direction) == Order.ASC;

    // 평점 기준으로 정렬되어 있을 경우
    if (ORDER_BY_RATING.equals(orderBy)) {
      int lastRating = Integer.parseInt(cursor);
      // OR로 풀어 쓰면 idx_reviews_rating_created_at_id를 범위로 타지 못해서 행 비교로 쓴다
      String rowCompare = asc ? "({0}, {1}) > ({2}, {3})" : "({0}, {1}) < ({2}, {3})";
      return Expressions.booleanTemplate(
          rowCompare, review.rating, review.createdAt, lastRating, after);
    }

    // 생성일 기준으로 정렬되어 있을 경우 - 애프터 필요없음
    Instant lastCreatedAt = Instant.parse(cursor);
    return asc ? review.createdAt.gt(lastCreatedAt) : review.createdAt.lt(lastCreatedAt);
  }
}
