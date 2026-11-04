package com.sprint.deokhugam.user.adapter.persistence;

import static com.sprint.deokhugam.user.domain.QUser.user;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.user.application.dto.PowerUserCursor;
import com.sprint.deokhugam.user.application.dto.req.PowerUserSearchCond;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class UserQuerydslRepository {

  private final JPAQueryFactory jpaQueryFactory;

  List<UUID> findCleanupIds(Instant cutoff, int limit) {
    return jpaQueryFactory
        .select(user.id)
        .from(user)
        .where(user.deletedAt.isNotNull().and(user.deletedAt.loe(cutoff)))
        .orderBy(user.deletedAt.asc(), user.id.asc())
        .limit(limit)
        .fetch();
  }

  public List<User> findPowerUsers(PowerUserSearchCond condition, LocalDate publishedOn) {
    PowerUserCursor cursor = PowerUserCursor.decode(condition.cursor());

    if (cursor != null
        && (cursor.period() != condition.period() || !cursor.publishedOn().equals(publishedOn))) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "조회 기간 또는 공개 기준일이 다른 커서입니다.");
    }

    NumberPath<Long> score = scorePath(condition.period());

    return jpaQueryFactory
        .selectFrom(user)
        .where(
            powerUserScope(publishedOn),
            powerUserCursorCondition(score, condition.direction(), cursor, condition.after()))
        .orderBy(powerUserOrder(condition.period(), condition.direction()))
        .limit((long) condition.limit() + 1)
        .fetch();
  }

  public long countPowerUsers(LocalDate publishedOn) {
    Long count =
        jpaQueryFactory
            .select(user.count())
            .from(user)
            .where(powerUserScope(publishedOn))
            .fetchOne();

    return count == null ? 0L : count;
  }

  public long countPowerUserAhead(PowerUserCursor point, Instant createdAt) {
    NumberPath<Long> score = scorePath(point.period());

    BooleanExpression sameScore = score.eq(point.scoreUnits());

    BooleanExpression ahead =
        score
            .gt(point.scoreUnits())
            .or(sameScore.and(user.createdAt.lt(createdAt)))
            .or(sameScore.and(user.createdAt.eq(createdAt)).and(user.id.lt(point.userId())));
    Long count =
        jpaQueryFactory
            .select(user.count())
            .from(user)
            .where(powerUserScope(point.publishedOn()), ahead)
            .fetchOne();
    return count == null ? 0L : count;
  }

  private NumberPath<Long> scorePath(RankingPeriod period) {
    return switch (period) {
      case DAILY -> user.dailyScore;
      case WEEKLY -> user.weeklyScore;
      case MONTHLY -> user.monthlyScore;
      case ALL_TIME -> user.allTimeScore;
    };
  }

  private OrderSpecifier<?>[] powerUserOrder(RankingPeriod period, Direction direction) {
    NumberPath<Long> score = scorePath(period);

    if (direction == Direction.ASC) {
      return new OrderSpecifier<?>[] {score.desc(), user.createdAt.asc(), user.id.asc()};
    }

    return new OrderSpecifier<?>[] {score.asc(), user.createdAt.desc(), user.id.desc()};
  }

  private BooleanExpression powerUserCursorCondition(
      NumberPath<Long> score, Direction direction, PowerUserCursor cursor, Instant after) {
    if (cursor == null) {
      return null;
    }

    BooleanExpression sameScore = score.eq(cursor.scoreUnits());

    if (direction == Direction.ASC) {
      return score
          .lt(cursor.scoreUnits())
          .or(sameScore.and(user.createdAt.gt(after)))
          .or(sameScore.and(user.createdAt.eq(after)).and(user.id.gt(cursor.userId())));
    }

    return score
        .gt(cursor.scoreUnits())
        .or(sameScore.and(user.createdAt.lt(after)))
        .or(sameScore.and(user.createdAt.eq(after)).and(user.id.lt(cursor.userId())));
  }

  private BooleanExpression powerUserScope(LocalDate publishedOn) {
    return user.deletedAt
        .isNull()
        .and(user.rankingStats.isNotNull())
        .and(user.rankingPublishedOn.eq(publishedOn));
  }
}
