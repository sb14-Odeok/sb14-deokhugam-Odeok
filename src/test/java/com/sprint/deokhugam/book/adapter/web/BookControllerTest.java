package com.sprint.deokhugam.book.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.deokhugam.book.FakeBookProviderPort;
import com.sprint.deokhugam.book.FakeBookRepositoryPort;
import com.sprint.deokhugam.book.FakeBookSearchPort;
import com.sprint.deokhugam.book.FakeOcrTextPort;
import com.sprint.deokhugam.book.FakeReviewCommand;
import com.sprint.deokhugam.book.FakeThumbnailStoragePort;
import com.sprint.deokhugam.book.application.BookCreator;
import com.sprint.deokhugam.book.application.BookInfoService;
import com.sprint.deokhugam.book.application.BookQueryService;
import com.sprint.deokhugam.book.application.BookRemoveService;
import com.sprint.deokhugam.book.application.BookUpdater;
import com.sprint.deokhugam.book.application.BookWriterApplicationService;
import com.sprint.deokhugam.book.application.IsbnRecognitionService;
import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.IsbnExtractor;
import com.sprint.deokhugam.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookControllerTest {

  private final FakeBookRepositoryPort bookRepositoryPort = new FakeBookRepositoryPort();
  private final FakeBookProviderPort bookProviderPort = new FakeBookProviderPort();
  private final FakeOcrTextPort ocrTextPort = new FakeOcrTextPort();
  private final FakeThumbnailStoragePort thumbnailStoragePort = new FakeThumbnailStoragePort();
  private final FakeReviewCommand reviewCommand = new FakeReviewCommand();

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new BookController(
                  // 이벤트는 받는 사람 없이 버린다. 파일 뒷정리는 ThumbnailCleanupListenerTest에서 본다
                  new BookWriterApplicationService(
                      thumbnailStoragePort,
                      new BookCreator(bookRepositoryPort),
                      new BookUpdater(bookRepositoryPort, event -> {})),
                  new BookRemoveService(bookRepositoryPort, event -> {}, reviewCommand),
                  new BookQueryService(
                      bookRepositoryPort, new FakeBookSearchPort(bookRepositoryPort)),
                  new BookInfoService(bookProviderPort),
                  new IsbnRecognitionService(ocrTextPort, new IsbnExtractor())))
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  private MockMultipartFile bookData(String json) {
    return new MockMultipartFile(
        "bookData", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
  }

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

  private Book savedBook(String title) {
    return bookRepositoryPort.save(
        Book.create(title, "저자", "소개", "출판사", LocalDate.of(2020, 1, 1), null, null));
  }

  // ===== (1) POST /api/books : create() =====
  // (1-1)
  @Test
  @DisplayName("도서를 등록하면 201과 도서 정보를 돌려준다")
  void create() throws Exception {
    MockMultipartFile thumbnail =
        new MockMultipartFile("thumbnailImage", "cover.png", "image/png", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/api/books").file(bookData(VALID_JSON)).file(thumbnail))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("클린 코드"))
        .andExpect(jsonPath("$.publishedDate").value("2013-12-24"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"))
        .andExpect(jsonPath("$.reviewCount").value(0))
        .andExpect(jsonPath("$.rating").value(0.0))
        .andExpect(
            jsonPath("$.thumbnailUrl")
                .value(
                    FakeThumbnailStoragePort.BASE_URL
                        + thumbnailStoragePort.uploadedKeys().get(0)));
  }

  // (1-5)
  @Test
  @DisplayName("썸네일 없이 등록하면 thumbnailUrl이 없다")
  void createWithoutThumbnail() throws Exception {
    mockMvc
        .perform(multipart("/api/books").file(bookData(VALID_JSON)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.thumbnailUrl").doesNotExist());
    assertThat(thumbnailStoragePort.uploadedKeys()).isEmpty();
  }

  // (1-6)
  @Test
  @DisplayName("썸네일이 jpg, png가 아니면 400 BOOK_INVALID_IMAGE")
  void createWithInvalidThumbnail() throws Exception {
    MockMultipartFile thumbnail =
        new MockMultipartFile("thumbnailImage", "cover.gif", "image/gif", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/api/books").file(bookData(VALID_JSON)).file(thumbnail))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BOOK_INVALID_IMAGE"));
  }

  // (1-2)
  @Test
  @DisplayName("필수 값이 비어 있으면 400과 필드별 메시지를 돌려준다")
  void createWithBlankTitle() throws Exception {
    String json = VALID_JSON.replace("\"클린 코드\"", "\"\"");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
        .andExpect(jsonPath("$.details.title").value("제목은 필수입니다."));
  }

  // (1-3)
  @Test
  @DisplayName("제목이 너무 길면 400을 돌려준다")
  void createWithTooLongTitle() throws Exception {
    String json = VALID_JSON.replace("클린 코드", "가".repeat(256));

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // (1-4)
  @Test
  @DisplayName("이미 등록된 ISBN이면 409를 돌려준다")
  void createWithDuplicateIsbn() throws Exception {
    mockMvc.perform(multipart("/api/books").file(bookData(VALID_JSON)));

    mockMvc
        .perform(multipart("/api/books").file(bookData(VALID_JSON)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BOOK_DUPLICATE_ISBN"));
  }

  // (1-7)
  @Test
  @DisplayName("하이픈이 섞인 ISBN은 숫자 13자리로 정리해 저장한다")
  void createWithHyphenIsbn() throws Exception {
    String json = VALID_JSON.replace("9788966260959", "978-89-6626-095-9");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isbn").value("9788966260959"));
  }

  // (1-8)
  @Test
  @DisplayName("형식이 틀린 ISBN이면 400 BOOK_INVALID_ISBN")
  void createWithInvalidIsbn() throws Exception {
    String json = VALID_JSON.replace("9788966260959", "9788966260950");

    mockMvc
        .perform(multipart("/api/books").file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BOOK_INVALID_ISBN"));
  }

  // ===== (2) GET /api/books/{bookId} : get() =====
  // (2-1)
  @Test
  @DisplayName("도서 id로 조회하면 200과 도서 정보를 돌려준다")
  void getBook() throws Exception {
    Book saved = savedBook();

    mockMvc
        .perform(get("/api/books/{bookId}", saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId().toString()))
        .andExpect(jsonPath("$.title").value("클린 코드"))
        .andExpect(jsonPath("$.author").value("로버트 C. 마틴"))
        .andExpect(jsonPath("$.publishedDate").value("2013-12-24"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"))
        .andExpect(jsonPath("$.reviewCount").value(0))
        .andExpect(jsonPath("$.rating").value(0.0));
  }

  // (2-2)
  @Test
  @DisplayName("없는 도서 id로 조회하면 404를 돌려준다")
  void getBookNotFound() throws Exception {
    mockMvc
        .perform(get("/api/books/{bookId}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (2-3)
  @Test
  @DisplayName("논리 삭제된 도서를 조회하면 404를 돌려준다")
  void getDeletedBook() throws Exception {
    Book saved = savedBook();
    saved.softDelete();

    mockMvc
        .perform(get("/api/books/{bookId}", saved.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (2-4)
  @Test
  @DisplayName("도서 id가 UUID 형식이 아니면 400을 돌려준다")
  void getBookWithInvalidId() throws Exception {
    mockMvc
        .perform(get("/api/books/{bookId}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // ===== (3) PATCH /api/books/{bookId} : update() =====
  // (3-1)
  @Test
  @DisplayName("PATCH + multipart로 도서를 수정하면 200과 바뀐 정보를 돌려준다")
  void update() throws Exception {
    Book saved = savedBook();
    MockMultipartFile thumbnail =
        new MockMultipartFile("thumbnailImage", "cover.png", "image/png", new byte[] {1, 2, 3});

    // multipart()는 기본이 POST라서 HttpMethod.PATCH를 넘긴다
    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId())
                .file(bookData(UPDATE_JSON))
                .file(thumbnail))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId().toString()))
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.author").value("새 저자"))
        .andExpect(jsonPath("$.description").value("새 소개"))
        .andExpect(jsonPath("$.publisher").value("새 출판사"))
        .andExpect(jsonPath("$.publishedDate").value("2021-02-02"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"))
        .andExpect(
            jsonPath("$.thumbnailUrl")
                .value(
                    FakeThumbnailStoragePort.BASE_URL
                        + thumbnailStoragePort.uploadedKeys().get(0)));
  }

  // (3-2)
  @Test
  @DisplayName("수정 요청에 isbn을 넣어도 무시되고 ISBN은 그대로다")
  void updateIgnoresIsbn() throws Exception {
    Book saved = savedBook();
    String json =
        UPDATE_JSON.replace(
            "\"publishedDate\"", "\"isbn\": \"9780000000000\",\n  \"publishedDate\"");

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId()).file(bookData(json)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"));
  }

  // (3-3)
  @Test
  @DisplayName("필수 값이 비어 있으면 400과 필드별 메시지를 돌려주고 값은 그대로다")
  void updateWithBlankTitle() throws Exception {
    Book saved = savedBook();
    String json = UPDATE_JSON.replace("\"새 제목\"", "\"\"");

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId()).file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
        .andExpect(jsonPath("$.details.title").value("제목은 필수입니다."));
    assertThat(saved.getTitle()).isEqualTo("클린 코드");
  }

  // (3-4)
  @Test
  @DisplayName("제목이 너무 길면 400을 돌려준다")
  void updateWithTooLongTitle() throws Exception {
    Book saved = savedBook();
    String json = UPDATE_JSON.replace("새 제목", "가".repeat(256));

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId()).file(bookData(json)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // (3-5)
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

  // (3-6)
  @Test
  @DisplayName("논리 삭제된 도서를 수정하면 404를 돌려준다")
  void updateDeletedBook() throws Exception {
    Book saved = savedBook();
    saved.softDelete();

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", saved.getId())
                .file(bookData(UPDATE_JSON)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (3-7)
  @Test
  @DisplayName("도서 id가 UUID 형식이 아니면 400을 돌려준다")
  void updateWithInvalidId() throws Exception {
    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/books/{bookId}", "not-a-uuid")
                .file(bookData(UPDATE_JSON)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // ===== (4) GET /api/books : search() =====
  // (4-1)
  @Test
  @DisplayName("파라미터 없이 조회하면 제목 내림차순으로 200과 커서 페이지를 돌려준다")
  void search() throws Exception {
    savedBook("A");
    savedBook("B");
    savedBook("C");

    mockMvc
        .perform(get("/api/books").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].title").value("C"))
        .andExpect(jsonPath("$.content[1].title").value("B"))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").value("B"))
        .andExpect(jsonPath("$.nextAfter").value("2026-01-01T00:00:01Z"));
  }

  // (4-2)
  @Test
  @DisplayName("cursor와 after를 넘기면 그 다음 도서부터 돌려준다")
  void searchNextPage() throws Exception {
    savedBook("A");
    savedBook("B");
    savedBook("C");

    mockMvc
        .perform(
            get("/api/books")
                .param("limit", "2")
                .param("cursor", "B")
                .param("after", "2026-01-01T00:00:01Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("A"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.nextAfter").doesNotExist());
  }

  // (4-3)
  @Test
  @DisplayName("keyword·orderBy·direction을 넘기면 조건에 맞게 돌려준다")
  void searchWithKeywordAndSort() throws Exception {
    savedBook("자바 기초");
    savedBook("파이썬 기초");
    savedBook("자바 심화");

    mockMvc
        .perform(
            get("/api/books")
                .param("keyword", "자바")
                .param("orderBy", "title")
                .param("direction", "ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].title").value("자바 기초"))
        .andExpect(jsonPath("$.content[1].title").value("자바 심화"))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  // (4-4)
  @Test
  @DisplayName("숫자 정렬이어도 nextCursor는 문자열이다")
  void searchRatingCursorIsString() throws Exception {
    savedBook("4점").addReview(4);
    savedBook("2점").addReview(2);

    mockMvc
        .perform(get("/api/books").param("orderBy", "rating").param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("4점"))
        .andExpect(jsonPath("$.nextCursor").value("4.0"));
  }

  // (4-5)
  @Test
  @DisplayName("명세에 없는 orderBy면 400을 돌려준다")
  void searchWithUnknownOrderBy() throws Exception {
    mockMvc
        .perform(get("/api/books").param("orderBy", "author"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // (4-6)
  @Test
  @DisplayName("ASC·DESC가 아닌 direction이면 400을 돌려준다")
  void searchWithUnknownDirection() throws Exception {
    mockMvc
        .perform(get("/api/books").param("direction", "UP"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // (4-7)
  @Test
  @DisplayName("limit이 1~100을 벗어나면 400을 돌려준다")
  void searchWithInvalidLimit() throws Exception {
    mockMvc
        .perform(get("/api/books").param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    mockMvc
        .perform(get("/api/books").param("limit", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // (4-8)
  @Test
  @DisplayName("cursor만 있고 after가 없으면 400을 돌려준다")
  void searchWithCursorWithoutAfter() throws Exception {
    mockMvc
        .perform(get("/api/books").param("cursor", "B"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // (4-9)
  @Test
  @DisplayName("정렬 기준 타입에 맞지 않는 cursor면 400을 돌려준다")
  void searchWithMalformedCursor() throws Exception {
    mockMvc
        .perform(
            get("/api/books")
                .param("orderBy", "rating")
                .param("cursor", "높음")
                .param("after", "2026-01-01T00:00:01Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
  }

  // ===== (5) GET /api/books/info : getInfo() =====
  // (5-1)
  @Test
  @DisplayName("ISBN으로 찾은 도서 정보를 200으로 돌려준다. 썸네일은 Base64 문자열")
  void getInfo() throws Exception {
    bookProviderPort.put(
        new BookInfoDto(
            "클린 코드",
            "로버트 C. 마틴",
            "소개",
            "인사이트",
            LocalDate.of(2013, 12, 24),
            "9788966260959",
            new byte[] {1, 2, 3}));

    mockMvc
        .perform(get("/api/books/info").param("isbn", "9788966260959"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("클린 코드"))
        .andExpect(jsonPath("$.author").value("로버트 C. 마틴"))
        .andExpect(jsonPath("$.publishedDate").value("2013-12-24"))
        .andExpect(jsonPath("$.isbn").value("9788966260959"))
        .andExpect(jsonPath("$.thumbnailImage").value("AQID"));
  }

  // (5-2)
  @Test
  @DisplayName("ISBN에 해당하는 도서가 없으면 404를 돌려준다")
  void getInfoNotFound() throws Exception {
    mockMvc
        .perform(get("/api/books/info").param("isbn", "9788966260959"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_INFO_NOT_FOUND"));
  }

  // (5-3)
  @Test
  @DisplayName("형식이 틀린 ISBN으로 조회하면 400 BOOK_INVALID_ISBN")
  void getInfoInvalidIsbn() throws Exception {
    mockMvc
        .perform(get("/api/books/info").param("isbn", "978-89-6626-095"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BOOK_INVALID_ISBN"));
  }

  // (5-4)
  @Test
  @DisplayName("/info 는 /{bookId} 가 아니라 ISBN 조회로 매칭된다")
  void getInfoIsNotTreatedAsBookId() throws Exception {
    mockMvc.perform(get("/api/books/info")).andExpect(status().isBadRequest());
  }

  // ===== (6) POST /api/books/isbn/ocr : recognizeIsbn() =====
  private MockMultipartFile imagePart(String filename, String contentType) {
    return new MockMultipartFile("image", filename, contentType, new byte[] {1, 2, 3});
  }

  // (6-1)
  @Test
  @DisplayName("이미지에서 읽은 ISBN을 200에 문자열 그대로 돌려준다")
  void recognizeIsbn() throws Exception {
    ocrTextPort.setText("ISBN 978-89-6626-095-9");

    mockMvc
        .perform(multipart("/api/books/isbn/ocr").file(imagePart("cover.jpg", "image/jpeg")))
        .andExpect(status().isOk())
        .andExpect(content().string("9788966260959"));
  }

  // (6-2)
  @Test
  @DisplayName("ISBN을 못 읽으면 400 BOOK_OCR_FAILED")
  void recognizeIsbnFailed() throws Exception {
    ocrTextPort.setText("");

    mockMvc
        .perform(multipart("/api/books/isbn/ocr").file(imagePart("cover.jpg", "image/jpeg")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BOOK_OCR_FAILED"));
  }

  // (6-3)
  @Test
  @DisplayName("jpg, png가 아니면 400 BOOK_INVALID_IMAGE")
  void recognizeIsbnInvalidImage() throws Exception {
    mockMvc
        .perform(multipart("/api/books/isbn/ocr").file(imagePart("cover.gif", "image/gif")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BOOK_INVALID_IMAGE"));
  }

  // (6-4)
  @Test
  @DisplayName("image 파트가 없으면 400")
  void recognizeIsbnMissingPart() throws Exception {
    mockMvc.perform(multipart("/api/books/isbn/ocr")).andExpect(status().isBadRequest());
  }

  // ===== (6) DELETE /api/books/{bookId} : BookController.delete() =====
  // (6-1)
  @Test
  @DisplayName("도서를 논리 삭제하면 204를 돌려주고 다시 조회하면 404다")
  void deleteBook() throws Exception {
    Book saved = savedBook();

    mockMvc.perform(delete("/api/books/{bookId}", saved.getId())).andExpect(status().isNoContent());

    assertThat(saved.isDeleted()).isTrue();
    mockMvc.perform(get("/api/books/{bookId}", saved.getId())).andExpect(status().isNotFound());
  }

  // (6-2)
  @Test
  @DisplayName("없는 도서를 논리 삭제하면 404를 돌려준다")
  void deleteNotFound() throws Exception {
    mockMvc
        .perform(delete("/api/books/{bookId}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }

  // (6-3)
  @Test
  @DisplayName("도서 id가 UUID 형식이 아니면 400을 돌려준다")
  void deleteWithInvalidId() throws Exception {
    mockMvc.perform(delete("/api/books/{bookId}", "not-a-uuid")).andExpect(status().isBadRequest());
  }

  // ===== (7) DELETE /api/books/{bookId}/hard : BookController.hardDelete() =====
  // (7-1)
  @Test
  @DisplayName("도서를 물리 삭제하면 204를 돌려주고 리뷰 정리 명령이 불린다")
  void hardDeleteBook() throws Exception {
    Book saved = savedBook();

    mockMvc
        .perform(delete("/api/books/{bookId}/hard", saved.getId()))
        .andExpect(status().isNoContent());

    assertThat(reviewCommand.cleanedBooks()).containsExactly(saved);
    assertThat(bookRepositoryPort.count()).isZero();
  }

  // (7-2)
  @Test
  @DisplayName("없는 도서를 물리 삭제하면 404를 돌려준다")
  void hardDeleteNotFound() throws Exception {
    mockMvc
        .perform(delete("/api/books/{bookId}/hard", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
  }
}
