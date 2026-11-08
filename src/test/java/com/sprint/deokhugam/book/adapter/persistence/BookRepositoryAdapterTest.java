package com.sprint.deokhugam.book.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.application.required.UniqueConstraintViolationException;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.common.config.JpaConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

// UNIQUE 위반 판별은 PostgreSQL의 SQLState에 기대므로 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, BookRepositoryAdapter.class, JacksonAutoConfiguration.class})
class BookRepositoryAdapterTest {

  @Autowired private BookRepositoryAdapter bookRepositoryAdapter;

  @Test
  @DisplayName("같은 ISBN을 사전 확인 없이 두 번 저장하면 두 번째가 UNIQUE 위반 예외로 끝난다")
  void saveSameIsbnTwice() {
    bookRepositoryAdapter.save(book("클린 코드", "9788966260959"));

    assertThatThrownBy(() -> bookRepositoryAdapter.save(book("클린 코드", "9788966260959")))
        .isInstanceOf(UniqueConstraintViolationException.class);
  }

  @Test
  @DisplayName("UNIQUE가 아닌 제약 위반은 바꾸지 않고 그대로 올린다")
  void saveNotNullViolation() {
    assertThatThrownBy(() -> bookRepositoryAdapter.save(book(null, "9788966260959")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(UniqueConstraintViolationException.class);
  }

  private Book book(String title, String isbn) {
    return Book.create(
        title, "로버트 C. 마틴", "소개", "인사이트", LocalDate.of(2013, 12, 24), Isbn.of(isbn), null);
  }
}
