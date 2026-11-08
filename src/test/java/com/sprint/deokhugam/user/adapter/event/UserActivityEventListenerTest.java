package com.sprint.deokhugam.user.adapter.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.user.UserTestFixtures;
import com.sprint.deokhugam.user.application.UserCommandService;
import com.sprint.deokhugam.user.application.provided.command.UserActivityCommand;
import com.sprint.deokhugam.user.application.required.PasswordHasher;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 Spring 이벤트/트랜잭션 프록시 검증. DB 커밋 자체를 검증하는 테스트는 아니다. */
@SpringJUnitConfig(UserActivityEventListenerTest.Config.class)
class UserActivityEventListenerTest {
  @Autowired ApplicationEventPublisher publisher;
  @Autowired UserRepositoryPort repository;
  @Autowired PlatformTransactionManager transactionManager;
  private TransactionTemplate transaction;
  private User actor;
  private User author;
  private ActivityEvent event;

  @BeforeEach
  void setUp() {
    reset(repository);
    actor = UserTestFixtures.user(UUID.randomUUID());
    author = UserTestFixtures.user(UUID.randomUUID());
    var instant = UserTestFixtures.DATE.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    event =
        ActivityEvent.LikeAdded(
            UUID.randomUUID(), UUID.randomUUID(), author.getId(), actor.getId(), instant, instant);
    transaction = new TransactionTemplate(transactionManager);
    transaction.setName("source-activity");
  }

  @Test
  void 원본_커밋_이후에_새_트랜잭션에서_통계를_반영한다() {
    when(repository.findById(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              assertThat(TransactionSynchronizationManager.getCurrentTransactionName())
                  .endsWith("UserCommandService.apply");
              UUID id = invocation.getArgument(0);
              return Optional.of(id.equals(actor.getId()) ? actor : author);
            });
    transaction.executeWithoutResult(
        status -> {
          publisher.publishEvent(event);
          verifyNoInteractions(repository);
        });
    verify(repository).findById(actor.getId());
    verify(repository).findById(author.getId());
    assertThat(actor.getRankingStats().current().allTime().metrics().participatedLikeCount())
        .isEqualTo(1);
    assertThat(author.getRankingStats().current().allTime().metrics().reviewLikeCount())
        .isEqualTo(1);
  }

  @Test
  void 원본이_롤백되면_통계_리스너를_실행하지_않는다() {
    transaction.executeWithoutResult(
        status -> {
          publisher.publishEvent(event);
          status.setRollbackOnly();
        });
    verifyNoInteractions(repository);
  }

  @Test
  void 트랜잭션_없이_발행한_이벤트는_기본_설정에서_처리하지_않는다() {
    publisher.publishEvent(event);
    verifyNoInteractions(repository);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Config {
    @Bean
    PlatformTransactionManager transactionManager() throws SQLException {
      // 실제 JDBC 트랜잭션 매니저로 suspend/resume을 검증하되 DB 연결은 대체한다.
      DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection())
          .thenAnswer(
              invocation -> {
                Connection connection = mock(Connection.class);
                when(connection.getAutoCommit()).thenReturn(true);
                return connection;
              });
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    UserRepositoryPort userRepositoryPort() {
      return mock(UserRepositoryPort.class);
    }

    @Bean
    UserActivityCommand activityCommand(UserRepositoryPort repository) {
      return new UserCommandService(repository, mock(PasswordHasher.class));
    }

    @Bean
    ActivityEventListener activityEventListener(UserActivityCommand command) {
      return new ActivityEventListener(command);
    }
  }
}
