package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.FakeBookRepositoryPort;
import com.sprint.deokhugam.book.FakeBookSearchPort;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookSearchCondition;
import com.sprint.deokhugam.book.application.dto.BookSortField;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class BookQueryServiceTest {

  private final FakeBookRepositoryPort bookRepositoryPort = new FakeBookRepositoryPort();
  private final BookQueryService bookQueryService =
      new BookQueryService(bookRepositoryPort, new FakeBookSearchPort(bookRepositoryPort));

  private Book savedBook() {
    return bookRepositoryPort.save(
        Book.create(
            "클린 코드",
            "로버트 C. 마틴",
            "소개",
            "인사이트",
            LocalDate.of(2013, 12, 24),
            Isbn.of("9788966260959"),
            null));
  }

  private Book savedBook(String title, String author, String isbn, LocalDate publishedDate) {
    return bookRepositoryPort.save(
        Book.create(title, author, "소개", "출판사", publishedDate, Isbn.of(isbn), null));
  }

  // 평점 rating인 리뷰가 1개 달린 도서
  private Book savedBookWithRating(String title, int rating) {
    Book book = savedBook(title, "저자", null, LocalDate.of(2020, 1, 1));
    book.addReview(rating);
    return book;
  }

  private BookSearchCondition condition(String keyword, BookSortField field, Sort.Direction dir) {
    return new BookSearchCondition(keyword, field, dir, null, null);
  }

  private static List<String> titles(CursorPageResponse<BookDto> page) {
    return page.content().stream().map(BookDto::title).toList();
  }

  // 커서로 끝까지 넘기면서 제목을 순서대로 모은다 (중복·누락 검증용)
  private List<String> collectAllPages(BookSortField field, Sort.Direction dir, int limit) {
    List<String> collected = new ArrayList<>();
    String cursor = null;
    java.time.Instant after = null;
    while (true) {
      CursorPageResponse<BookDto> page =
          bookQueryService.search(new BookSearchCondition(null, field, dir, cursor, after), limit);
      collected.addAll(titles(page));
      if (!page.hasNext()) {
        return collected;
      }
      cursor = page.nextCursor();
      after = page.nextAfter();
    }
  }

  // ===== (1) findById() : Book 엔티티 반환 =====
  // (1-1)
  @Test
  @DisplayName("id로 도서를 찾으면 저장된 도서를 돌려준다")
  void findById() {
    Book saved = savedBook();

    assertThat(bookQueryService.findById(saved.getId())).isEqualTo(saved);
  }

  // ===== (2) getById() : BookDto 반환 =====
  // (2-1)
  @Test
  @DisplayName("id로 도서 상세를 조회하면 도서 정보 응답을 돌려준다")
  void getById() {
    Book saved = savedBook();

    BookDto bookDto = bookQueryService.getById(saved.getId());

    assertThat(bookDto.id()).isEqualTo(saved.getId());
    assertThat(bookDto.title()).isEqualTo("클린 코드");
    assertThat(bookDto.isbn()).isEqualTo("9788966260959");
    assertThat(bookDto.reviewCount()).isZero();
    assertThat(bookDto.rating()).isZero();
  }

  // (2-2)
  @Test
  @DisplayName("없는 id로 조회하면 NOT_FOUND 예외가 발생한다")
  void getByIdNotFound() {
    assertThatThrownBy(() -> bookQueryService.getById(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // (2-3)
  @Test
  @DisplayName("논리 삭제된 도서는 없는 것으로 보고 NOT_FOUND 예외가 발생한다")
  void getByIdDeleted() {
    Book saved = savedBook();
    saved.softDelete();

    assertThatThrownBy(() -> bookQueryService.getById(saved.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // ===== (3) search() : 키워드 검색 =====
  // (3-1)
  @Test
  @DisplayName("제목·저자·ISBN 중 하나라도 키워드를 포함하면 검색된다")
  void searchByKeyword() {
    savedBook("클린 코드", "로버트 C. 마틴", "9788966260959", LocalDate.of(2013, 12, 24));
    savedBook("리팩터링", "마틴 파울러", "9791162242742", LocalDate.of(2020, 4, 1));

    assertThat(
            titles(
                bookQueryService.search(
                    condition("클린", BookSortField.TITLE, Sort.Direction.ASC), 10)))
        .containsExactly("클린 코드");
    assertThat(
            titles(
                bookQueryService.search(
                    condition("마틴", BookSortField.TITLE, Sort.Direction.ASC), 10)))
        .containsExactly("리팩터링", "클린 코드");
    assertThat(
            titles(
                bookQueryService.search(
                    condition("2742", BookSortField.TITLE, Sort.Direction.ASC), 10)))
        .containsExactly("리팩터링");
  }

  // (3-2)
  @Test
  @DisplayName("키워드는 대소문자를 구분하지 않는다")
  void searchIgnoresCase() {
    savedBook("Clean Code", "Robert C. Martin", null, LocalDate.of(2008, 8, 1));

    CursorPageResponse<BookDto> page =
        bookQueryService.search(condition("clean", BookSortField.TITLE, Sort.Direction.ASC), 10);

    assertThat(titles(page)).containsExactly("Clean Code");
  }

  // (3-3)
  @Test
  @DisplayName("키워드에 맞는 도서가 없으면 빈 목록과 totalElements 0을 돌려준다")
  void searchNoResult() {
    savedBook();

    CursorPageResponse<BookDto> page =
        bookQueryService.search(condition("없는 책", BookSortField.TITLE, Sort.Direction.ASC), 10);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
    assertThat(page.nextAfter()).isNull();
  }

  // (3-4)
  @Test
  @DisplayName("논리 삭제된 도서는 목록과 totalElements에서 제외한다")
  void searchExcludesDeleted() {
    savedBook("남은 책", "저자", null, LocalDate.of(2020, 1, 1));
    savedBook("지운 책", "저자", null, LocalDate.of(2020, 1, 1)).softDelete();

    CursorPageResponse<BookDto> page =
        bookQueryService.search(condition(null, BookSortField.TITLE, Sort.Direction.ASC), 10);

    assertThat(titles(page)).containsExactly("남은 책");
    assertThat(page.totalElements()).isEqualTo(1);
  }

  // ===== (4) search() : 정렬과 커서 페이지 =====
  // (4-1)
  @Test
  @DisplayName("다음 페이지가 있으면 hasNext와 마지막 도서의 정렬값·createdAt을 커서로 돌려준다")
  void searchFirstPage() {
    savedBook("A", "저자", null, LocalDate.of(2020, 1, 1));
    Book second = savedBook("B", "저자", null, LocalDate.of(2020, 1, 1));
    savedBook("C", "저자", null, LocalDate.of(2020, 1, 1));

    CursorPageResponse<BookDto> page =
        bookQueryService.search(condition(null, BookSortField.TITLE, Sort.Direction.DESC), 2);

    assertThat(titles(page)).containsExactly("C", "B");
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.hasNext()).isTrue();
    assertThat(page.nextCursor()).isEqualTo("B");
    assertThat(page.nextAfter()).isEqualTo(second.getCreatedAt());
  }

  // (4-2)
  @Test
  @DisplayName("마지막 페이지면 hasNext가 false이고 커서는 null이다")
  void searchLastPage() {
    savedBook("A", "저자", null, LocalDate.of(2020, 1, 1));
    savedBook("B", "저자", null, LocalDate.of(2020, 1, 1));

    CursorPageResponse<BookDto> page =
        bookQueryService.search(condition(null, BookSortField.TITLE, Sort.Direction.DESC), 2);

    assertThat(titles(page)).containsExactly("B", "A");
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
    assertThat(page.nextAfter()).isNull();
  }

  // (4-3)
  @Test
  @DisplayName("평점이 같은 도서는 createdAt으로 갈라서 페이지를 넘겨도 중복·누락이 없다")
  void searchRatingWithTies() {
    savedBookWithRating("먼저 등록한 5점", 5);
    savedBookWithRating("나중 등록한 5점", 5);
    savedBookWithRating("3점", 3);
    savedBookWithRating("먼저 등록한 1점", 1);
    savedBookWithRating("나중 등록한 1점", 1);

    // DESC: 평점 높은 순, 같으면 나중에 등록한 것부터
    assertThat(collectAllPages(BookSortField.RATING, Sort.Direction.DESC, 2))
        .containsExactly("나중 등록한 5점", "먼저 등록한 5점", "3점", "나중 등록한 1점", "먼저 등록한 1점");
    // ASC: 평점 낮은 순, 같으면 먼저 등록한 것부터
    assertThat(collectAllPages(BookSortField.RATING, Sort.Direction.ASC, 2))
        .containsExactly("먼저 등록한 1점", "나중 등록한 1점", "3점", "먼저 등록한 5점", "나중 등록한 5점");
  }

  // (4-4)
  @Test
  @DisplayName("출간일 정렬은 날짜 커서로 다음 페이지를 이어 준다")
  void searchByPublishedDate() {
    savedBook("2013", "저자", null, LocalDate.of(2013, 12, 24));
    savedBook("2020", "저자", null, LocalDate.of(2020, 4, 1));
    savedBook("2008", "저자", null, LocalDate.of(2008, 8, 1));

    CursorPageResponse<BookDto> first =
        bookQueryService.search(
            condition(null, BookSortField.PUBLISHED_DATE, Sort.Direction.ASC), 2);
    assertThat(titles(first)).containsExactly("2008", "2013");
    assertThat(first.nextCursor()).isEqualTo("2013-12-24");

    CursorPageResponse<BookDto> second =
        bookQueryService.search(
            new BookSearchCondition(
                null,
                BookSortField.PUBLISHED_DATE,
                Sort.Direction.ASC,
                first.nextCursor(),
                first.nextAfter()),
            2);
    assertThat(titles(second)).containsExactly("2020");
    assertThat(second.hasNext()).isFalse();
  }

  // (4-5)
  @Test
  @DisplayName("리뷰 수 정렬은 숫자 커서로 다음 페이지를 이어 준다")
  void searchByReviewCount() {
    Book many = savedBook("리뷰 3개", "저자", null, LocalDate.of(2020, 1, 1));
    many.addReview(5);
    many.addReview(4);
    many.addReview(3);
    Book one = savedBook("리뷰 1개", "저자", null, LocalDate.of(2020, 1, 1));
    one.addReview(5);
    savedBook("리뷰 0개", "저자", null, LocalDate.of(2020, 1, 1));

    CursorPageResponse<BookDto> first =
        bookQueryService.search(
            condition(null, BookSortField.REVIEW_COUNT, Sort.Direction.DESC), 1);
    assertThat(titles(first)).containsExactly("리뷰 3개");
    assertThat(first.nextCursor()).isEqualTo("3");

    assertThat(collectAllPages(BookSortField.REVIEW_COUNT, Sort.Direction.DESC, 1))
        .containsExactly("리뷰 3개", "리뷰 1개", "리뷰 0개");
  }

  // (4-6)
  @Test
  @DisplayName("키워드 검색 결과도 커서로 끝까지 넘기면 중복·누락이 없다")
  void searchKeywordWithPages() {
    savedBook("자바 1", "저자", null, LocalDate.of(2020, 1, 1));
    savedBook("파이썬", "저자", null, LocalDate.of(2020, 1, 1));
    savedBook("자바 2", "저자", null, LocalDate.of(2020, 1, 1));
    savedBook("자바 3", "저자", null, LocalDate.of(2020, 1, 1));

    List<String> collected = new ArrayList<>();
    String cursor = null;
    java.time.Instant after = null;
    do {
      CursorPageResponse<BookDto> page =
          bookQueryService.search(
              new BookSearchCondition("자바", BookSortField.TITLE, Sort.Direction.ASC, cursor, after),
              2);
      collected.addAll(titles(page));
      assertThat(page.totalElements()).isEqualTo(3);
      cursor = page.nextCursor();
      after = page.nextAfter();
    } while (cursor != null);

    assertThat(collected).containsExactly("자바 1", "자바 2", "자바 3");
  }
}
