package com.sprint.deokhugam.review.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.common.config.JpaConfig;
import com.sprint.deokhugam.common.config.QuerydslConfig;
import com.sprint.deokhugam.review.application.dto.req.ReviewSearchCondition;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 행 비교 커서는 HQL과 PostgreSQL에 기대므로 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
  QuerydslConfig.class,
  JpaConfig.class,
  ReviewQuerydslRepository.class,
  JacksonAutoConfiguration.class
})
class ReviewQuerydslRepositoryTest {

  private static final int PAGE_SIZE = 2;

  @Autowired private EntityManager entityManager;
  @Autowired private ReviewQuerydslRepository reviewQuerydslRepository;

  private int savedCount = 0;

  @Test
  @DisplayName("평점 내림차순 커서로 끝까지 넘기면 평점이 같아도 중복, 누락 없이 최신순으로 이어진다")
  void searchByRatingDesc() {
    saveReviews();

    assertThat(contentsOfAllPages("DESC")).containsExactly("5", "4 new", "4 old", "3 new", "3 old");
  }

  @Test
  @DisplayName("평점 오름차순 커서로 끝까지 넘기면 평점이 같아도 중복, 누락 없이 오래된 순으로 이어진다")
  void searchByRatingAsc() {
    saveReviews();

    assertThat(contentsOfAllPages("ASC")).containsExactly("3 old", "3 new", "4 old", "4 new", "5");
  }

  private void saveReviews() {
    saveReview(3, "3 old");
    saveReview(4, "4 old");
    saveReview(5, "5");
    saveReview(3, "3 new");
    saveReview(4, "4 new");
    entityManager.clear();
  }

  private void saveReview(int rating, String content) {
    savedCount++;
    User user =
        User.create(
            "tester" + savedCount,
            Email.to("tester" + savedCount + "@example.com"),
            Password.to("abcd1234!"));
    Book book =
        Book.create("제목" + savedCount, "저자", "소개", "출판사", LocalDate.of(2020, 1, 1), null, null);
    entityManager.persist(user);
    entityManager.persist(book);
    entityManager.persist(Review.create(rating, content, user, book));
    entityManager.flush();
  }

  private List<String> contentsOfAllPages(String direction) {
    List<String> contents = new ArrayList<>();
    String cursor = null;
    Instant after = null;
    while (true) {
      List<Review> rows =
          reviewQuerydslRepository.search(
              new ReviewSearchCondition(
                  null, null, null, "rating", direction, cursor, after, PAGE_SIZE));
      List<Review> page = rows.subList(0, Math.min(PAGE_SIZE, rows.size()));
      page.forEach(row -> contents.add(row.getContent()));
      if (rows.size() <= PAGE_SIZE) {
        return contents;
      }
      Review last = page.getLast();
      cursor = String.valueOf(last.getRating());
      after = last.getCreatedAt();
    }
  }
}
