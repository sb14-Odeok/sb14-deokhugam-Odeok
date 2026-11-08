package com.sprint.deokhugam.comment.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.deokhugam.comment.application.dto.CommentCreateRequest;
import com.sprint.deokhugam.comment.application.dto.CommentDto;
import com.sprint.deokhugam.comment.application.dto.CommentUpdateRequest;
import com.sprint.deokhugam.comment.application.provided.CommentUseCase;
import com.sprint.deokhugam.comment.domain.exception.CommentErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.review.adapter.web.ReviewApiController;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(
    controllers = CommentController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = ReviewApiController.class))
public class CommentControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @MockitoBean private CommentUseCase commentUseCase;

  private UUID reviewId;
  private UUID userId;
  private UUID commentId;

  @BeforeEach
  void setUp() {
    reviewId = UUID.randomUUID();
    userId = UUID.randomUUID();
    commentId = UUID.randomUUID();
  }

  @Test
  @DisplayName("댓글 등록 성공")
  void createComment_success() throws Exception {
    // Given
    CommentCreateRequest createRequest =
        new CommentCreateRequest(reviewId, userId, "testCreateContent");

    CommentDto commentDto =
        new CommentDto(
            commentId,
            reviewId,
            userId,
            "testNickname",
            createRequest.content(),
            Instant.now(),
            Instant.now());

    given(commentUseCase.create(any(CommentCreateRequest.class))).willReturn(commentDto);

    // When & Then
    mockMvc
        .perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("testCreateContent"));
  }

  @Test
  @DisplayName("댓글 등록 실패 - 빈 내용 입력 시 400 에러")
  void createComment_fail_blankContent() throws Exception {
    // Given
    CommentCreateRequest invalidRequest = new CommentCreateRequest(reviewId, userId, "");

    // When & Then
    mockMvc
        .perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).create(any());
  }

  @Test
  @DisplayName("댓글 등록 실패 - 공백만 입력 시 400 에러")
  void createComment_fail_whitespaceContent() throws Exception {
    // Given
    CommentCreateRequest invalidRequest = new CommentCreateRequest(reviewId, userId, "   ");

    // When & Then
    mockMvc
        .perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).create(any());
  }

  @Test
  @DisplayName("댓글 등록 실패 - 1000자 초과 입력 시 400 에러")
  void createComment_fail_tooLongContent() throws Exception {
    // Given
    CommentCreateRequest invalidRequest =
        new CommentCreateRequest(reviewId, userId, "a".repeat(1001));

    // When & Then
    mockMvc
        .perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).create(any());
  }

  @Test
  @DisplayName("댓글 수정 성공")
  void updateComment_success() throws Exception {
    // Given
    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");

    CommentDto commentDto =
        new CommentDto(
            commentId,
            reviewId,
            userId,
            "testNickname",
            updateRequest.content(),
            Instant.now(),
            Instant.now());

    given(commentUseCase.update(eq(commentId), eq(userId), any(CommentUpdateRequest.class)))
        .willReturn(commentDto);

    // When & Then
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("testUpdatedContent"));
  }

  @Test
  @DisplayName("댓글 수정 실패 - 빈 내용 입력 시 예외 발생")
  void updateComment_fail_blankContent() throws Exception {
    // Given
    CommentUpdateRequest invalidRequest = new CommentUpdateRequest("");

    // When & Then
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("댓글 수정 실패 - 공백만 입력 시 400 에러")
  void updateComment_fail_whitespaceContent() throws Exception {
    // Given
    CommentUpdateRequest invalidRequest = new CommentUpdateRequest("   ");

    // When & Then
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("댓글 수정 실패 - 1000자 초과 입력 시 400 에러")
  void updateComment_fail_tooLongContent() throws Exception {
    // Given
    CommentUpdateRequest invalidRequest = new CommentUpdateRequest("a".repeat(1001));

    // When & Then
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("댓글 수정 실패 - 요청자 헤더가 없으면 400 에러")
  void updateComment_fail_missingHeader() throws Exception {
    // Given
    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");

    // When & Then
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("댓글 수정 실패 - 작성자가 아닌 타인이 요청할 경우 예외 발생")
  void updateComment_fail_forbidden() throws Exception {
    // Given
    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");

    given(commentUseCase.update(eq(commentId), eq(userId), any(CommentUpdateRequest.class)))
        .willThrow(new BusinessException(CommentErrorCode.FORBIDDEN));

    // When & Then
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("댓글 논리 삭제 성공")
  void softDelete_success() throws Exception {
    // Given
    willDoNothing().given(commentUseCase).softDelete(eq(commentId), eq(userId));

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("댓글 논리 삭제 실패 - 존재하지 않는 댓글일 경우 예외 발생")
  void softDelete_fail_notFound() throws Exception {
    // Given
    willThrow(new BusinessException(CommentErrorCode.NOT_FOUND))
        .given(commentUseCase)
        .softDelete(commentId, userId);

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("댓글 논리 삭제 실패 - 작성자가 아닌 타인이 요청할 경우 예외 발생")
  void softDelete_fail_forbidden() throws Exception {
    // Given
    willThrow(new BusinessException(CommentErrorCode.FORBIDDEN))
        .given(commentUseCase)
        .softDelete(commentId, userId);

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("댓글 논리 삭제 실패 - 요청자 헤더가 없으면 400 에러")
  void softDelete_fail_missingHeader() throws Exception {
    // When & Then
    mockMvc
        .perform(delete("/api/comments/{commentId}", commentId))
        .andExpect(status().isBadRequest());

    verify(commentUseCase, never()).softDelete(any(), any());
  }

  @Test
  @DisplayName("댓글 물리 삭제 성공")
  void hardDelete_success() throws Exception {
    // Given
    willDoNothing().given(commentUseCase).hardDelete(eq(commentId), eq(userId));

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}/hard", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("댓글 물리 삭제 실패 - 존재하지 않는 댓글일 경우 예외 발생")
  void hardDelete_fail_notFound() throws Exception {
    // Given
    willThrow(new BusinessException(CommentErrorCode.NOT_FOUND))
        .given(commentUseCase)
        .hardDelete(commentId, userId);

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}/hard", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("댓글 물리 삭제 실패 - 작성자가 아닌 타인이 요청할 경우 예외 발생")
  void hardDelete_fail_forbidden() throws Exception {
    // Given
    willThrow(new BusinessException(CommentErrorCode.FORBIDDEN))
        .given(commentUseCase)
        .hardDelete(commentId, userId);

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}/hard", commentId)
                .header("Deokhugam-Request-User-ID", userId.toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("댓글 목록 조회 성공")
  void searchComments_success() throws Exception {
    // Given
    CursorPageResponse<CommentDto> pageResponse =
        CursorPageResponse.of(List.of(), 50, CommentDto::createdAt, CommentDto::createdAt, 0L);

    given(commentUseCase.searchCommentsByPagination(any())).willReturn(pageResponse);

    // When & Then
    mockMvc
        .perform(get("/api/comments").param("reviewId", reviewId.toString()).param("limit", "50"))
        .andExpect(status().isOk());
  }
}
