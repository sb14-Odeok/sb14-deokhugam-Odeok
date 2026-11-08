package com.sprint.deokhugam.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.common.config.PasswordConfig;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.user.UserTestFixtures;
import com.sprint.deokhugam.user.adapter.crypto.Argon2PasswordHasher;
import com.sprint.deokhugam.user.application.dto.command.RegisterUserCommand;
import com.sprint.deokhugam.user.application.dto.query.LoginUserQuery;
import com.sprint.deokhugam.user.application.dto.result.UserResult;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 Argon2와 서비스를 연결한다. Spring/JPA/외부 DB는 시작하지 않는다. */
class UserServiceTest {
  private final UserRepositoryPort repository = mock(UserRepositoryPort.class);
  private final Argon2PasswordHasher hasher =
      new Argon2PasswordHasher(new PasswordConfig().passwordEncoder());
  private final UserCommandService commands = new UserCommandService(repository, hasher);
  private final UserQueryService queries = new UserQueryService(repository, hasher);
  private final AtomicReference<User> saved = new AtomicReference<>();
  private static final String EMAIL = "tester@example.com";
  private static final String PASSWORD = "Password1!";

  @BeforeEach
  void setUp() {
    when(repository.existsByEmail(any(Email.class)))
        .thenAnswer(
            invocation ->
                saved.get() != null && saved.get().getEmail().equals(invocation.getArgument(0)));
    when(repository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = UserTestFixtures.persisted(invocation.getArgument(0), UUID.randomUUID());
              saved.set(user);
              return user;
            });
    when(repository.findByEmail(any(Email.class)))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(saved.get())
                    .filter(user -> user.getEmail().equals(invocation.getArgument(0))));
    when(repository.findById(any(UUID.class)))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(saved.get())
                    .filter(user -> user.getId().equals(invocation.getArgument(0))));
  }

  @Test
  void 가입한_비밀번호는_평문과_다르게_저장되고_로그인할_수_있다() {
    UserResult registered = commands.registerUser(new RegisterUserCommand(EMAIL, "테스터", PASSWORD));
    UserResult loggedIn = queries.login(new LoginUserQuery(EMAIL, PASSWORD));
    assertThat(saved.get().getPassword().password())
        .startsWith("$argon2id$")
        .isNotEqualTo(PASSWORD);
    assertThat(loggedIn).isEqualTo(registered);
  }

  @Test
  void 같은_비밀번호도_가입할_때마다_다른_솔트를_사용한다() {
    String first = hasher.hash(PASSWORD);
    String second = hasher.hash(PASSWORD);
    assertThat(first).isNotEqualTo(second);
    assertThat(hasher.matches(PASSWORD, first)).isTrue();
    assertThat(hasher.matches("WrongPassword1!", first)).isFalse();
  }

  @Test
  void 탈퇴하면_기존_비밀번호로도_로그인할_수_없다() {
    UserResult registered = commands.registerUser(new RegisterUserCommand(EMAIL, "테스터", PASSWORD));
    commands.withdraw(registered.id(), registered.id());
    assertThatThrownBy(() -> queries.login(new LoginUserQuery(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.LOGIN_FAILED);
  }
}
