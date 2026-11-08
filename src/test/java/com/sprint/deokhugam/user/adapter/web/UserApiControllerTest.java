package com.sprint.deokhugam.user.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.GlobalExceptionHandler;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.common.web.resolver.RequestArgumentResolver;
import com.sprint.deokhugam.user.application.dto.command.RegisterUserCommand;
import com.sprint.deokhugam.user.application.dto.query.LoginUserQuery;
import com.sprint.deokhugam.user.application.dto.query.PowerUserSearchCond;
import com.sprint.deokhugam.user.application.dto.result.PowerUserResult;
import com.sprint.deokhugam.user.application.dto.result.UserResult;
import com.sprint.deokhugam.user.application.provided.command.UserCleanupUseCase;
import com.sprint.deokhugam.user.application.provided.command.UserCommand;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.application.provided.query.UserLogin;
import com.sprint.deokhugam.user.domain.exception.UserErrorCode;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실제 컨트롤러/검증/헤더 리졸버/예외 핸들러를 사용하고 유스케이스만 대체한다. */
class UserApiControllerTest {
  private static final String HEADER = "Deokhugam-Request-User-ID";
  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Instant CREATED_AT = Instant.parse("2026-11-04T01:00:00Z");
  private final UserCommand commands = mock(UserCommand.class);
  private final UserLogin login = mock(UserLogin.class);
  private final UserFinder finder = mock(UserFinder.class);
  private final UserCleanupUseCase cleanup = mock(UserCleanupUseCase.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new UserApiController(commands, login, finder, cleanup))
          .setCustomArgumentResolvers(new RequestArgumentResolver())
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  private UserResult result() {
    return new UserResult(ID, "user@example.com", "테스터", CREATED_AT);
  }

  @Test
  void 물리_삭제는_정리_유스케이스를_호출하고_204를_반환한다() throws Exception {
    mvc.perform(delete("/api/users/hard/{userId}", ID))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(cleanup).cleanupUserWithRelatedData(ID);
    verifyNoInteractions(commands, login, finder);
  }

  @Test
  void 회원가입은_명령으로_변환하고_201_응답에_비밀번호를_노출하지_않는다() throws Exception {
    when(commands.registerUser(any())).thenReturn(result());
    mvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"user@example.com","nickname":"테스터","password":"Password1!"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.nickname").value("테스터"))
        .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
        .andExpect(jsonPath("$.password").doesNotExist());
    verify(commands).registerUser(new RegisterUserCommand("user@example.com", "테스터", "Password1!"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"email\":\"bad-email\",\"nickname\":\"테스터\",\"password\":\"Password1!\"}",
        "{\"email\":\"user@example.com\",\"nickname\":\" \",\"password\":\"Password1!\"}",
        "{\"email\":\"user@example.com\",\"nickname\":\"테스터\",\"password\":\"weak\"}"
      })
  void 잘못된_가입_입력은_서비스_호출_없이_400이다(String json) throws Exception {
    mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    verifyNoInteractions(commands);
  }

  @Test
  void 중복_이메일은_409와_에러코드를_반환한다() throws Exception {
    when(commands.registerUser(any()))
        .thenThrow(new BusinessException(UserErrorCode.DUPLICATE_EMAIL));
    mvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"user@example.com","nickname":"테스터","password":"Password1!"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_DUPLICATE_EMAIL"));
  }

  @Test
  void 로그인은_Query로_변환하고_웹_응답을_반환한다() throws Exception {
    when(login.login(any())).thenReturn(result());
    mvc.perform(
            post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"user@example.com","password":"Password1!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.password").doesNotExist());
    verify(login).login(new LoginUserQuery("user@example.com", "Password1!"));
  }

  @Test
  void 로그인_실패는_401이다() throws Exception {
    when(login.login(any())).thenThrow(new BusinessException(UserErrorCode.LOGIN_FAILED));
    mvc.perform(
            post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"user@example.com","password":"wrong"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER_LOGIN_FAILED"));
  }

  @Test
  void 상세_조회는_웹_응답으로_변환한다() throws Exception {
    when(finder.getUserDetail(ID)).thenReturn(result());
    mvc.perform(get("/api/users/{id}", ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("user@example.com"));
  }

  @Test
  void 없는_유저는_404다() throws Exception {
    when(finder.getUserDetail(ID)).thenThrow(new BusinessException(UserErrorCode.NOT_FOUND));
    mvc.perform(get("/api/users/{id}", ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  void 수정은_헤더를_UUID로_변환해_전달한다() throws Exception {
    when(commands.update(ID, ID, "새닉네임"))
        .thenReturn(new UserResult(ID, "user@example.com", "새닉네임", CREATED_AT));
    mvc.perform(
            patch("/api/users/{id}", ID)
                .header(HEADER, ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("새닉네임"));
    verify(commands).update(ID, ID, "새닉네임");
  }

  @Test
  void 수정_헤더가_없으면_400이며_서비스를_호출하지_않는다() throws Exception {
    mvc.perform(
            patch("/api/users/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_MISSING_HEADER"));
    verifyNoInteractions(commands);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", " "})
  void 잘못된_헤더_UUID는_400이다(String header) throws Exception {
    mvc.perform(
            patch("/api/users/{id}", ID)
                .header(HEADER, header)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    verifyNoInteractions(commands);
  }

  @Test
  void 다른_사용자의_수정은_403이다() throws Exception {
    UUID other = UUID.randomUUID();
    when(commands.update(ID, other, "새닉네임"))
        .thenThrow(new BusinessException(UserErrorCode.FORBIDDEN));
    mvc.perform(
            patch("/api/users/{id}", ID)
                .header(HEADER, other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_FORBIDDEN"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "a", "123456789012345678901"})
  void 잘못된_닉네임_수정은_400이다(String nickname) throws Exception {
    mvc.perform(
            patch("/api/users/{id}", ID)
                .header(HEADER, ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(commands);
  }

  @Test
  void 탈퇴는_헤더_ID를_전달하고_204를_반환한다() throws Exception {
    mvc.perform(delete("/api/users/{id}", ID).header(HEADER, ID))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(commands).withdraw(ID, ID);
  }

  @Test
  void 탈퇴_헤더가_없으면_400이다() throws Exception {
    mvc.perform(delete("/api/users/{id}", ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_MISSING_HEADER"));
    verifyNoInteractions(commands);
  }

  @Test
  void 파워_조회는_헤더_없이_결과와_페이지_메타데이터를_보존한다() throws Exception {
    String cursor = "ALL_TIME|2026-11-05|165|" + ID;
    PowerUserResult power =
        new PowerUserResult(ID, "테스터", RankingPeriod.ALL_TIME, CREATED_AT, 3, 1.65, 1.7, 1, 2);
    when(finder.findPowerUsers(any()))
        .thenReturn(new CursorPageResponse<>(List.of(power), cursor, CREATED_AT, 1, 42, true));

    mvc.perform(get("/api/users/power"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].userId").value(ID.toString()))
        .andExpect(jsonPath("$.content[0].period").value("ALL_TIME"))
        .andExpect(jsonPath("$.content[0].rank").value(3))
        .andExpect(jsonPath("$.content[0].score").value(1.65))
        .andExpect(jsonPath("$.content[0].reviewScoreSum").value(1.7))
        .andExpect(jsonPath("$.content[0].likeCount").value(1))
        .andExpect(jsonPath("$.content[0].commentCount").value(2))
        .andExpect(jsonPath("$.nextCursor").value(cursor))
        .andExpect(jsonPath("$.nextAfter").value(CREATED_AT.toString()))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(42))
        .andExpect(jsonPath("$.hasNext").value(true));
    ArgumentCaptor<PowerUserSearchCond> captor = ArgumentCaptor.forClass(PowerUserSearchCond.class);
    verify(finder).findPowerUsers(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new PowerUserSearchCond(RankingPeriod.ALL_TIME, Direction.ASC, null, null, 20));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101", "-1"})
  void 잘못된_페이지_크기는_400이다(String limit) throws Exception {
    mvc.perform(get("/api/users/power").param("limit", limit)).andExpect(status().isBadRequest());
    verifyNoInteractions(finder);
  }

  @Test
  void 커서와_after는_함께_전달해야_한다() throws Exception {
    mvc.perform(get("/api/users/power").param("cursor", "cursor"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(finder);
  }

  @Test
  void 잘못된_경로_UUID는_400이다() throws Exception {
    mvc.perform(get("/api/users/not-a-uuid")).andExpect(status().isBadRequest());
    verifyNoInteractions(finder);
  }
}
