package com.sprint.deokhugam.dashboard.review;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.deokhugam.book.adapter.persistence.BookJpaRepository;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.comment.adapter.persistence.CommentJpaRepository;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.dashboard.review.application.provided.PopularReviewUseCase;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.review.adapter.persistence.ReviewJpaRepository;
import com.sprint.deokhugam.review.adapter.persistence.ReviewLikeJpaRepository;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.review.domain.ReviewLike;
import com.sprint.deokhugam.user.adapter.persistence.UserJpaRepository;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class PopularReviewIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private PopularReviewUseCase popularReviewUseCase;
  @Autowired private BookJpaRepository bookJpaRepository;
  @Autowired private ReviewJpaRepository reviewJpaRepository;
  @Autowired private ReviewLikeJpaRepository reviewLikeJpaRepository;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private CommentJpaRepository commentJpaRepository;

  private User writer;
  private Review popularReview;
  private Review normalReview;
  private int userSequence;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // repository test와 달리 특별히 지시하지 않는 이상 메인 DB 설정을 임의로 바꾸지 않기 때문에 URL을 H2로 강제 덮어쓰기
    registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "false");
  }

  // 테스트 시나리오
  // 1. 낮 동안 활동이 쌓인다 → 리뷰 2개에 좋아요·댓글이 달림
  // 2. 밤 0시에 배치가 돈다 → 점수 계산 후 popular_reviews 표에 저장
  // 3. 사용자가 대시보드를 연다 → API로 조회
  @BeforeEach
  void setUp() {
    writer = saveUser();

    Book book =
        bookJpaRepository.save(
            Book.create(
                "testTitle",
                "testAuthor",
                "testDescription",
                "testPublisher",
                LocalDate.of(2011, 1, 1),
                null,
                "https://example.com/thumbnails/testTitle.jpg"));

    // 인기 리뷰: 좋아요 1 + 댓글 2 = 1.7점 / 일반 리뷰: 좋아요 1 = 0.3점
    popularReview = reviewJpaRepository.save(Review.create(5, "popularReview", writer, book));
    normalReview = reviewJpaRepository.save(Review.create(3, "normalReview", writer, book));

    reviewLikeJpaRepository.save(ReviewLike.init(popularReview, saveUser()));
    reviewLikeJpaRepository.save(ReviewLike.init(normalReview, saveUser()));
    commentJpaRepository.save(Comment.create(popularReview, writer, "comment1"));
    commentJpaRepository.save(Comment.create(popularReview, writer, "comment2"));

    // 오늘 생긴 활동이 일간 순위에 들어가도록, 내일 0시에 배치가 돈 것처럼 집계
    LocalDate baseDate = LocalDate.now(PopularReviewPeriod.ZONE).plusDays(1);
    for (PopularReviewPeriod period : PopularReviewPeriod.values()) {
      popularReviewUseCase.aggregatePopularReviews(period, baseDate);
    }
  }

  private User saveUser() {
    userSequence++;
    return userJpaRepository.save(
        User.create(
            "tester" + userSequence,
            Email.to("tester" + userSequence + "@example.com"),
            Password.to("abcd1234!")));
  }

  @Test
  @DisplayName("통합 테스트: 집계 후 조회하면 점수 높은 순으로 순위와 리뷰 정보가 나온다")
  void aggregateAndSearchIntegration() throws Exception {
    mockMvc
        .perform(
            get("/api/reviews/popular")
                .param("period", "DAILY")
                .param("direction", "DESC")
                .param("limit", "20"))
        .andExpect(status().isOk())
        // 필드가 제대로 채워지는지 확인
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].rank").value(1))
        .andExpect(jsonPath("$.content[0].reviewId").value(popularReview.getId().toString()))
        .andExpect(jsonPath("$.content[0].score").value(1.7))
        .andExpect(jsonPath("$.content[0].likeCount").value(1))
        .andExpect(jsonPath("$.content[0].commentCount").value(2))
        .andExpect(jsonPath("$.content[0].bookTitle").value("testTitle"))
        .andExpect(jsonPath("$.content[0].userNickname").value(writer.getNickname()))
        .andExpect(jsonPath("$.content[0].reviewRating").value(5.0))
        // 순서가 맞는지 확인
        .andExpect(jsonPath("$.content[1].rank").value(2))
        .andExpect(jsonPath("$.content[1].reviewId").value(normalReview.getId().toString()))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("통합 테스트: 커서로 다음 페이지를 이어서 조회")
  void cursorPaginationIntegration() throws Exception {
    // Given: 첫 페이지 1개
    MvcResult firstPage =
        mockMvc
            .perform(
                get("/api/reviews/popular")
                    .param("period", "ALL_TIME")
                    .param("direction", "DESC")
                    .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.nextCursor").value("1"))
            .andReturn();
    JsonNode body = jsonMapper.readTree(firstPage.getResponse().getContentAsString());

    // When & Then: 받은 커서로 다음 페이지
    mockMvc
        .perform(
            get("/api/reviews/popular")
                .param("period", "ALL_TIME")
                .param("direction", "DESC")
                .param("limit", "1")
                .param("cursor", body.get("nextCursor").asString())
                .param("after", body.get("nextAfter").asString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].rank").value(2))
        // 2위가 normalReview가 맞는지 확인
        .andExpect(jsonPath("$.content[0].reviewId").value(normalReview.getId().toString()))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }
}
