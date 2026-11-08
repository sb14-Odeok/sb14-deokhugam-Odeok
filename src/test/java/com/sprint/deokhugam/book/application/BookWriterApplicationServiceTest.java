package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.FakeBookRepositoryPort;
import com.sprint.deokhugam.book.FakeThumbnailStoragePort;
import com.sprint.deokhugam.book.application.dto.BookCreateRequest;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookUpdateRequest;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.ThumbnailChangedEvent;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.book.exception.BookErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BookWriterApplicationServiceTest {

  private final FakeBookRepositoryPort bookRepositoryPort = new FakeBookRepositoryPort();
  private final FakeThumbnailStoragePort thumbnailStoragePort = new FakeThumbnailStoragePort();
  // 스프링 대신 발행된 이벤트를 리스트에 모은다. 파일 뒷정리는 ThumbnailCleanupListenerTest에서 본다
  private final List<Object> events = new ArrayList<>();
  private final BookWriterApplicationService service =
      new BookWriterApplicationService(
          thumbnailStoragePort,
          new BookCreator(bookRepositoryPort),
          new BookUpdater(bookRepositoryPort, events::add));

  private static final String OLD_URL = FakeThumbnailStoragePort.BASE_URL + "books/old.png";

  private BookCreateRequest request(String isbn) {
    return new BookCreateRequest(
        "클린 코드", "로버트 C. 마틴", "소개", "인사이트", LocalDate.of(2013, 12, 24), isbn);
  }

  private BookUpdateRequest updateRequest() {
    return new BookUpdateRequest("새 제목", "새 저자", "새 소개", "새 출판사", LocalDate.of(2021, 2, 2));
  }

  private Book savedBook() {
    return savedBook(null);
  }

  private Book savedBook(String thumbnailUrl) {
    return bookRepositoryPort.save(
        Book.create(
            "클린 코드",
            "로버트 C. 마틴",
            "소개",
            "인사이트",
            LocalDate.of(2013, 12, 24),
            Isbn.of("9788966260959"),
            thumbnailUrl));
  }

  private MockMultipartFile image(String filename, String contentType, byte[] bytes) {
    return new MockMultipartFile("thumbnailImage", filename, contentType, bytes);
  }

  private MockMultipartFile png() {
    return image("cover.PNG", "image/png", new byte[] {1, 2, 3});
  }

  // ===== (1) create() =====
  // (1-1)
  @Test
  @DisplayName("도서를 등록하면 저장되고 리뷰 수와 평점이 0인 응답을 돌려준다")
  void create() {
    BookDto bookDto = service.create(request("9788966260959"), null);

    assertThat(bookRepositoryPort.count()).isEqualTo(1);
    assertThat(bookDto.title()).isEqualTo("클린 코드");
    assertThat(bookDto.isbn()).isEqualTo("9788966260959");
    assertThat(bookDto.thumbnailUrl()).isNull();
    assertThat(bookDto.reviewCount()).isZero();
    assertThat(bookDto.rating()).isZero();
  }

  // (1-2)
  @Test
  @DisplayName("이미 등록된 ISBN이면 DUPLICATE_ISBN 예외가 발생하고 저장하지 않는다")
  void createWithDuplicateIsbn() {
    service.create(request("9788966260959"), null);

    assertThatThrownBy(() -> service.create(request("9788966260959"), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.DUPLICATE_ISBN);
    assertThat(bookRepositoryPort.count()).isEqualTo(1);
  }

  // (1-2b)
  @Test
  @DisplayName("사전 확인을 통과했는데 저장에서 UNIQUE 위반이 나면 DUPLICATE_ISBN 예외로 바꾼다")
  void createWithUniqueViolationOnSave() {
    bookRepositoryPort.failNextSaveWithUniqueViolation();

    assertThatThrownBy(() -> service.create(request("9788966260959"), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.DUPLICATE_ISBN);
  }

  // (1-3)
  @Test
  @DisplayName("ISBN이 없는 도서는 여러 권 등록할 수 있다")
  void createWithoutIsbn() {
    service.create(request(null), null);
    service.create(request(null), null);

    assertThat(bookRepositoryPort.count()).isEqualTo(2);
  }

  // (1-4)
  @Test
  @DisplayName("썸네일과 함께 등록하면 books/{uuid}.{확장자} 키로 올리고 그 URL을 저장한다")
  void createWithThumbnail() {
    BookDto bookDto = service.create(request("9788966260959"), png());

    assertThat(thumbnailStoragePort.uploadedKeys()).hasSize(1);
    String key = thumbnailStoragePort.uploadedKeys().get(0);
    assertThat(key).startsWith("books/").endsWith(".png");
    assertThat(UUID.fromString(key.substring("books/".length(), key.length() - ".png".length())))
        .isNotNull();
    assertThat(bookDto.thumbnailUrl()).isEqualTo(FakeThumbnailStoragePort.BASE_URL + key);
  }

  // (1-5)
  @Test
  @DisplayName("빈 파일이면 INVALID_IMAGE 예외가 발생하고 올리지도 저장하지도 않는다")
  void createWithEmptyThumbnail() {
    assertThatThrownBy(
            () -> service.create(request(null), image("cover.png", "image/png", new byte[0])))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThat(thumbnailStoragePort.uploadedKeys()).isEmpty();
    assertThat(bookRepositoryPort.count()).isZero();
  }

  // (1-6)
  @Test
  @DisplayName("5MB를 넘으면 INVALID_IMAGE 예외가 발생한다")
  void createWithTooLargeThumbnail() {
    byte[] tooLarge = new byte[5_000_001];

    assertThatThrownBy(
            () -> service.create(request(null), image("cover.png", "image/png", tooLarge)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThat(thumbnailStoragePort.uploadedKeys()).isEmpty();
  }

  // (1-7)
  @Test
  @DisplayName("Content-Type이나 확장자가 jpg, png가 아니면 INVALID_IMAGE 예외가 발생한다")
  void createWithUnsupportedThumbnail() {
    assertThatThrownBy(
            () -> service.create(request(null), image("cover.gif", "image/gif", new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThatThrownBy(
            () -> service.create(request(null), image("cover.gif", "image/png", new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThat(thumbnailStoragePort.uploadedKeys()).isEmpty();
  }

  // (1-8)
  @Test
  @DisplayName("S3에 올리지 못하면 THUMBNAIL_UPLOAD_FAILED 예외가 발생하고 도서는 저장하지 않는다")
  void createWhenUploadFails() {
    thumbnailStoragePort.setFailing(true);

    assertThatThrownBy(() -> service.create(request(null), png()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.THUMBNAIL_UPLOAD_FAILED);
    assertThat(bookRepositoryPort.count()).isZero();
  }

  // (1-9)
  @Test
  @DisplayName("썸네일과 함께 등록에 성공하면 이벤트를 발행하지 않고 올린 파일도 지우지 않는다")
  void createWithThumbnailKeepsUploaded() {
    service.create(request(null), png());

    assertThat(events).isEmpty();
    assertThat(thumbnailStoragePort.deletedKeys()).isEmpty();
  }

  // (1-10)
  @Test
  @DisplayName("썸네일 없이 등록하면 이벤트를 발행하지 않는다")
  void createWithoutThumbnailPublishesNothing() {
    service.create(request(null), null);

    assertThat(events).isEmpty();
  }

  // (1-11)
  @Test
  @DisplayName("하이픈이 섞인 ISBN은 숫자 13자리로 정리해 저장한다")
  void createWithHyphenIsbn() {
    BookDto bookDto = service.create(request("978-89-6626-095-9"), null);

    assertThat(bookDto.isbn()).isEqualTo("9788966260959");
  }

  // (1-12)
  @Test
  @DisplayName("10자리 ISBN은 13자리로 바꿔 저장하므로 같은 책의 13자리와 중복이 걸린다")
  void createWithIsbn10ThenIsbn13() {
    service.create(request("8966260950"), null);

    assertThatThrownBy(() -> service.create(request("9788966260959"), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.DUPLICATE_ISBN);
    assertThat(bookRepositoryPort.count()).isEqualTo(1);
  }

  // (1-13)
  @Test
  @DisplayName("빈 문자열 ISBN은 없는 것으로 보고 여러 권 등록할 수 있다")
  void createWithBlankIsbn() {
    BookDto first = service.create(request(""), null);
    service.create(request("  "), null);

    assertThat(first.isbn()).isNull();
    assertThat(bookRepositoryPort.count()).isEqualTo(2);
  }

  // (1-14)
  @Test
  @DisplayName("형식이 틀린 ISBN이면 INVALID_ISBN 예외가 발생하고 저장하지 않는다")
  void createWithInvalidIsbn() {
    assertThatThrownBy(() -> service.create(request("9788966260950"), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_ISBN);
    assertThat(bookRepositoryPort.count()).isZero();
  }

  // ===== (2) update() =====
  // (2-1)
  @Test
  @DisplayName("도서를 수정하면 기본 정보만 바뀌고 ISBN은 그대로다")
  void update() {
    Book saved = savedBook();

    BookDto bookDto = service.update(saved.getId(), updateRequest(), null);

    assertThat(bookDto.id()).isEqualTo(saved.getId());
    assertThat(bookDto.title()).isEqualTo("새 제목");
    assertThat(bookDto.author()).isEqualTo("새 저자");
    assertThat(bookDto.description()).isEqualTo("새 소개");
    assertThat(bookDto.publisher()).isEqualTo("새 출판사");
    assertThat(bookDto.publishedDate()).isEqualTo(LocalDate.of(2021, 2, 2));
    assertThat(bookDto.isbn()).isEqualTo("9788966260959");
    assertThat(bookRepositoryPort.count()).isEqualTo(1);
  }

  // (2-2)
  @Test
  @DisplayName("없는 도서를 수정하면 NOT_FOUND 예외가 발생한다")
  void updateNotFound() {
    assertThatThrownBy(() -> service.update(UUID.randomUUID(), updateRequest(), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // (2-3)
  @Test
  @DisplayName("논리 삭제된 도서를 수정하면 NOT_FOUND 예외가 발생하고 값은 그대로다")
  void updateDeleted() {
    Book saved = savedBook();
    saved.softDelete();

    assertThatThrownBy(() -> service.update(saved.getId(), updateRequest(), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
    assertThat(saved.getTitle()).isEqualTo("클린 코드");
  }

  // (2-4)
  @Test
  @DisplayName("썸네일 파트 없이 수정하면 기존 썸네일은 그대로다")
  void updateWithoutThumbnail() {
    Book saved = savedBook(OLD_URL);

    BookDto bookDto = service.update(saved.getId(), updateRequest(), null);

    assertThat(bookDto.thumbnailUrl()).isEqualTo(OLD_URL);
    assertThat(thumbnailStoragePort.uploadedKeys()).isEmpty();
    assertThat(events).isEmpty();
  }

  // (2-5)
  @Test
  @DisplayName("썸네일과 함께 수정하면 새 URL로 바뀌고 새 키와 이전 키를 담은 이벤트를 발행한다")
  void updateWithThumbnail() {
    Book saved = savedBook(OLD_URL);

    BookDto bookDto = service.update(saved.getId(), updateRequest(), png());

    String newKey = thumbnailStoragePort.uploadedKeys().get(0);
    assertThat(bookDto.thumbnailUrl()).isEqualTo(FakeThumbnailStoragePort.BASE_URL + newKey);
    assertThat(events).containsExactly(new ThumbnailChangedEvent("books/old.png"));
    assertThat(thumbnailStoragePort.deletedKeys()).isEmpty();
  }

  // (2-6)
  @Test
  @DisplayName("썸네일이 없던 도서에 썸네일을 넣으면 지울 이전 파일이 없어 이벤트를 발행하지 않는다")
  void updateAddsThumbnailToBookWithoutOne() {
    Book saved = savedBook();

    service.update(saved.getId(), updateRequest(), png());

    assertThat(events).isEmpty();
  }

  // (2-8)
  @Test
  @DisplayName("수정 때 썸네일이 잘못되면 INVALID_IMAGE 예외가 발생하고 썸네일은 그대로다")
  void updateWithInvalidThumbnail() {
    Book saved = savedBook(OLD_URL);

    assertThatThrownBy(
            () ->
                service.update(
                    saved.getId(),
                    updateRequest(),
                    image("cover.gif", "image/gif", new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThat(saved.getThumbnailUrl()).isEqualTo(OLD_URL);
    assertThat(thumbnailStoragePort.uploadedKeys()).isEmpty();
  }

  // ===== (3) 보상 삭제 =====
  // (3-1)
  @Test
  @DisplayName("이미 등록된 ISBN이면 409이고, 먼저 올린 썸네일을 지운다")
  void createDuplicateIsbnDeletesUploaded() {
    service.create(request("9788966260959"), null);

    assertThatThrownBy(() -> service.create(request("9788966260959"), png()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.DUPLICATE_ISBN);
    assertThat(thumbnailStoragePort.uploadedKeys()).hasSize(1);
    assertThat(thumbnailStoragePort.deletedKeys()).isEqualTo(thumbnailStoragePort.uploadedKeys());
  }

  // (3-2)
  @Test
  @DisplayName("저장에서 UNIQUE 위반이 나도 먼저 올린 썸네일을 지운다")
  void createUniqueViolationDeletesUploaded() {
    bookRepositoryPort.failNextSaveWithUniqueViolation();

    assertThatThrownBy(() -> service.create(request("9788966260959"), png()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.DUPLICATE_ISBN);
    assertThat(thumbnailStoragePort.deletedKeys()).isEqualTo(thumbnailStoragePort.uploadedKeys());
  }

  // (3-3)
  @Test
  @DisplayName("없는 도서를 썸네일과 함께 수정하면 404이고, 먼저 올린 썸네일을 지운다")
  void updateNotFoundDeletesUploaded() {
    assertThatThrownBy(() -> service.update(UUID.randomUUID(), updateRequest(), png()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
    assertThat(thumbnailStoragePort.uploadedKeys()).hasSize(1);
    assertThat(thumbnailStoragePort.deletedKeys()).isEqualTo(thumbnailStoragePort.uploadedKeys());
  }

  // (3-4)
  @Test
  @DisplayName("썸네일 없이 실패하면 지울 파일이 없다")
  void failWithoutThumbnailDeletesNothing() {
    assertThatThrownBy(() -> service.update(UUID.randomUUID(), updateRequest(), null))
        .isInstanceOf(BusinessException.class);
    assertThat(thumbnailStoragePort.deletedKeys()).isEmpty();
  }
}
