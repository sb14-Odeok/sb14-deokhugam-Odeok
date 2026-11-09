package com.sprint.deokhugam.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.deokhugam.book.adapter.persistence.BookJpaRepository;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookSearchCondition;
import com.sprint.deokhugam.book.application.dto.BookSortField;
import com.sprint.deokhugam.book.application.provided.query.BookFinder;
import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.comment.adapter.persistence.CommentJpaRepository;
import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.review.adapter.persistence.ReviewJpaRepository;
import com.sprint.deokhugam.review.adapter.persistence.ReviewLikeJpaRepository;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.review.domain.ReviewLike;
import com.sprint.deokhugam.user.adapter.persistence.UserJpaRepository;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.Password;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private EntityManager entityManager;
  @Autowired private BookJpaRepository bookJpaRepository;
  @Autowired private BookRepositoryPort bookRepositoryPort;
  @Autowired private BookFinder bookFinder;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private ReviewJpaRepository reviewJpaRepository;
  @Autowired private ReviewLikeJpaRepository reviewLikeJpaRepository;
  @Autowired private CommentJpaRepository commentJpaRepository;

  private static final String VALID_JSON =
      """
      {
        "title": "클린 코드",
        "author": "로버트 C. 마틴",
        "description": "소개",
        "publisher": "인사이트",
        "publishedDate": "2013-12-24",
        "isbn": "9788966260959"
      }
      """;

  private static final String UPDATE_JSON =
      """
      {
        "title": "새 제목",
        "author": "새 저자",
        "description": "새 소개",
        "publisher": "새 출판사",
        "publishedDate": "2021-02-02"
      }
      """;

  private MockMultipartFile bookData(String json) {
    return new MockMultipartFile(
        "bookData", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
  }

  private Book book(String isbn) {
    return Book.create("제목", "저자", "소개", "출판사", LocalDate.of(2020, 1, 1), Isbn.of(isbn), null);
  }

  // ===== (1) POST /api/books : BookController.create() =====
  // (1-1)
  @Test
  @DisplayName("도서를 등록하면 DB에 저장되고 id와 생성 시각이 채워진 응답을 돌려준다")
  void create() throws Exception {
    mockMvc
        .perform(multipart("/api/books").file(bookData(VALID_JSON)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.title").value("클린 코드"))
        .andExpect(jsonPath("$.thumbnailUrl").doesNotExist());

    assertThat(bookJpaRepository.count()).isEqualTo(1);
    assertThat(bookJpaRepository.existsByIsbn(Isbn.of("9788966260959"))).isTrue();
  }

  // (1-1b)
  @Test
  @DisplayName("bookData 파트에 Content-Type이 없어도(octet-stream) JSON으로 읽어 등록한다")
  void createWithoutPartContentType() throws Exception {
    MockMultipartFile bookDataWithoutType =
        new MockMultipartFile(
            "bookData",
            "",
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            VALID_JSON.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/api/books").file(bookDataWithoutType))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("클린 코드"));

    assertThat(bookJpaRepository.count()).isEqualTo(1);
  }

  // (1-2)
  @Test
  @DisplayName("이미 등록된 ISBN으로 등록하면 409를 돌려주고 저장하지 않는다")
  void createWithDuplicateIsbn() throws Exception {
    mockMvc.perform(multipart("/api/books").file(bookData(VALID_JSON)));

    mockMvc
        .perform(multipart("/api/books").file(bookData(VALID_JSON)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BOOK_DUPLICATE_ISBN"));

    assertThat(bookJpaRepository.count()).isEqualTo(1);
  }

  // (1-3)
  @Test
  @DisplayName("필수 값이 없으면 400을 돌려주고 저장하지 않는다")
  void createWithoutRequiredField() throws Exception {
    String json = VALID_JSON.replace("\"publishedDate\": \"2013-12-24\",", "");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.publishedDate").value("출간일은 필수입니다."));

    assertThat(bookJpaRepository.count()).isZero();
  }

  // (1-4)
  @Test
  @DisplayName("ISBN 없이 여러 권 등록할 수 있다")
  void createWithoutIsbn() throws Exception {
    String json = VALID_JSON.replace(",\n  \"isbn\": \"9788966260959\"", "");

    mockMvc.perform(multipart("/api/books").file(bookData(json))).andExpect(status().isCreated());
    mockMvc.perform(multipart("/api/books").file(bookData(json))).andExpect(status().isCreated());

    assertThat(bookJpaRepository.count()).isEqualTo(2);
  }

  // (1-5)
  @Test
  @DisplayName("하이픈이 섞인 ISBN은 숫자 13자리로 정리해 저장한다")
  void createWithHyphenIsbn() throws Exception {
    String json = VALID_JSON.replace("9788966260959", "978-89-6626-095-9");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isbn").value("9788966260959"));

    assertThat(bookJpaRepository.existsByIsbn(Isbn.of("9788966260959"))).isTrue();
  }

  // (1-6)
  @Test
  @DisplayName("같은 책의 10자리 ISBN으로 다시 등록하면 409를 돌려준다")
  void createWithIsbn10OfSameBook() throws Exception {
    mockMvc.perform(multipart("/api/books").file(bookData(VALID_JSON)));
    String json = VALID_JSON.replace("9788966260959", "8966260950");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BOOK_DUPLICATE_ISBN"));

    assertThat(bookJpaRepository.count()).isEqualTo(1);
  }

  // (1-7)
  @Test
  @DisplayName("빈 문자열 ISBN은 NULL로 저장해 여러 권 등록할 수 있다")
  void createWithBlankIsbn() throws Exception {
    String json = VALID_JSON.replace("9788966260959", "");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isbn").doesNotExist());
    mockMvc.perform(multipart("/api/books").file(bookData(json))).andExpect(status().isCreated());

    assertThat(bookJpaRepository.count()).isEqualTo(2);
  }

  // (1-8)
  @Test
  @DisplayName("형식이 틀린 ISBN이면 400을 돌려주고 저장하지 않는다")
  void createWithInvalidIsbn() throws Exception {
    String json = VALID_JSON.replace("9788966260959", "9788966260950");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BOOK_INVALID_ISBN"));

    assertThat(bookJpaRepository.count()).isZero();
  }

  // ===== (2) books 테이블 제약 (isbn unique) =====
  // (2-1)
  @Test
  @DisplayName("중복 확인을 거치지 않아도 DB unique 제약이 같은 ISBN 저장을 막는다")
  void uniqueConstraint() {
    bookJpaRepository.saveAndFlush(book("9788966260959"));

    assertThatThrownBy(() -> bookJpaRepository.saveAndFlush(book("9788966260959")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // (2-2)
  @Test
  @DisplayName("DB에서 다시 읽은 도서의 ISBN은 컨버터가 Isbn 값 객체로 되돌려 준다")
  void isbnConverterRoundTrip() {
    Book saved = bookJpaRepository.saveAndFlush(book("978-89-6626-095-9"));
    entityManager.clear();

    Book found = bookJpaRepository.findById(saved.getId()).orElseThrow();

    assertThat(found.getIsbn()).isEqualTo(Isbn.of("9788966260959"));
    assertThat(found.getIsbn().value()).isEqualTo("9788966260959");
  }

  // ===== (3) BookFinder.findById() : 다른 도메인이 쓰는 조회 =====
  // (3-1)
  @Test
  @DisplayName("BookFinder로 저장한 도서를 찾는다")
  void findById() {
    Book saved = bookRepositoryPort.save(book(null));

    assertThat(bookFinder.findById(saved.getId())).isEqualTo(saved);
  }

  // (3-2)
  @Test
  @DisplayName("BookFinder로 없는 id를 찾으면 BOOK_NOT_FOUND 예외가 발생한다")
  void findByIdNotFound() {
    assertThatThrownBy(() -> bookFinder.findById(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // (3-3)
  @Test
  @DisplayName("BookFinder로 논리 삭제된 도서를 찾으면 BOOK_NOT_FOUND 예외가 발생한다")
  void findByIdDeleted() {
    Book saved = bookRepositoryPort.save(book(null));
    saved.softDelete();
    bookJpaRepository.flush();

    assertThatThrownBy(() -> bookFinder.findById(saved.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // ===== (4) GET /api/books/{bookId} : BookController.get() =====
  // (4-1)
  @Test
  @DisplayName("등록한 도서를 id로 조회하면 200과 저장된 정보를 돌려준다")
  void getBook() throws Exception {
    Book saved = bookRepositoryPort.save(book("9788966260959"));

    mockMvc
        .perform(get("/api/books/{bookId}", saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId().toString()))
        .andExpect(jsonPath("$.title").value("제목"))
        .andExpect(jsonPath("$.publishedDate").value("2020-01-01"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"))
        .andExpect(jsonPath("$.reviewCount").value(0))
        .andExpect(jsonPath("$.rating").value(0.0))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  // (4-2)
  @Test
  @DisplayName("없는 도서 id로 조회하면 404를 돌려준다")
  void getBookNotFound() throws Exception {
    mockMvc
        .perform(get("/api/books/{bookId}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (4-3)
  @Test
  @DisplayName("논리 삭제된 도서를 조회하면 404를 돌려준다")
  void getDeletedBook() throws Exception {
    Book saved = bookRepositoryPort.save(book(null));
    saved.softDelete();
    bookJpaRepository.flush();

    mockMvc
        .perform(get("/api/books/{bookId}", saved.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (4-4)
  @Test
  @DisplayName("도서 id가 UUID 형식이 아니면 400을 돌려준다")
  void getBookWithInvalidId() throws Exception {
    mockMvc
        .perform(get("/api/books/{bookId}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // ===== (5) PATCH /api/books/{bookId} : BookController.update() =====
  // (5-1)
  @Test
  @DisplayName("도서를 수정하면 DB에 반영되고 다시 조회해도 바뀐 값이 나온다")
  void update() throws Exception {
    Book saved = bookRepositoryPort.save(book("9788966260959"));

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId())
                .file(bookData(UPDATE_JSON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId().toString()))
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.publishedDate").value("2021-02-02"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"));

    // 변경 감지로 UPDATE가 나갔는지 DB에서 다시 읽어 확인한다
    bookJpaRepository.flush();
    Book found = bookJpaRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("새 제목");
    assertThat(found.getAuthor()).isEqualTo("새 저자");
    assertThat(found.getDescription()).isEqualTo("새 소개");
    assertThat(found.getPublisher()).isEqualTo("새 출판사");
    assertThat(found.getPublishedDate()).isEqualTo(LocalDate.of(2021, 2, 2));
    assertThat(found.getIsbn()).isEqualTo(Isbn.of("9788966260959"));
    assertThat(found.getVersion()).isEqualTo(1);

    mockMvc
        .perform(get("/api/books/{bookId}", saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"));
  }

  // (5-2)
  @Test
  @DisplayName("수정 요청에 isbn을 넣어도 ISBN은 바뀌지 않는다")
  void updateIgnoresIsbn() throws Exception {
    Book saved = bookRepositoryPort.save(book("9788966260959"));
    String json =
        UPDATE_JSON.replace(
            "\"publishedDate\"", "\"isbn\": \"9780000000000\",\n  \"publishedDate\"");

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId()).file(bookData(json)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isbn").value("9788966260959"));

    bookJpaRepository.flush();
    assertThat(bookJpaRepository.existsByIsbn(Isbn.of("9788966260959"))).isTrue();
    assertThat(bookJpaRepository.existsByIsbn(Isbn.of("9791162242742"))).isFalse();
  }

  // (5-3)
  @Test
  @DisplayName("필수 값이 없으면 400을 돌려주고 DB 값은 그대로다")
  void updateWithoutRequiredField() throws Exception {
    Book saved = bookRepositoryPort.save(book(null));
    String json = UPDATE_JSON.replace("\"author\": \"새 저자\",", "");

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId()).file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.author").value("저자는 필수입니다."));

    Book found = bookJpaRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("제목");
    assertThat(found.getAuthor()).isEqualTo("저자");
  }

  // (5-4)
  @Test
  @DisplayName("없는 도서를 수정하면 404를 돌려준다")
  void updateNotFound() throws Exception {
    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", UUID.randomUUID())
                .file(bookData(UPDATE_JSON)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (5-5)
  @Test
  @DisplayName("논리 삭제된 도서를 수정하면 404를 돌려주고 값은 그대로다")
  void updateDeletedBook() throws Exception {
    Book saved = bookRepositoryPort.save(book(null));
    saved.softDelete();
    bookJpaRepository.flush();

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId())
                .file(bookData(UPDATE_JSON)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));

    Book found = bookJpaRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("제목");
  }

  // (5-6)
  @Test
  @DisplayName("읽은 뒤 다른 요청이 먼저 수정했으면 저장할 때 낙관적 락 예외가 난다")
  void updateConflict() {
    Book saved = bookRepositoryPort.save(book(null));
    flushAndClear();
    Book book = bookJpaRepository.findById(saved.getId()).orElseThrow();

    // 다른 요청이 먼저 커밋한 상황을 SQL로 만든다
    entityManager
        .createNativeQuery("update books set version = version + 1 where id = :id")
        .setParameter("id", saved.getId())
        .executeUpdate();
    book.update("새 제목", "새 저자", "새 소개", "새 출판사", LocalDate.of(2021, 2, 2));

    assertThatThrownBy(() -> bookJpaRepository.flush())
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  // ===== (7) DELETE /api/books/{bookId} : BookController.delete() =====

  // 리뷰, 댓글, 좋아요가 하나씩 달린 도서
  private Book bookWithReview() {
    User user =
        userJpaRepository.save(
            User.create("닉네임", Email.to("book-delete@example.com"), Password.to("abcd1234!")));
    Book book = bookRepositoryPort.save(book(null));
    Review review = reviewJpaRepository.save(Review.create(5, "리뷰", user, book));
    commentJpaRepository.save(Comment.create(review, user, "댓글"));
    reviewLikeJpaRepository.save(ReviewLike.init(review, user));
    flushAndClear();
    return book;
  }

  // (7-1)
  @Test
  @DisplayName("논리 삭제하면 204를 돌려주고 DB에 deletedAt이 찍히며 리뷰는 그대로다")
  void deleteBook() throws Exception {
    Book saved = bookWithReview();

    mockMvc.perform(delete("/api/books/{bookId}", saved.getId())).andExpect(status().isNoContent());
    flushAndClear();

    Book found = bookJpaRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.isDeleted()).isTrue();
    assertThat(reviewJpaRepository.count()).isEqualTo(1);
    mockMvc.perform(get("/api/books/{bookId}", saved.getId())).andExpect(status().isNotFound());
  }

  // (7-2)
  @Test
  @DisplayName("이미 논리 삭제된 도서를 다시 논리 삭제하면 404를 돌려준다")
  void deleteAlreadyDeleted() throws Exception {
    Book saved = bookRepositoryPort.save(book(null));
    saved.softDelete();
    bookJpaRepository.flush();

    mockMvc
        .perform(delete("/api/books/{bookId}", saved.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // ===== (8) DELETE /api/books/{bookId}/hard : BookController.hardDelete() =====
  // (8-1)
  @Test
  @DisplayName("물리 삭제하면 204를 돌려주고 도서, 리뷰, 댓글, 좋아요 행이 모두 사라진다")
  void hardDeleteBook() throws Exception {
    Book saved = bookWithReview();

    mockMvc
        .perform(delete("/api/books/{bookId}/hard", saved.getId()))
        .andExpect(status().isNoContent());
    flushAndClear();

    assertThat(bookJpaRepository.findById(saved.getId())).isEmpty();
    assertThat(reviewJpaRepository.count()).isZero();
    assertThat(commentJpaRepository.count()).isZero();
    assertThat(reviewLikeJpaRepository.count()).isZero();
  }

  // (8-2)
  @Test
  @DisplayName("논리 삭제된 도서도 물리 삭제하면 204를 돌려주고 행이 사라진다")
  void hardDeleteSoftDeletedBook() throws Exception {
    Book saved = bookRepositoryPort.save(book(null));
    saved.softDelete();
    flushAndClear();

    mockMvc
        .perform(delete("/api/books/{bookId}/hard", saved.getId()))
        .andExpect(status().isNoContent());
    flushAndClear();

    assertThat(bookJpaRepository.findById(saved.getId())).isEmpty();
  }

  // (8-3)
  @Test
  @DisplayName("없는 도서를 물리 삭제하면 404를 돌려준다")
  void hardDeleteNotFound() throws Exception {
    mockMvc
        .perform(delete("/api/books/{bookId}/hard", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // ===== (6) GET /api/books : BookController.search() — 실제 SQL 검증 =====

  private Book savedBook(String title, String author, LocalDate publishedDate, int... scores) {
    Book book = Book.create(title, author, "소개", "출판사", publishedDate, null, null);
    for (int score : scores) {
      book.addReview(score);
    }
    return bookRepositoryPort.save(book);
  }

  // 저장한 엔티티를 버리고 DB에서 다시 읽게 한다 → createdAt이 DB에 저장된 정밀도(마이크로초)로 맞춰진다
  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private BookSearchCondition condition(
      String keyword, BookSortField field, Sort.Direction dir, String cursor, Instant after) {
    return new BookSearchCondition(keyword, field, dir, cursor, after);
  }

  // 커서로 끝까지 넘기면서 제목을 순서대로 모은다 (중복·누락 검증용)
  private List<String> collectAllPages(
      String keyword, BookSortField field, Sort.Direction dir, int limit) {
    List<String> collected = new ArrayList<>();
    String cursor = null;
    Instant after = null;
    while (true) {
      CursorPageResponse<BookDto> page =
          bookFinder.search(condition(keyword, field, dir, cursor, after), limit);
      collected.addAll(page.content().stream().map(BookDto::title).toList());
      if (!page.hasNext()) {
        return collected;
      }
      cursor = page.nextCursor();
      after = page.nextAfter();
    }
  }

  // (6-1)
  @Test
  @DisplayName("키워드는 제목·저자·ISBN에 대소문자 없이 부분 일치하고 논리 삭제는 제외한다")
  void searchByKeyword() throws Exception {
    savedBook("Clean Code", "Robert C. Martin", LocalDate.of(2008, 8, 1));
    savedBook("리팩터링", "마틴 파울러", LocalDate.of(2020, 4, 1));
    bookRepositoryPort.save(book("9791162242742")); // 제목 "제목", ISBN으로만 걸린다
    savedBook("clean architecture", "저자", LocalDate.of(2017, 9, 1)).softDelete();
    flushAndClear();

    mockMvc
        .perform(get("/api/books").param("keyword", "CLEAN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Clean Code"))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(get("/api/books").param("keyword", "마틴"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("리팩터링"));

    mockMvc
        .perform(get("/api/books").param("keyword", "2742"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("9791162242742"));
  }

  // (6-2)
  @Test
  @DisplayName("평점 동률이 있어도 커서로 끝까지 넘기면 중복·누락 없이 모두 나온다")
  void searchRatingPagesWithTies() {
    savedBook("5점 먼저", "저자", LocalDate.of(2020, 1, 1), 5);
    savedBook("5점 나중", "저자", LocalDate.of(2020, 1, 1), 5);
    savedBook("3점", "저자", LocalDate.of(2020, 1, 1), 3);
    savedBook("1점 먼저", "저자", LocalDate.of(2020, 1, 1), 1);
    savedBook("1점 나중", "저자", LocalDate.of(2020, 1, 1), 1);
    flushAndClear();

    assertThat(collectAllPages(null, BookSortField.RATING, Sort.Direction.DESC, 2))
        .containsExactly("5점 나중", "5점 먼저", "3점", "1점 나중", "1점 먼저");
    assertThat(collectAllPages(null, BookSortField.RATING, Sort.Direction.ASC, 2))
        .containsExactly("1점 먼저", "1점 나중", "3점", "5점 먼저", "5점 나중");
  }

  // (6-3)
  @Test
  @DisplayName("제목·출간일·리뷰 수 정렬도 커서로 끝까지 넘기면 중복·누락이 없다")
  void searchOtherSortsWithPages() {
    savedBook("B", "저자", LocalDate.of(2013, 1, 1), 5, 4);
    savedBook("A", "저자", LocalDate.of(2020, 1, 1), 5);
    savedBook("C", "저자", LocalDate.of(2008, 1, 1));
    savedBook("B", "저자", LocalDate.of(2013, 1, 1), 5, 4); // 제목·출간일·리뷰 수 모두 동률
    flushAndClear();

    assertThat(collectAllPages(null, BookSortField.TITLE, Sort.Direction.ASC, 1))
        .containsExactly("A", "B", "B", "C");
    assertThat(collectAllPages(null, BookSortField.PUBLISHED_DATE, Sort.Direction.DESC, 1))
        .containsExactly("A", "B", "B", "C");
    assertThat(collectAllPages(null, BookSortField.REVIEW_COUNT, Sort.Direction.DESC, 3))
        .containsExactly("B", "B", "A", "C");
  }

  // (6-4)
  @Test
  @DisplayName("HTTP 응답의 nextCursor·nextAfter를 그대로 다음 요청에 넣으면 이어진다")
  void searchNextPageOverHttp() throws Exception {
    savedBook("A", "저자", LocalDate.of(2020, 1, 1));
    savedBook("B", "저자", LocalDate.of(2020, 1, 1));
    savedBook("C", "저자", LocalDate.of(2020, 1, 1));
    flushAndClear();

    String firstPage =
        mockMvc
            .perform(get("/api/books").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("C"))
            .andExpect(jsonPath("$.content[1].title").value("B"))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode json = objectMapper.readTree(firstPage);

    mockMvc
        .perform(
            get("/api/books")
                .param("limit", "2")
                .param("cursor", json.get("nextCursor").asText())
                .param("after", json.get("nextAfter").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("A"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  // (6-5)
  @Test
  @DisplayName("도서가 하나도 없으면 빈 목록과 totalElements 0을 돌려준다")
  void searchEmpty() throws Exception {
    mockMvc
        .perform(get("/api/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.hasNext").value(false));
  }
}
