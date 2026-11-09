package com.sprint.deokhugam.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.user.UserTestFixtures;
import com.sprint.deokhugam.user.application.dto.command.RegisterUserCommand;
import com.sprint.deokhugam.user.application.dto.result.UserResult;
import com.sprint.deokhugam.user.application.required.PasswordHasher;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.User;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserCommandServiceTest {
  private final UserRepositoryPort repository = mock(UserRepositoryPort.class);
  private final PasswordHasher hasher = mock(PasswordHasher.class);
  private final UserCommandService service = new UserCommandService(repository, hasher);
  private final UUID id = UUID.randomUUID();

  @Test
  void 회원가입은_해시된_비밀번호와_초기_통계를_저장한다() {
    when(hasher.hash("Password1!")).thenReturn("encoded-password");
    when(repository.save(any(User.class)))
        .thenAnswer(invocation -> UserTestFixtures.persisted(invocation.getArgument(0), id));
    LocalDate before = LocalDate.now(ZoneId.of("Asia/Seoul"));

    UserResult result =
        service.registerUser(new RegisterUserCommand("user@example.com", "닉네임", "Password1!"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getPassword().password()).isEqualTo("encoded-password");
    assertThat(saved.getRankingStats().current().daily().date())
        .isIn(before, LocalDate.now(ZoneId.of("Asia/Seoul")));
    assertThat(saved.getRankingStats().current().allTime().metrics().scoreUnits()).isZero();
    assertThat(saved.getRankingPublishedOn()).isNull();
    assertThat(saved.getAllTimeScore()).isZero();
    assertThat(result.id()).isEqualTo(id);
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.nickname()).isEqualTo("닉네임");
    assertThat(result.createdAt()).isEqualTo(UserTestFixtures.CREATED_AT);
  }

  @Test
  void 중복_이메일이면_해싱이나_저장을_하지_않는다() {
    when(repository.existsByEmail(Email.to("user@example.com"))).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.registerUser(
                    new RegisterUserCommand("user@example.com", "닉네임", "Password1!")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.DUPLICATE_EMAIL);

    verifyNoInteractions(hasher);
    verify(repository, never()).save(any());
  }

  @Test
  void 본인_닉네임을_수정하고_변경한_결과를_반환한다() {
    User user = UserTestFixtures.user(id);
    when(repository.findById(id)).thenReturn(Optional.of(user));

    UserResult result = service.update(id, id, "새닉네임");

    assertThat(user.getNickname()).isEqualTo("새닉네임");
    assertThat(result.nickname()).isEqualTo("새닉네임");
    assertThat(result.id()).isEqualTo(id);
  }

  @Test
  void 다른_사용자의_닉네임은_조회하기_전에_거부한다() {
    assertThatThrownBy(() -> service.update(id, UUID.randomUUID(), "새닉네임"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.FORBIDDEN);
    verifyNoInteractions(repository);
  }

  @Test
  void 없는_유저의_수정은_404에_대응하는_에러다() {
    when(repository.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(id, id, "새닉네임"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
  }

  @Test
  void 탈퇴한_유저는_수정할_수_없다() {
    User user = UserTestFixtures.user(id);
    user.softDelete();
    when(repository.findById(id)).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.update(id, id, "새닉네임"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
    assertThat(user.getNickname()).isEqualTo("테스터");
  }

  @Test
  void 같은_닉네임으로_변경하면_충돌_에러다() {
    User user = UserTestFixtures.user(id);
    when(repository.findById(id)).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.update(id, id, user.getNickname()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
  }

  @Test
  void 본인_탈퇴는_논리_삭제하고_물리_삭제하지_않는다() {
    User user = UserTestFixtures.user(id);
    when(repository.findById(id)).thenReturn(Optional.of(user));

    service.withdraw(id, id);

    assertThat(user.isDeleted()).isTrue();
    assertThat(user.getDeletedAt()).isNotNull();
    verify(repository, never()).remove(any());
  }

  @Test
  void 다른_사용자를_탈퇴시킬_수_없다() {
    assertThatThrownBy(() -> service.withdraw(id, UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.FORBIDDEN);
    verifyNoInteractions(repository);
  }

  @Test
  void 없는_유저는_탈퇴할_수_없다() {
    when(repository.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.withdraw(id, id))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
  }

  @Test
  void 이미_탈퇴한_유저의_재탈퇴는_거부한다() {
    User user = UserTestFixtures.user(id);
    user.softDelete();
    when(repository.findById(id)).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.withdraw(id, id))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(UserErrorCode.NOT_FOUND);
  }

  @Test
  void 최종_삭제는_저장소에_유저_ID를_전달한다() {
    service.delete(id);
    verify(repository).remove(id);
  }
}
