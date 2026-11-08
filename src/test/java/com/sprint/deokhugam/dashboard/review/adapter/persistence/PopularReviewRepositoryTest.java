package com.sprint.deokhugam.dashboard.review.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.common.config.JpaConfig;
import com.sprint.deokhugam.common.config.QuerydslConfig;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewDto;
import com.sprint.deokhugam.dashboard.review.application.dto.PopularReviewSearchCondition;
import com.sprint.deokhugam.dashboard.review.application.dto.ReviewActivity;
import com.sprint.deokhugam.dashboard.review.domain.PopularReview;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.review.domain.ReviewLike;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

// H2는 기간 검사 조건이 있는 popular_reviews 저장에서 오류가 난다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
  PopularReviewQuerydslSearchAdapter.class,
  PopularReviewJpaPersistencePortAdapter.class,
  QuerydslConfig.class,
  JpaConfig.class,
  // 파워 유저 기능에서 UserRankingStatsConverter는 엔티티의 값을 JSON으로 바꿔 DB에 저장하는 변환기.
  JacksonAutoConfiguration.class
})
public class PopularReviewRepositoryTest {

  @Autowired private PopularReviewQuerydslSearchAdapter searchAdapter;
  @Autowired private PopularReviewJpaPersistencePortAdapter persistenceAdapter;
  @Autowired private PopularReviewJpaRepository popularReviewJpaRepository;
  @Autowired private TestEntityManager entityManager;

  private User writer;
  private Book book;
  private int userSequence;

  @BeforeEach
  void setUp() {
    writer = persistUser();

    book =
        Book.create(
            "testTitle",
            "testAuthor",
            "testDescription",
            "testPublisher",
            LocalDate.of(2011, 1, 1),
            null,
            "https://example.com/thumbnails/testTitle.jpg");
    // 영속성 컨텍스트에 등록
    entityManager.persist(book);
  }

  // 진짜 사용자 객체를 writer 변수에 담는다
  private User persistUser() {
    userSequence++;
    User user =
        User.create(
            "tester" + userSequence,
            Email.to("tester" + userSequence + "@example.com"),
            Password.to("abcd1234!"));
    return entityManager.persist(user);
  }

  private Review persistReview(String content) {
    return entityManager.persist(Review.create(4, content, writer, book));
  }

  private void persistLikes(Review review, int count) {
    for (int i = 0; i < count; i++) {
      entityManager.persist(ReviewLike.init(review, persistUser()));
    }
  }

  private void persistComments(Review review, int count) {
    for (int i = 0; i < count; i++) {
      entityManager.persist(Comment.create(review, writer, "testComment" + i));
    }
  }

  private Map<UUID, ReviewActivity> activitiesByReviewId(PopularReviewPeriod.Range range) {
    return searchAdapter.findReviewActivities(range).stream()
        .collect(Collectors.toMap(ReviewActivity::reviewId, Function.identity()));
  }

  private PopularReviewSearchCondition condition(
      PopularReviewPeriod period, Sort.Direction direction, Long cursor, int limit) {
    return new PopularReviewSearchCondition(
        period, direction, cursor, cursor == null ? null : Instant.now(), limit);
  }

  // 점수 높은 리뷰부터 1, 2, 3위로 DAILY 순위를 저장
  private List<Review> persistDailyRankings() {
    Review first = persistReview("first");
    Review second = persistReview("second");
    Review third = persistReview("third");
    entityManager.persist(
        PopularReview.create(first.getId(), PopularReviewPeriod.DAILY, 1L, 1L, 5L));
    entityManager.persist(
        PopularReview.create(second.getId(), PopularReviewPeriod.DAILY, 2L, 1L, 2L));
    entityManager.persist(
        PopularReview.create(third.getId(), PopularReviewPeriod.DAILY, 3L, 1L, 0L));
    entityManager.flush();
    entityManager.clear();
    return List.of(first, second, third);
  }

  @Test
  @DisplayName("순위 조회 - DESC는 점수 높은 순(1위부터), limit보다 1개 더 조회")
  void search_descending() {
    // Given
    List<Review> reviews = persistDailyRankings();

    // When
    List<PopularReviewDto> result =
        searchAdapter.search(condition(PopularReviewPeriod.DAILY, Sort.Direction.DESC, null, 2));

    // Then
    assertThat(result).extracting(PopularReviewDto::rank).containsExactly(1L, 2L, 3L);
    PopularReviewDto first = result.get(0);
    assertThat(first.reviewId()).isEqualTo(reviews.get(0).getId());
    assertThat(first.bookTitle()).isEqualTo("testTitle");
    assertThat(first.userNickname()).isEqualTo(writer.getNickname());
    assertThat(first.reviewContent()).isEqualTo("first");
    assertThat(first.reviewRating()).isEqualTo(4.0);
    assertThat(first.score()).isEqualTo(PopularReview.calculateScore(1L, 5L));
  }

  @Test
  @DisplayName("순위 조회 - ASC는 점수 낮은 순(마지막 순위부터)")
  void search_ascending() {
    // Given
    persistDailyRankings();

    // When
    List<PopularReviewDto> result =
        searchAdapter.search(condition(PopularReviewPeriod.DAILY, Sort.Direction.ASC, null, 10));

    // Then
    assertThat(result).extracting(PopularReviewDto::rank).containsExactly(3L, 2L, 1L);
  }

  @Test
  @DisplayName("순위 조회 - 커서(이전 마지막 순위) 다음부터 조회")
  void search_withCursor() {
    // Given
    persistDailyRankings();

    // When
    List<PopularReviewDto> descNext =
        searchAdapter.search(condition(PopularReviewPeriod.DAILY, Sort.Direction.DESC, 1L, 10));
    List<PopularReviewDto> ascNext =
        searchAdapter.search(condition(PopularReviewPeriod.DAILY, Sort.Direction.ASC, 3L, 10));

    // Then
    assertThat(descNext).extracting(PopularReviewDto::rank).containsExactly(2L, 3L);
    assertThat(ascNext).extracting(PopularReviewDto::rank).containsExactly(2L, 1L);
  }

  @Test
  @DisplayName("순위 조회 - 다른 기간의 순위와 논리 삭제된 리뷰는 개수에서 빠진다")
  void search_filterPeriodAndDeletedReview() {
    // Given
    List<Review> reviews = persistDailyRankings();
    Review weeklyReview = persistReview("weekly");
    entityManager.persist(
        PopularReview.create(weeklyReview.getId(), PopularReviewPeriod.WEEKLY, 1L, 1L, 1L));
    Review deleted = entityManager.find(Review.class, reviews.get(2).getId());
    deleted.softDelete();
    entityManager.flush();
    entityManager.clear();
    PopularReviewSearchCondition daily =
        condition(PopularReviewPeriod.DAILY, Sort.Direction.DESC, null, 10);

    // When
    List<PopularReviewDto> result = searchAdapter.search(daily);
    long count = searchAdapter.count(daily);

    // Then
    assertThat(result).extracting(PopularReviewDto::rank).containsExactly(1L, 2L);
    assertThat(result).extracting(PopularReviewDto::period).containsOnly(PopularReviewPeriod.DAILY);
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @DisplayName("활동 집계 - 리뷰마다 좋아요 수와 댓글 수를 센다")
  void findReviewActivities_countLikesAndComments() {
    // Given
    Review review = persistReview("testReview");
    persistLikes(review, 2);
    persistComments(review, 3);
    entityManager.flush();
    entityManager.clear();

    // When
    Map<UUID, ReviewActivity> activities =
        activitiesByReviewId(PopularReviewPeriod.ALL_TIME.rangeOf(LocalDate.now()));

    // Then
    ReviewActivity activity = activities.get(review.getId());
    assertThat(activity.likeCount()).isEqualTo(2L);
    assertThat(activity.commentCount()).isEqualTo(3L);
  }

  @Test
  @DisplayName("활동 집계 - 취소된 좋아요는 세지 않고, 논리 삭제된 댓글은 센다")
  void findReviewActivities_excludeCanceledLikeAndIncludeDeletedComment() {
    // Given
    Review review = persistReview("testReview");
    ReviewLike canceledLike = ReviewLike.init(review, persistUser());
    canceledLike.toggle();
    entityManager.persist(canceledLike);
    Comment deletedComment = Comment.create(review, writer, "deletedComment");
    deletedComment.softDelete();
    entityManager.persist(deletedComment);
    persistComments(review, 1);
    entityManager.flush();
    entityManager.clear();

    // When
    ReviewActivity activity =
        activitiesByReviewId(PopularReviewPeriod.ALL_TIME.rangeOf(LocalDate.now()))
            .get(review.getId());

    // Then
    assertThat(activity.likeCount()).isZero();
    assertThat(activity.commentCount()).isEqualTo(2L);
  }

  @Test
  @DisplayName("활동 집계 - 논리 삭제된 리뷰는 집계 대상에서 빠진다")
  void findReviewActivities_excludeDeletedReview() {
    // Given
    Review deletedReview = persistReview("deletedReview");
    persistComments(deletedReview, 2);
    deletedReview.softDelete();
    entityManager.flush();
    entityManager.clear();

    // When
    Map<UUID, ReviewActivity> activities =
        activitiesByReviewId(PopularReviewPeriod.ALL_TIME.rangeOf(LocalDate.now()));

    // Then
    assertThat(activities).doesNotContainKey(deletedReview.getId());
  }

  @Test
  @DisplayName("활동 집계 - 기간 밖에서 생긴 좋아요와 댓글은 세지 않는다")
  void findReviewActivities_outOfRange() {
    // Given
    Review review = persistReview("testReview");
    persistLikes(review, 1);
    persistComments(review, 1);
    entityManager.flush();
    entityManager.clear();

    // 지난주 범위로 집계
    Instant now = Instant.now();
    PopularReviewPeriod.Range lastWeek =
        new PopularReviewPeriod.Range(
            now.minus(14, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS));

    // When
    Map<UUID, ReviewActivity> activities = activitiesByReviewId(lastWeek);

    // Then: 기간 안에 활동이 없는 리뷰는 결과에 없다
    assertThat(activities).doesNotContainKey(review.getId());
  }

  @Test
  @DisplayName("활동 집계 - 좋아요만 있는 리뷰와 댓글만 있는 리뷰를 모두 포함한다")
  void findReviewActivities_likeOnlyAndCommentOnly() {
    // Given
    Review likeOnlyReview = persistReview("likeOnlyReview");
    persistLikes(likeOnlyReview, 2);
    Review commentOnlyReview = persistReview("commentOnlyReview");
    persistComments(commentOnlyReview, 3);
    entityManager.flush();
    entityManager.clear();

    // When
    Map<UUID, ReviewActivity> activities =
        activitiesByReviewId(PopularReviewPeriod.ALL_TIME.rangeOf(LocalDate.now()));

    // Then
    ReviewActivity likeOnly = activities.get(likeOnlyReview.getId());
    assertThat(likeOnly.likeCount()).isEqualTo(2L);
    assertThat(likeOnly.commentCount()).isZero();

    ReviewActivity commentOnly = activities.get(commentOnlyReview.getId());
    assertThat(commentOnly.likeCount()).isZero();
    assertThat(commentOnly.commentCount()).isEqualTo(3L);
  }

  @Test
  @DisplayName("순위 저장 - 같은 기간의 기존 순위는 지우고 새 순위로 바꾸며, 다른 기간은 그대로 둔다")
  void replaceAll_onlyTargetPeriod() {
    // Given
    List<Review> reviews = persistDailyRankings();
    entityManager.persist(
        PopularReview.create(reviews.get(0).getId(), PopularReviewPeriod.WEEKLY, 1L, 1L, 1L));
    entityManager.flush();
    entityManager.clear();

    // When
    persistenceAdapter.replaceAll(
        PopularReviewPeriod.DAILY,
        List.of(
            PopularReview.create(reviews.get(1).getId(), PopularReviewPeriod.DAILY, 1L, 3L, 3L)));
    entityManager.flush();
    entityManager.clear();

    // Then
    List<PopularReview> saved = popularReviewJpaRepository.findAll();
    assertThat(saved)
        .filteredOn(popularReview -> popularReview.getPeriod() == PopularReviewPeriod.DAILY)
        .singleElement()
        .satisfies(
            popularReview -> {
              assertThat(popularReview.getReviewId()).isEqualTo(reviews.get(1).getId());
              assertThat(popularReview.getRank()).isEqualTo(1L);
            });
    assertThat(saved)
        .filteredOn(popularReview -> popularReview.getPeriod() == PopularReviewPeriod.WEEKLY)
        .hasSize(1);
  }
}
