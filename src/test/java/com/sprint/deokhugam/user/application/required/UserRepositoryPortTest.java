package com.sprint.deokhugam.user.application.required;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.user.application.dto.query.PowerUserSearchCond;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.ranking.Period.AllTime;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import com.sprint.deokhugam.user.domain.ranking.UserActivityMetrics;
import com.sprint.deokhugam.user.domain.ranking.UserRankingStats;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * RUN_USER_POSTGRES_TESTS=true인 경우에만 실행한다. test 프로필은 로컬 deokhugam_test 스키마를 생성하므로 개발/운영 DB에는 실행하지
 * 않는다.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:postgresql://localhost:${POSTGRES_PORT:5432}/deokhugam_test"
    })
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_USER_POSTGRES_TESTS", matches = "true")
@Tag("postgres")
@Transactional
class UserRepositoryPortTest {

  @Autowired UserRepositoryPort repository;
  @Autowired EntityManager em;

  private User create(String nickname) {
    return User.create(
        nickname, Email.to(UUID.randomUUID() + "@example.com"), Password.to("encoded"));
  }

  @Test
  void 저장한_유저의_식별자_감사필드_값객체와_통계를_다시_읽는다() {
    User user = create("테스터");
    user.initializeRankingStats(LocalDate.of(2026, 11, 4));
    repository.save(user);
    em.flush();
    UUID id = user.getId();
    UserRankingStats stats = user.getRankingStats();
    em.clear();

    User found = repository.findById(id).orElseThrow();
    assertThat(found).isNotSameAs(user);
    assertThat(found.getEmail()).isEqualTo(user.getEmail());
    assertThat(found.getPassword()).isEqualTo(user.getPassword());
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getRankingStats()).isEqualTo(stats);
  }

  @Test
  void 같은_이메일의_동시성_최종_방어는_DB_유니크_제약이다() {
    User first = create("첫유저");
    repository.save(first);
    em.flush();
    User duplicate = User.create("다른유저", first.getEmail(), Password.to("another-hash"));
    assertThatThrownBy(
            () -> {
              repository.save(duplicate);
              em.flush();
            })
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
  }

  @Test
  void 정리_대상은_기준시각_이전_탈퇴자만_개수_제한으로_조회한다() {
    Instant cutoff = Instant.parse("2026-11-05T00:00:00Z");
    User old = create("오래된탈퇴자");
    User recent = create("최근탈퇴자");
    User active = create("활성유저");
    ReflectionTestUtils.setField(old, "deletedAt", cutoff.minusSeconds(60));
    ReflectionTestUtils.setField(recent, "deletedAt", cutoff.plusSeconds(60));
    repository.save(old);
    repository.save(recent);
    repository.save(active);
    em.flush();
    em.clear();
    assertThat(repository.findCleanupIds(cutoff, 1)).containsExactly(old.getId());
  }

  @Test
  void 파워_유저는_오늘_공개한_활성_유저만_조회한다() {
    LocalDate date = LocalDate.of(2026, 11, 5);
    User visible = publish(create("공개유저"), date, 3);
    User deleted = publish(create("탈퇴유저"), date, 9);
    deleted.softDelete();
    User stale = publish(create("이전공개유저"), date.minusDays(1), 5);
    User unpublished = create("미공개유저");
    unpublished.initializeRankingStats(date);
    repository.save(visible);
    repository.save(deleted);
    repository.save(stale);
    repository.save(unpublished);
    em.flush();
    em.clear();

    PowerUserSearchCond condition =
        new PowerUserSearchCond(RankingPeriod.ALL_TIME, Direction.ASC, null, null, 20);
    assertThat(repository.findPowerUsers(condition, date))
        .extracting(User::getId)
        .containsExactly(visible.getId());
    assertThat(repository.countPowerUsers(date)).isEqualTo(1);
  }

  private User publish(User user, LocalDate date, long likes) {
    UserRankingStats stats = UserRankingStats.empty(date.minusDays(1));
    user.updateRankingStats(
        new UserRankingStats(
                stats.current().withPeriod(new AllTime(new UserActivityMetrics(likes, 0, 0, 0))),
                stats.published(),
                null)
            .close(date));
    return user;
  }
}
