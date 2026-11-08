package com.sprint.deokhugam.comment.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.comment.application.dto.CommentSearchCondition;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.common.config.QuerydslConfig;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@EnableJpaAuditing
@ActiveProfiles("test")
@Import({CommentQuerydslSearchAdapter.class, QuerydslConfig.class, JacksonAutoConfiguration.class})
public class CommentQuerydslSearchAdapterTest {

  private static final Instant OLD = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant MIDDLE = Instant.parse("2026-11-02T00:00:00Z");
  private static final Instant NEW = Instant.parse("2026-11-03T00:00:00Z");

  @Autowired private CommentQuerydslSearchAdapter searchAdapter;
  @Autowired private TestEntityManager entityManager;

  private Review review;
  private Review otherReview;
  private User user;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "false");
  }

  @BeforeEach
  void setUp() {
    user = User.create("testNickname", Email.to("test@example.com"), Password.to("abcd1234!"));
    entityManager.persist(user);

    User otherUser =
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
  }

  // 생성 시각은 저장할 때 자동으로 찍혀서, 정렬 확인을 위해 저장 후 원하는 시각으로 바꾼다
  private Comment saveComment(Review targetReview, String content, Instant createdAt) {
    Comment comment = entityManager.persistAndFlush(Comment.create(targetReview, user, content));
    entityManager
        .getEntityManager()
        .createQuery("update Comment c set c.createdAt = :createdAt where c.id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", comment.getId())
        .executeUpdate();
    entityManager.clear();
    return comment;
  }

  private void saveThreeComments() {
    saveComment(review, "oldComment", OLD);
    saveComment(review, "middleComment", MIDDLE);
    saveComment(review, "newComment", NEW);
  }

  private CommentSearchCondition condition(Sort.Direction direction, Instant cursor, int limit) {
    return new CommentSearchCondition(review.getId(), direction, cursor, cursor, limit);
  }

  private List<String> contents(CommentSearchCondition condition) {
    return searchAdapter.search(condition).stream().map(Comment::getContent).toList();
  }

  @Test
  @DisplayName("댓글 검색 - DESC는 최신순으로 조회")
  void search_descending() {
    // Given
    saveThreeComments();

    // When
    List<String> result = contents(condition(Sort.Direction.DESC, null, 10));

    // Then
    assertThat(result).containsExactly("newComment", "middleComment", "oldComment");
  }

  @Test
  @DisplayName("댓글 검색 - ASC는 오래된 순으로 조회")
  void search_ascending() {
    // Given
    saveThreeComments();

    // When
    List<String> result = contents(condition(Sort.Direction.ASC, null, 10));

    // Then
    assertThat(result).containsExactly("oldComment", "middleComment", "newComment");
  }

  @Test
  @DisplayName("댓글 검색 - DESC 커서는 커서보다 오래된 댓글만 조회")
  void search_descendingCursor() {
    // Given
    saveThreeComments();

    // When
    List<String> result = contents(condition(Sort.Direction.DESC, MIDDLE, 10));

    // Then
    assertThat(result).containsExactly("oldComment");
  }

  @Test
  @DisplayName("댓글 검색 - ASC 커서는 커서보다 최신 댓글만 조회")
  void search_ascendingCursor() {
    // Given
    saveThreeComments();

    // When
    List<String> result = contents(condition(Sort.Direction.ASC, MIDDLE, 10));

    // Then
    assertThat(result).containsExactly("newComment");
  }

  @Test
  @DisplayName("댓글 검색 - 다음 페이지 확인을 위해 limit + 1개까지 조회")
  void search_limitPlusOne() {
    // Given
    saveThreeComments();

    // When
    List<String> result = contents(condition(Sort.Direction.DESC, null, 1));

    // Then
    assertThat(result).containsExactly("newComment", "middleComment");
  }

  @Test
  @DisplayName("댓글 검색 - 논리 삭제된 댓글과 다른 리뷰의 댓글은 제외")
  void search_excludeDeletedAndOtherReview() {
    // Given
    saveComment(review, "activeComment", OLD);
    saveComment(otherReview, "otherReviewComment", OLD);
    UUID deletedId = saveComment(review, "deletedComment", OLD).getId();
    entityManager.find(Comment.class, deletedId).softDelete();
    entityManager.flush();
    entityManager.clear();

    CommentSearchCondition condition = condition(Sort.Direction.DESC, null, 10);

    // When
    List<String> result = contents(condition);
    long count = searchAdapter.count(condition);

    // Then
    assertThat(result).containsExactly("activeComment");
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("댓글 개수 - 커서와 상관없이 리뷰의 전체 댓글 수를 센다")
  void count_ignoreCursor() {
    // Given
    saveThreeComments();

    // When
    long count = searchAdapter.count(condition(Sort.Direction.DESC, MIDDLE, 1));

    // Then
    assertThat(count).isEqualTo(3L);
  }
}
