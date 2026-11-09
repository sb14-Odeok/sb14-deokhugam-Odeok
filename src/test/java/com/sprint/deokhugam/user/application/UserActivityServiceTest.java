package com.sprint.deokhugam.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.user.UserTestFixtures;
import com.sprint.deokhugam.user.application.required.PasswordHasher;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserActivityServiceTest {
  private final UserRepositoryPort repository = mock(UserRepositoryPort.class);
  private final UserCommandService service =
      new UserCommandService(repository, mock(PasswordHasher.class));
  private final UUID actorId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();
  private final LocalDate date = UserTestFixtures.DATE;
  private final Instant occurredAt = date.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();

  private User givenUser(UUID id) {
    User user = UserTestFixtures.user(id);
    when(repository.findById(id)).thenReturn(Optional.of(user));
    return user;
  }

  private ActivityEvent like(UUID author, UUID actor, Instant reviewCreatedAt) {
    return ActivityEvent.LikeAdded(
        UUID.randomUUID(), UUID.randomUUID(), author, actor, reviewCreatedAt, occurredAt);
  }

  @Test
  void 좋아요는_행위자_참여와_작성자_리뷰_지표에_각각_누적된다() {
    User actor = givenUser(actorId);
    User author = givenUser(authorId);
    service.apply(like(authorId, actorId, occurredAt.minusSeconds(60)));
    assertThat(actor.getRankingStats().current().daily().metrics().participatedLikeCount())
        .isEqualTo(1);
    assertThat(actor.getRankingStats().current().allTime().metrics().participatedLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().daily().metrics().reviewLikeCount()).isEqualTo(1);
    assertThat(author.getRankingStats().current().weekly().metrics().reviewLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().monthly().metrics().reviewLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().allTime().metrics().reviewLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().published().allTime().metrics().reviewLikeCount()).isZero();
    assertThat(author.getAllTimeScore()).isZero();
  }

  @Test
  void 댓글은_행위자와_작성자의_다른_지표에_누적된다() {
    User actor = givenUser(actorId);
    User author = givenUser(authorId);
    service.apply(
        ActivityEvent.CommentAdded(
            UUID.randomUUID(), UUID.randomUUID(), authorId, actorId, occurredAt, occurredAt));
    assertThat(actor.getRankingStats().current().daily().metrics().participatedCommentCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().daily().metrics().reviewCommentCount())
        .isEqualTo(1);
    assertThat(actor.getRankingStats().current().daily().metrics().reviewCommentCount()).isZero();
    assertThat(author.getRankingStats().current().daily().metrics().participatedCommentCount())
        .isZero();
  }

  @Test
  void 어제_작성한_리뷰는_오늘_작성자의_일간_기여도에_포함되지_않는다() {
    User actor = givenUser(actorId);
    User author = givenUser(authorId);
    service.apply(like(authorId, actorId, occurredAt.minusSeconds(86400)));
    assertThat(actor.getRankingStats().current().daily().metrics().participatedLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().daily().metrics().reviewLikeCount()).isZero();
    assertThat(author.getRankingStats().current().weekly().metrics().reviewLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().monthly().metrics().reviewLikeCount())
        .isEqualTo(1);
  }

  @Test
  void 지난달_리뷰에_받은_반응은_작성자의_전체_기간에만_누적된다() {
    givenUser(actorId);
    User author = givenUser(authorId);
    service.apply(like(authorId, actorId, occurredAt.minusSeconds(40L * 86400)));
    assertThat(author.getRankingStats().current().daily().metrics().reviewLikeCount()).isZero();
    assertThat(author.getRankingStats().current().weekly().metrics().reviewLikeCount()).isZero();
    assertThat(author.getRankingStats().current().monthly().metrics().reviewLikeCount()).isZero();
    assertThat(author.getRankingStats().current().allTime().metrics().reviewLikeCount())
        .isEqualTo(1);
  }

  @Test
  void 자기_리뷰의_좋아요는_동일_유저의_두_지표에_모두_누적된다() {
    User user = givenUser(actorId);
    service.apply(like(actorId, actorId, occurredAt));
    assertThat(user.getRankingStats().current().daily().metrics().participatedLikeCount())
        .isEqualTo(1);
    assertThat(user.getRankingStats().current().daily().metrics().reviewLikeCount()).isEqualTo(1);
    assertThat(user.getRankingStats().current().daily().scoreUnits()).isEqualTo(35);
  }

  @Test
  void 활동일은_UTC가_아닌_한국_시간으로_판정한다() {
    User actor = givenUser(actorId);
    givenUser(authorId);
    Instant midnight = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
    service.apply(
        ActivityEvent.LikeAdded(
            UUID.randomUUID(), UUID.randomUUID(), authorId, actorId, midnight, midnight));
    assertThat(actor.getRankingStats().current().daily().metrics().participatedLikeCount())
        .isEqualTo(1);
  }

  @Test
  void 이벤트가_없으면_잘못된_입력으로_거부한다() {
    assertThatThrownBy(() -> service.apply(null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
    verifyNoInteractions(repository);
  }

  @Test
  void 다음날_마감은_공개_점수와_공개일을_갱신한다() {
    User user = givenUser(actorId);
    service.apply(like(actorId, actorId, occurredAt));
    service.closeRanking(actorId, date.plusDays(1));
    assertThat(user.getDailyScore()).isEqualTo(35);
    assertThat(user.getAllTimeScore()).isEqualTo(35);
    assertThat(user.getRankingPublishedOn()).isEqualTo(date.plusDays(1));
    assertThat(user.getRankingStats().current().daily().scoreUnits()).isZero();
  }
}
