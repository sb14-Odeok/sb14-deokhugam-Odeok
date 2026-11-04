package com.sprint.deokhugam.user.application.required;

import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserRepositoryPortTest {

  @Autowired UserRepositoryPort userRepositoryPort;

  @Test
  public void 계정생성_테스트() throws Exception {
    // given
    Email email = Email.to("test@test.com");
    Password password = Password.to("abcd1234!");
    User user = User.create("tester", email, password);
    // when
    User findUser = userRepositoryPort.save(user);
    // then
    Assertions.assertThat(findUser.getId()).isEqualTo(user.getId());
  }
}
