package com.sprint.deokhugam.comment.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.comment.domain.exception.CommentErrorCode;
import com.sprint.deokhugam.common.config.QuerydslConfig;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Jpa 컴포넌트만 띄워주는 슬라이스 테스
@DataJpaTest
// Jpa 엔티티의 생성일, 수정일 등 자동 기록
@EnableJpaAuditing
// 프로필 환경 활성화
@ActiveProfiles("test")
// 자동으로 등록되지 않는 클래스나 설정 import
@Import({CommentQuerydslRepository.class, QuerydslConfig.class, JacksonAutoConfiguration.class})
public class CommentJpaRepositoryTest {

  @Autowired private CommentJpaRepository commentJpaRepository;
  @Autowired private CommentQuerydslRepository commentQuerydslRepository;
  @Autowired private TestEntityManager entityManager;

  private Review review;
  private Review otherReview;
  private User user;
  private User otherUser;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // 전역 따옴표("text")로 인한 DDL 테이블 생성 실패 해결를 위해 전역 따옴표 비활성화
    registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "false");
  }

  @BeforeEach
  void setUp() {
    user = User.create("testNickname", Email.to("test@example.com"), Password.to("abcd1234!"));
    entityManager.persist(user);

    otherUser =
        User.create("otherNickname", Email.to("other@example.com"), Password.to("abcd1234!"));
    entityManager.persist(otherUser);

    Book book =
        Book.create(
            "testTitle",
            "testAuthor",
            "testDescription",
            "testPublisher",
            LocalDate.of(2011, 1, 1),
            null,
            "https://example.com/thumbnails/testTitle.jpg");
    entityManager.persist(book);

    review = Review.create(1, "testReview", user, book);
    entityManager.persist(review);

    otherReview = Review.create(1, "otherReview", otherUser, book);
    entityManager.persist(otherReview);

    entityManager.flush();
    entityManager.clear();
  }

  // DB에 반영한 뒤 JPA가 기억하던 객체를 비워서, 이후 조회가 실제 DB에서 읽도록 만든다
  private Comment saveComment(Review targetReview, User writer, String content) {
    Comment savedComment =
        commentJpaRepository.saveAndFlush(Comment.create(targetReview, writer, content));
    entityManager.clear();
    return savedComment;
  }

  private UUID saveSoftDeletedComment() {
    UUID commentId = saveComment(review, user, "testDeletedContent").getId();

    commentJpaRepository.findByIdOrThrow(commentId).softDelete();
    entityManager.flush();
    entityManager.clear();

    return commentId;
  }

  @Test
  @DisplayName("댓글 저장 및 ID 조회 성공")
  void saveAndFindById() {
    // Given
    Comment savedComment = saveComment(review, user, "testCreateContent");

    // When
    Comment foundComment = commentJpaRepository.findByIdOrThrow(savedComment.getId());

    // Then
    assertThat(foundComment).isNotNull();
    assertThat(foundComment.getContent()).isEqualTo("testCreateContent");
  }

  @Test
  @DisplayName("존재하지 않는 댓글 조회 시 예외 발생")
  void findByIdNotFoundThrowsException() {
    // Given
    UUID nonExistentId = UUID.randomUUID();

    // When & Then
    assertThatThrownBy(() -> commentJpaRepository.findByIdOrThrow(nonExistentId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("논리 삭제된 댓글은 일반 조회로 찾을 수 없다")
  void findByIdOrThrow_softDeletedComment() {
    // Given
    UUID commentId = saveSoftDeletedComment();

    // When & Then
    assertThatThrownBy(() -> commentJpaRepository.findByIdOrThrow(commentId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("논리 삭제된 댓글도 물리 삭제용 조회로 찾을 수 있다")
  void findByIdIncludingDeletedOrThrow_softDeletedComment() {
    // Given
    UUID commentId = saveSoftDeletedComment();

    // When
    Comment foundComment = commentJpaRepository.findByIdIncludingDeletedOrThrow(commentId);

    // Then
    assertThat(foundComment.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("존재하지 않는 댓글을 물리 삭제용 조회로 찾으면 예외 발생")
  void findByIdIncludingDeletedOrThrow_notFound() {
    // Given
    UUID nonExistentId = UUID.randomUUID();

    // When & Then
    assertThatThrownBy(() -> commentJpaRepository.findByIdIncludingDeletedOrThrow(nonExistentId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommentErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("댓글 수정 성공")
  void updateComment() {
    // Given
    UUID commentId = saveComment(review, user, "testCreateContent").getId();

    // When
    commentJpaRepository.findByIdOrThrow(commentId).update("testUpdatedContent");
    entityManager.flush();
    entityManager.clear();

    // Then
    Comment updatedComment = commentJpaRepository.findByIdOrThrow(commentId);
    assertThat(updatedComment.getContent()).isEqualTo("testUpdatedContent");
  }

  @Test
  @DisplayName("유저 ID로 댓글 리스트 조회 성공")
  void findByUser_Id() {
    // Given
    saveComment(review, user, "testComment1");
    saveComment(review, user, "testComment2");
    saveComment(review, otherUser, "otherComment");

    // When
    List<Comment> comments = commentQuerydslRepository.findByUser_Id(user.getId());

    // Then
    assertThat(comments).hasSize(2);
    assertThat(comments)
        .extracting(Comment::getContent)
        .containsExactlyInAnyOrder("testComment1", "testComment2");
  }

  @Test
  @DisplayName("유저 ID로 댓글 일괄 삭제 시 해당 유저의 댓글만 삭제된다")
  void deleteCommentsByUser() {
    // Given
    saveComment(review, user, "testComment");
    saveComment(review, otherUser, "otherComment");

    // When
    commentQuerydslRepository.deleteCommentsByUser(user.getId());
    entityManager.clear();

    // Then
    assertThat(commentJpaRepository.findAll())
        .extracting(Comment::getContent)
        .containsExactly("otherComment");
  }

  @Test
  @DisplayName("리뷰 ID 목록으로 댓글 일괄 삭제 시 해당 리뷰의 댓글만 삭제된다")
  void deleteCommentsByReview() {
    // Given
    saveComment(review, user, "testComment");
    saveComment(otherReview, user, "otherComment");

    // When
    commentQuerydslRepository.deleteCommentsByReview(List.of(review.getId()));
    entityManager.clear();

    // Then
    assertThat(commentJpaRepository.findAll())
        .extracting(Comment::getContent)
        .containsExactly("otherComment");
  }
}
