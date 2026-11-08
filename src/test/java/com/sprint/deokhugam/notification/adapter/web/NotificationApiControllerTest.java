package com.sprint.deokhugam.notification.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.GlobalExceptionHandler;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.common.web.resolver.RequestArgumentResolver;
import com.sprint.deokhugam.notification.application.dto.NotificationDto;
import com.sprint.deokhugam.notification.application.dto.NotificationSearchCond;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCommand;
import com.sprint.deokhugam.notification.application.provided.query.NotificationFinder;
import com.sprint.deokhugam.notification.domain.exception.NotificationErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationApiControllerTest {
  private static final String HEADER = "Deokhugam-Request-User-ID";
  private final UUID userId = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();
  private final Instant createdAt = Instant.parse("2026-11-08T00:00:00Z");
  private final NotificationCommand command = mock(NotificationCommand.class);
  private final NotificationFinder finder = mock(NotificationFinder.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new NotificationApiController(command, finder))
          .setCustomArgumentResolvers(new RequestArgumentResolver())
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  private NotificationDto dto() {
    return NotificationDto.to(
        id, userId, UUID.randomUUID(), "리뷰 스냅샷", "알림 문구", true, createdAt, createdAt);
  }

  @Test
  void 읽음_요청은_헤더_ID를_전달하고_200과_알림을_반환한다() throws Exception {
    when(command.confirm(id, userId)).thenReturn(dto());
    mvc.perform(patch("/api/notifications/{id}", id).header(HEADER, userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.reviewContent").value("리뷰 스냅샷"))
        .andExpect(jsonPath("$.confirmed").value(true));
    verify(command).confirm(id, userId);
  }

  @Test
  void 전체_읽음은_204와_빈_본문을_반환한다() throws Exception {
    mvc.perform(patch("/api/notifications/read-all").header(HEADER, userId))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(command).confirmAll(userId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/notifications/read-all",
        "/api/notifications/00000000-0000-0000-0000-000000000001"
      })
  void 읽음_요청에_헤더가_없으면_400이다(String path) throws Exception {
    mvc.perform(patch(path))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_MISSING_HEADER"));
    verifyNoInteractions(command);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", " "})
  void 잘못된_헤더_UUID는_400이다(String header) throws Exception {
    mvc.perform(patch("/api/notifications/{id}", id).header(HEADER, header))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    verifyNoInteractions(command);
  }

  @ParameterizedTest
  @EnumSource(NotificationErrorCode.class)
  void 읽음_실패는_도메인_오류의_상태와_코드를_반환한다(NotificationErrorCode error) throws Exception {
    when(command.confirm(id, userId)).thenThrow(new BusinessException(error));
    mvc.perform(patch("/api/notifications/{id}", id).header(HEADER, userId))
        .andExpect(status().is(error.getStatus().value()))
        .andExpect(jsonPath("$.code").value(error.getCode()));
  }

  @Test
  void 목록은_기본_조건으로_조회하고_페이지_메타데이터를_보존한다() throws Exception {
    when(finder.findAllByUser(any()))
        .thenReturn(
            new CursorPageResponse<>(List.of(dto()), id.toString(), createdAt, 1, 10, true));
    mvc.perform(get("/api/notifications").param("userId", userId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(id.toString()))
        .andExpect(jsonPath("$.nextCursor").value(id.toString()))
        .andExpect(jsonPath("$.nextAfter").value(createdAt.toString()))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(10))
        .andExpect(jsonPath("$.hasNext").value(true));
    verify(finder)
        .findAllByUser(new NotificationSearchCond(userId, Direction.DESC, null, null, 20));
  }

  @Test
  void 다음_페이지의_커서_시각_방향_크기를_변환한다() throws Exception {
    when(finder.findAllByUser(any()))
        .thenReturn(new CursorPageResponse<>(List.of(), null, null, 0, 0, false));
    mvc.perform(
            get("/api/notifications")
                .param("userId", userId.toString())
                .param("direction", "ASC")
                .param("cursor", id.toString())
                .param("after", createdAt.toString())
                .param("limit", "5"))
        .andExpect(status().isOk());
    ArgumentCaptor<NotificationSearchCond> captor =
        ArgumentCaptor.forClass(NotificationSearchCond.class);
    verify(finder).findAllByUser(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new NotificationSearchCond(userId, Direction.ASC, id, createdAt, 5));
  }

  @Test
  void 사용자_ID_누락은_400이다() throws Exception {
    mvc.perform(get("/api/notifications")).andExpect(status().isBadRequest());
    verifyNoInteractions(finder);
  }

  @Test
  void 커서와_after중_하나만_오면_400이다() throws Exception {
    mvc.perform(
            get("/api/notifications")
                .param("userId", userId.toString())
                .param("cursor", id.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    verifyNoInteractions(finder);
  }
}
