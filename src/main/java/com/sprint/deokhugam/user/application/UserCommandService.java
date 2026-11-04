package com.sprint.deokhugam.user.application;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.user.application.dto.req.UserRegisterRequest;
import com.sprint.deokhugam.user.application.dto.res.UserDto;
import com.sprint.deokhugam.user.application.provided.command.UserActivityCommand;
import com.sprint.deokhugam.user.application.provided.command.UserCommand;
import com.sprint.deokhugam.user.application.required.PasswordHasher;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import com.sprint.deokhugam.user.domain.ranking.Period;
import com.sprint.deokhugam.user.domain.ranking.PeriodStats;
import com.sprint.deokhugam.user.domain.ranking.UserActivityMetrics;
import com.sprint.deokhugam.user.domain.ranking.UserRankingStats;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCommandService implements UserCommand, UserActivityCommand {

  private final UserRepositoryPort userRepositoryPort;
  private final PasswordHasher passwordHasher;

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Override
  public UserDto save(UserRegisterRequest request) {
    Email email = Email.to(request.email());

    if (userRepositoryPort.existsByEmail(email)) {
      throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
    }

    String encode = passwordHasher.hash(request.password());
    Password password = Password.to(encode);
    String nickname = request.nickname();
    User user = User.create(nickname, email, password);
    user.initializeRankingStats(LocalDate.now(SEOUL));
    userRepositoryPort.save(user);
    return UserDto.to(
        user.getId(), user.getEmail().value(), user.getNickname(), user.getCreatedAt());
  }

  @Override
  public void withdraw(UUID userId, UUID headerId) {
    if (!userId.equals(headerId)) {
      throw new BusinessException(UserErrorCode.FORBIDDEN);
    }
    User user = getUser(userId);

    user.softDelete();
  }

  @Override
  public void delete(UUID userId) {
    userRepositoryPort.remove(userId);
  }

  @Override
  public UserDto update(UUID userId, UUID headerId, String nickname) {
    if (!userId.equals(headerId)) {
      throw new BusinessException(UserErrorCode.FORBIDDEN);
    }
    User user = getUser(userId);
    user.updateNickname(nickname);

    return UserDto.to(
        user.getId(), user.getEmail().value(), user.getNickname(), user.getCreatedAt());
  }

  private @NonNull User getUser(UUID userId) {
    User user =
        userRepositoryPort
            .findById(userId)
            .filter(find -> !find.isDeleted())
            .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND));
    return user;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void apply(ActivityEvent event) {
    if (event == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    LocalDate occurredOn = event.occurredAt().atZone(SEOUL).toLocalDate();

    switch (event) {
      case ActivityEvent.LikeAdded e -> {
        LocalDate reviewCreatedOn = e.reviewCreatedAt().atZone(SEOUL).toLocalDate();

        // 좋아요를 누른 사람의 참여 지표
        updateUser(
            e.actorId(),
            occurredOn,
            period -> true,
            metrics -> metrics.changeParticipatedLikeCount(1));

        // 해당 기간에 작성한 리뷰의 기여도
        updateUser(
            e.authorId(),
            occurredOn,
            period -> period.covers(reviewCreatedOn),
            metrics -> metrics.changeReviewLikeCount(1));
      }

      case ActivityEvent.CommentAdded e -> {
        LocalDate reviewCreatedOn = e.reviewCreatedAt().atZone(SEOUL).toLocalDate();

        // 댓글을 작성한 사람의 참여 지표
        updateUser(
            e.actorId(),
            occurredOn,
            period -> true,
            metrics -> metrics.changeParticipatedCommentCount(1));

        // 해당 기간에 작성한 리뷰의 기여도
        updateUser(
            e.authorId(),
            occurredOn,
            period -> period.covers(reviewCreatedOn),
            metrics -> metrics.changeReviewCommentCount(1));
      }
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void closeRanking(UUID userId, LocalDate date) {
    User user =
        userRepositoryPort
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("마감 대상 유저가 없습니다: " + userId));

    UserRankingStats stats = user.getRankingStats();

    if (stats == null) {
      throw new IllegalStateException("유저 순위 통계 초기화가 필요합니다: " + userId);
    }

    user.updateRankingStats(stats.close(date));
  }

  private void updateUser(
      UUID userId,
      LocalDate occurredOn,
      Predicate<Period> condition,
      UnaryOperator<UserActivityMetrics> updater) {
    User user =
        userRepositoryPort
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT));

    UserRankingStats stats = user.getRankingStats();

    if (stats == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    // 날짜가 맞지 않는데 활동을 누적하거나 버리지 않도록 검사
    if (!stats.current().daily().covers(occurredOn)) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    PeriodStats current = stats.current();
    PeriodStats updatedCurrent = current;

    for (Period period :
        List.of(current.daily(), current.weekly(), current.monthly(), current.allTime())) {
      if (period.covers(occurredOn) && condition.test(period)) {
        UserActivityMetrics updatedMetrics = updater.apply(period.metrics());

        updatedCurrent = updatedCurrent.withPeriod(period.withMetrics(updatedMetrics));
      }
    }

    user.updateRankingStats(
        new UserRankingStats(updatedCurrent, stats.published(), stats.lastPublished()));
  }
}
