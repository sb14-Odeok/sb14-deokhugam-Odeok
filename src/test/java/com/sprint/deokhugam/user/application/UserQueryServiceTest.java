package com.sprint.deokhugam.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.user.UserTestFixtures;
import com.sprint.deokhugam.user.application.dto.PowerUserCursor;
import com.sprint.deokhugam.user.application.dto.query.LoginUserQuery;
import com.sprint.deokhugam.user.application.dto.query.PowerUserSearchCond;
import com.sprint.deokhugam.user.application.dto.result.PowerUserResult;
import com.sprint.deokhugam.user.application.required.PasswordHasher;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import com.sprint.deokhugam.user.domain.ranking.Period;
import com.sprint.deokhugam.user.domain.ranking.PeriodStats;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import com.sprint.deokhugam.user.domain.ranking.UserActivityMetrics;
import com.sprint.deokhugam.user.domain.ranking.UserRankingStats;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Sort.Direction;

class UserQueryServiceTest {
  private final UserRepositoryPort repository = mock(UserRepositoryPort.class);
  private final PasswordHasher hasher = mock(PasswordHasher.class);
  private final UserQueryService service = new UserQueryService(repository, hasher);
  private final UUID id = UUID.randomUUID();

  @Test
  void 활성_유저의_상세_정보를_반환한다() {
    User user = UserTestFixtures.user(id);
    when(repository.findById(id)).thenReturn(Optional.of(user));
    assertThat(service.getUserDetail(id).id()).isEqualTo(id);
    assertThat(service.getUserDetail(id).email()).isEqualTo(user.getEmail().value());
  }

  @Test
  void 없는_유저는_조회할_수_없다() {
    when(repository.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findByIdOrThrow(id))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
  }

  @Test
  void 탈퇴한_유저는_조회할_수_없다() {
    User user = UserTestFixtures.user(id);
    user.softDelete();
    when(repository.findById(id)).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.getUserDetail(id))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
  }

  @Test
  void 비밀번호가_일치하면_로그인_결과를_반환한다() {
    User user = UserTestFixtures.user(id);
    when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    when(hasher.matches("Password1!", "encoded")).thenReturn(true);
    assertThat(service.login(new LoginUserQuery(user.getEmail().value(), "Password1!")).id())
        .isEqualTo(id);
  }

  @Test
  void 잘못된_비밀번호는_로그인을_거부한다() {
    User user = UserTestFixtures.user(id);
    when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.login(new LoginUserQuery(user.getEmail().value(), "wrong")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.LOGIN_FAILED);
  }

  @Test
  void 없는_이메일은_비밀번호_비교_없이_동일한_로그인_실패를_반환한다() {
    when(repository.findByEmail(Email.to("missing@example.com"))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.login(new LoginUserQuery("missing@example.com", "Password1!")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.LOGIN_FAILED);
    verifyNoInteractions(hasher);
  }

  @Test
  void 탈퇴한_유저는_비밀번호가_맞아도_로그인할_수_없다() {
    User user = UserTestFixtures.user(id);
    user.softDelete();
    when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () -> service.login(new LoginUserQuery(user.getEmail().value(), "Password1!")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.LOGIN_FAILED);
    verifyNoInteractions(hasher);
  }

  @ParameterizedTest
  @CsvSource({
    "DAILY,0.15,0.3,0,0",
    "WEEKLY,0.35,0.7,0,0",
    "MONTHLY,0.20,0.0,1,0",
    "ALL_TIME,0.30,0.0,0,1"
  })
  void 기간에_맞는_공개_점수와_활동_지표를_반환한다(
      RankingPeriod period, double score, double reviewScore, long likes, long comments) {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    User user = UserTestFixtures.user(id, today);
    PeriodStats published =
        new PeriodStats(
            new Period.Daily(today.minusDays(1), new UserActivityMetrics(1, 0, 0, 0)),
            new Period.Weekly(today.minusWeeks(1), new UserActivityMetrics(0, 1, 0, 0)),
            new Period.Monthly(today.minusMonths(1), new UserActivityMetrics(0, 0, 1, 0)),
            new Period.AllTime(new UserActivityMetrics(0, 0, 0, 1)));
    user.updateRankingStats(
        new UserRankingStats(user.getRankingStats().current(), published, today));
    PowerUserSearchCond condition = new PowerUserSearchCond(period, Direction.ASC, null, null, 20);
    when(repository.findPowerUsers(eq(condition), any(LocalDate.class))).thenReturn(List.of(user));
    when(repository.countPowerUsers(any(LocalDate.class))).thenReturn(1L);

    PowerUserResult result = service.findPowerUsers(condition).content().getFirst();

    assertThat(result.period()).isEqualTo(period);
    assertThat(result.rank()).isEqualTo(1);
    assertThat(result.score()).isEqualTo(score);
    assertThat(result.reviewScoreSum()).isEqualTo(reviewScore);
    assertThat(result.likeCount()).isEqualTo(likes);
    assertThat(result.commentCount()).isEqualTo(comments);
  }

  @Test
  void 다음_페이지가_있으면_마지막_응답_항목으로_커서를_만든다() {
    User first = UserTestFixtures.user(id);
    User second = UserTestFixtures.user(UUID.randomUUID());
    PowerUserSearchCond condition =
        new PowerUserSearchCond(RankingPeriod.ALL_TIME, Direction.ASC, null, null, 1);
    when(repository.findPowerUsers(eq(condition), any(LocalDate.class)))
        .thenReturn(List.of(first, second));
    when(repository.countPowerUsers(any(LocalDate.class))).thenReturn(7L);
    when(repository.countPowerUsersAhead(any(), eq(first.getCreatedAt()))).thenReturn(3L);

    CursorPageResponse<PowerUserResult> page = service.findPowerUsers(condition);

    assertThat(page.content()).extracting(PowerUserResult::userId).containsExactly(id);
    assertThat(page.content().getFirst().rank()).isEqualTo(4);
    assertThat(page.hasNext()).isTrue();
    assertThat(page.totalElements()).isEqualTo(7);
    assertThat(page.size()).isEqualTo(1);
    assertThat(PowerUserCursor.decode(page.nextCursor()).userId()).isEqualTo(id);
    assertThat(page.nextAfter()).isEqualTo(first.getCreatedAt());
  }

  @Test
  void 순위_내림차순은_페이지_안에서_순위가_감소한다() {
    User first = UserTestFixtures.user(id);
    User second = UserTestFixtures.user(UUID.randomUUID());
    PowerUserSearchCond condition =
        new PowerUserSearchCond(RankingPeriod.ALL_TIME, Direction.DESC, null, null, 2);
    when(repository.findPowerUsers(eq(condition), any(LocalDate.class)))
        .thenReturn(List.of(first, second));
    when(repository.countPowerUsers(any(LocalDate.class))).thenReturn(7L);
    when(repository.countPowerUsersAhead(any(), eq(first.getCreatedAt()))).thenReturn(6L);

    CursorPageResponse<PowerUserResult> page = service.findPowerUsers(condition);

    assertThat(page.content()).extracting(PowerUserResult::rank).containsExactly(7L, 6L);
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
    assertThat(page.nextAfter()).isNull();
  }

  @Test
  void 결과가_없으면_빈_페이지를_반환하고_순위를_계산하지_않는다() {
    PowerUserSearchCond condition = new PowerUserSearchCond(null, null, null, null, 20);
    when(repository.findPowerUsers(eq(condition), any(LocalDate.class))).thenReturn(List.of());
    CursorPageResponse<PowerUserResult> page = service.findPowerUsers(condition);
    assertThat(page.content()).isEmpty();
    assertThat(page.size()).isZero();
    assertThat(page.hasNext()).isFalse();
    verify(repository, never()).countPowerUsersAhead(any(), any());
  }
}
