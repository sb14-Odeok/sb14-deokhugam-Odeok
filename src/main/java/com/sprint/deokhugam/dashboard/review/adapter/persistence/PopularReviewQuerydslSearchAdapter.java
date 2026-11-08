package com.sprint.deokhugam.dashboard.review.adapter.persistence;

import static com.sprint.deokhugam.book.domain.QBook.book;
import static com.sprint.deokhugam.comment.domain.QComment.comment;
import static com.sprint.deokhugam.dashboard.review.domain.QPopularReview.popularReview;
import static com.sprint.deokhugam.review.domain.QReview.review;
import static com.sprint.deokhugam.review.domain.QReviewLike.reviewLike;
import static com.sprint.deokhugam.user.domain.QUser.user;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewDto;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewSearchCondition;
import com.sprint.deokhugam.dashboard.review.application.dto.ReviewActivity;
import com.sprint.deokhugam.dashboard.review.application.required.PopularReviewSearchPort;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 저장된 순위 조회와 배치용 좋아요/댓글 집계
@Repository
@RequiredArgsConstructor
public class PopularReviewQuerydslSearchAdapter implements PopularReviewSearchPort {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<PopularReviewDto> search(PopularReviewSearchCondition condition) {
    return queryFactory
        .select(
            // Dto 클래스의 생성자에 매개변수로 직접 전달하여 객체로 매핑
            Projections.constructor(
                PopularReviewDto.class,
                popularReview.id,
                review.id,
                book.id,
                book.title,
                book.thumbnailUrl,
                user.id,
                user.nickname,
                review.content,
                review.rating.doubleValue(),
                popularReview.period,
                popularReview.createdAt,
                popularReview.rank,
                popularReview.score,
                popularReview.likeCount,
                popularReview.commentCount))
        .from(popularReview)
        .join(review)
        .on(review.id.eq(popularReview.reviewId))
        .join(review.book, book)
        .join(review.user, user)
        .where(
            popularReview.period.eq(condition.period()),
            review.deletedAt.isNull(),
            // 커서 기반 페이지네이션 -> 필터링
            cursorCondition(condition))
        // 정렬
        .orderBy(orderSpecifier(condition))
        .limit(condition.limit() + 1L)
        .fetch();
  }

  @Override
  public long count(PopularReviewSearchCondition condition) {
    Long count =
        queryFactory
            .select(popularReview.count())
            .from(popularReview)
            .join(review)
            .on(review.id.eq(popularReview.reviewId))
            .where(popularReview.period.eq(condition.period()), review.deletedAt.isNull())
            // 단건 조회
            .fetchOne();

    return count == null ? 0L : count;
  }

  // 인기 리뷰는 순위로만 정렬
  private OrderSpecifier<?> orderSpecifier(PopularReviewSearchCondition condition) {
    return condition.isDescending() ? popularReview.rank.asc() : popularReview.rank.desc();
  }

  // cursor에는 이전 페이지 마지막 항목의 순위
  private BooleanExpression cursorCondition(PopularReviewSearchCondition condition) {
    if (condition.isFirstPage()) {
      return null;
    }

    return condition.isDescending()
        // ls(less then, <), gt(greater then, >)
        ? popularReview.rank.gt(condition.cursor())
        : popularReview.rank.lt(condition.cursor());
  }

  // 활동 없는 리뷰는 제외
  @Override
  public List<ReviewActivity> findReviewActivities(PopularReviewPeriod.Range range) {
    Map<UUID, Long> likeCounts = countLikesByReview(range);
    Map<UUID, Long> commentCounts = countCommentsByReview(range);

    Set<UUID> reviewIds = new HashSet<>(likeCounts.keySet());
    reviewIds.addAll(commentCounts.keySet());

    // 좋아요 수와 댓글 수를 리뷰별로 묶어서 센 뒤 합친다
    return reviewIds.stream()
        .map(
            reviewId ->
                new ReviewActivity(
                    reviewId,
                    // reviewId가 없다면 0 반환
                    likeCounts.getOrDefault(reviewId, 0L),
                    commentCounts.getOrDefault(reviewId, 0L)))
        .toList();
  }

  // 삭제된 리뷰, 취소된 좋아요는 제외
  private Map<UUID, Long> countLikesByReview(PopularReviewPeriod.Range range) {
    NumberExpression<Long> likeCount = reviewLike.count();

    return queryFactory
        .select(reviewLike.review.id, likeCount)
        .from(reviewLike)
        .join(reviewLike.review, review)
        .where(
            review.deletedAt.isNull(),
            reviewLike.liked.isTrue(),
            inPeriod(reviewLike.createdAt, range))
        .groupBy(reviewLike.review.id)
        .fetch()
        .stream()
        .collect(
            Collectors.toMap(
                tuple -> tuple.get(reviewLike.review.id), tuple -> tuple.get(likeCount)));
  }

  // 삭제된 리뷰는 제외. 논리 삭제된 댓글은 점수에 포함
  private Map<UUID, Long> countCommentsByReview(PopularReviewPeriod.Range range) {
    NumberExpression<Long> commentCount = comment.count();

    return queryFactory
        .select(comment.review.id, commentCount)
        .from(comment)
        .join(comment.review, review)
        .where(review.deletedAt.isNull(), inPeriod(comment.createdAt, range))
        .groupBy(comment.review.id)
        .fetch()
        .stream()
        .collect(
            Collectors.toMap(
                tuple -> tuple.get(comment.review.id), tuple -> tuple.get(commentCount)));
  }

  @Override
  public List<UUID> findReviewsForNotify(PopularReviewPeriod period, int limit) {
    return queryFactory
        .select(popularReview.reviewId)
        .from(popularReview)
        .where(popularReview.period.eq(period).and(popularReview.rank.between(1L, limit)))
        .orderBy(popularReview.rank.asc())
        .fetch();
  }

  // 시작 시각 이상, 끝 시각 미만. 역대(ALL_TIME)는 기간 제한이 없어서 조건 생략(null)
  private BooleanExpression inPeriod(
      DateTimePath<Instant> createdAt, PopularReviewPeriod.Range range) {
    if (range.startAt() == null) {
      return null;
    }
    return createdAt.goe(range.startAt()).and(createdAt.lt(range.endAt()));
  }
}
