package com.sprint.deokhugam.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.deokhugam.book.adapter.persistence.BookJpaRepository;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.comment.adapter.persistence.CommentJpaRepository;
import com.sprint.deokhugam.comment.application.dto.CommentCreateRequest;
import com.sprint.deokhugam.comment.application.dto.CommentUpdateRequest;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.review.adapter.persistence.ReviewJpaRepository;
import com.sprint.deokhugam.review.application.provided.command.ReviewCommand;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.adapter.persistence.UserJpaRepository;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class CommentIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private BookJpaRepository bookJpaRepository;
  @Autowired private ReviewJpaRepository reviewJpaRepository;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private CommentJpaRepository commentJpaRepository;
  @Autowired private ReviewCommand reviewCommand;
  @Autowired private EntityManager entityManager;

  private Review savedReview;
  private User savedUser;
  private Comment savedComment;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // repository test와 달리 특별히 지시하지 않는 이상 메인 DB 설정을 임의로 바꾸지 않기 때문에 URL을 H2로 강제 덮어쓰기
    registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "false");
  }

  @BeforeEach
  void setUp() {
    User user = User.create("testNickname", Email.to("test@example.com"), Password.to("abcd1234!"));
    savedUser = userJpaRepository.save(user);

    Book book =
        Book.create(
            "testTitle",
            "testAuthor",
            "testDescription",
            "testPublisher",
            LocalDate.of(2011, 1, 1),
            null,
            "https://example.com/thumbnails/testTitle.jpg");
    Book savedBook = bookJpaRepository.save(book);

    Review review = Review.create(1, "testReview", savedUser, savedBook);
    savedReview = reviewJpaRepository.save(review);

    Comment comment = Comment.create(savedReview, savedUser, "testCreateContent");
    savedComment = commentJpaRepository.save(comment);
    flushAndClear();
    // 저장소로 직접 넣은 댓글이라 리뷰의 댓글 수를 맞춰 준다
    reviewCommand.increaseReviewCommentCount(savedReview.getId());
  }

  // 요청 결과를 DB에 반영한 뒤 JPA가 기억하던 객체를 비운다. 이후 조회가 실제 DB에서 읽도록 만든다
  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private int findCommentCount() {
    return reviewJpaRepository.findById(savedReview.getId()).orElseThrow().getCommentCount();
  }

  @Test
  @DisplayName("통합 테스트: 댓글 생성 요청 시 DB에 저장")
  void createCommentIntegration() throws Exception {
    // Given
    CommentCreateRequest request =
        new CommentCreateRequest(savedReview.getId(), savedUser.getId(), "testNewContent");

    // When
    mockMvc
        .perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("testNewContent"));

    // Then
    flushAndClear();
    assertThat(commentJpaRepository.findAll())
        .extracting(Comment::getContent)
        .containsExactlyInAnyOrder("testCreateContent", "testNewContent");
    assertThat(findCommentCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("통합 테스트: 필수 값이 누락된 댓글 생성 요청 시 예외 처리")
  void createCommentValidationFailIntegration() throws Exception {
    CommentCreateRequest request = new CommentCreateRequest(null, null, "");

    mockMvc
        .perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("통합 테스트: 댓글 수정 요청 시 변경된 내용 DB에 저장")
  void updateCommentIntegration() throws Exception {
    // Given
    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");

    // When
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("testUpdatedContent"));

    // Then
    flushAndClear();
    Comment updatedComment = commentJpaRepository.findByIdOrThrow(savedComment.getId());
    assertThat(updatedComment.getContent()).isEqualTo("testUpdatedContent");
  }

  @Test
  @DisplayName("통합 테스트: 다른 유저가 댓글 수정 요청 시 403 응답 후 내용 유지")
  void updateCommentForbiddenIntegration() throws Exception {
    // Given
    User otherUser =
        userJpaRepository.save(
            User.create("otherNickname", Email.to("other@example.com"), Password.to("abcd1234!")));
    CommentUpdateRequest updateRequest = new CommentUpdateRequest("testUpdatedContent");

    // When
    mockMvc
        .perform(
            patch("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", otherUser.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());

    // Then
    flushAndClear();
    Comment comment = commentJpaRepository.findByIdOrThrow(savedComment.getId());
    assertThat(comment.getContent()).isEqualTo("testCreateContent");
  }

  @Test
  @DisplayName("통합 테스트: 댓글 논리 삭제 시 삭제 시각 저장 및 리뷰 댓글 수 감소")
  void softDeleteCommentIntegration() throws Exception {
    // When
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId()))
        .andExpect(status().isNoContent());

    // Then
    flushAndClear();
    Comment deletedComment =
        commentJpaRepository.findByIdIncludingDeletedOrThrow(savedComment.getId());
    assertThat(deletedComment.getDeletedAt()).isNotNull();
    assertThat(findCommentCount()).isZero();
  }

  @Test
  @DisplayName("통합 테스트: 이미 논리 삭제된 댓글을 다시 삭제하면 404 응답")
  void softDeleteTwiceIntegration() throws Exception {
    // Given
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId()))
        .andExpect(status().isNoContent());
    flushAndClear();

    // When & Then
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("통합 테스트: 다른 유저가 댓글 삭제 요청 시 403 응답 후 댓글 유지")
  void softDeleteForbiddenIntegration() throws Exception {
    // Given
    User otherUser =
        userJpaRepository.save(
            User.create("otherNickname", Email.to("other@example.com"), Password.to("abcd1234!")));

    // When
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", otherUser.getId()))
        .andExpect(status().isForbidden());

    // Then
    flushAndClear();
    assertThat(commentJpaRepository.findByIdOrThrow(savedComment.getId())).isNotNull();
    assertThat(findCommentCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("통합 테스트: 댓글 목록 조회 시 논리 삭제된 댓글 제외")
  void searchCommentsIntegration() throws Exception {
    // Given
    Comment deletedComment =
        commentJpaRepository.save(
            Comment.create(
                reviewJpaRepository.getReferenceById(savedReview.getId()),
                userJpaRepository.getReferenceById(savedUser.getId()),
                "deletedContent"));
    deletedComment.softDelete();
    flushAndClear();

    // When & Then
    mockMvc
        .perform(
            get("/api/comments")
                .param("reviewId", savedReview.getId().toString())
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].content").value("testCreateContent"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("통합 테스트: 댓글 물리 삭제 시 DB에서 지워지고 리뷰 댓글 수 감소")
  void hardDeleteCommentIntegration() throws Exception {
    // When
    mockMvc
        .perform(
            delete("/api/comments/{commentId}/hard", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId()))
        .andExpect(status().isNoContent());

    // Then
    flushAndClear();
    assertThat(commentJpaRepository.findById(savedComment.getId())).isEmpty();
    assertThat(findCommentCount()).isZero();
  }

  @Test
  @DisplayName("통합 테스트: 논리 삭제된 댓글을 물리 삭제해도 리뷰 댓글 수는 다시 줄지 않음")
  void hardDeleteAfterSoftDeleteIntegration() throws Exception {
    // Given
    reviewCommand.increaseReviewCommentCount(savedReview.getId());
    mockMvc
        .perform(
            delete("/api/comments/{commentId}", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId()))
        .andExpect(status().isNoContent());
    flushAndClear();

    // When
    mockMvc
        .perform(
            delete("/api/comments/{commentId}/hard", savedComment.getId())
                .header("Deokhugam-Request-User-ID", savedUser.getId()))
        .andExpect(status().isNoContent());

    // Then
    flushAndClear();
    assertThat(commentJpaRepository.findById(savedComment.getId())).isEmpty();
    assertThat(findCommentCount()).isEqualTo(1);
  }
}
