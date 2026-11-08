package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.FakeBookRepositoryPort;
import com.sprint.deokhugam.book.FakeReviewCommand;
import com.sprint.deokhugam.book.FakeThumbnailStoragePort;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.BookDeletedEvent;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.dashboard.book.domain.BookDeletingEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookRemoveServiceTest {

  private final FakeBookRepositoryPort bookRepositoryPort = new FakeBookRepositoryPort();
  // 스프링 대신 발행된 이벤트를 리스트에 모은다. 파일 뒷정리는 ThumbnailCleanupListenerTest에서 본다
  private final List<Object> events = new ArrayList<>();
  private final FakeReviewCommand reviewCommand = new FakeReviewCommand();
  private final BookRemoveService service =
      new BookRemoveService(bookRepositoryPort, events::add, reviewCommand);

  private static final String OLD_URL = FakeThumbnailStoragePort.BASE_URL + "books/old.png";

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

  // ===== (1) delete() =====
  // (1-1)
  @Test
  @DisplayName("논리 삭제하면 deletedAt이 찍히고 활성 조회에서 빠지지만 저장소에는 남는다")
  void delete() {
    Book saved = savedBook(OLD_URL);

    service.delete(saved.getId());

    assertThat(saved.isDeleted()).isTrue();
    assertThat(bookRepositoryPort.findActiveById(saved.getId())).isEmpty();
    assertThat(bookRepositoryPort.findById(saved.getId())).contains(saved);
    assertThat(events).containsExactly(BookDeletingEvent.of(saved.getId()));
    assertThat(reviewCommand.cleanedBooks()).isEmpty();
  }

  // (1-2)
  @Test
  @DisplayName("없는 도서를 논리 삭제하면 NOT_FOUND 예외가 발생한다")
  void deleteNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // (1-3)
  @Test
  @DisplayName("이미 논리 삭제된 도서를 다시 논리 삭제하면 NOT_FOUND 예외가 발생한다")
  void deleteAlreadyDeleted() {
    Book saved = savedBook();
    saved.softDelete();

    assertThatThrownBy(() -> service.delete(saved.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
  }

  // ===== (2) hardDelete() =====
  // (2-1)
  @Test
  @DisplayName("물리 삭제하면 리뷰 정리 명령을 그 도서로 부르고 행을 지우고 썸네일 키 이벤트를 낸다")
  void hardDelete() {
    Book saved = savedBook(OLD_URL);

    service.hardDelete(saved.getId());

    assertThat(reviewCommand.cleanedBooks()).containsExactly(saved);
    assertThat(bookRepositoryPort.findById(saved.getId())).isEmpty();
    assertThat(events)
        .containsExactly(
            BookDeletingEvent.of(saved.getId()), new BookDeletedEvent("books/old.png"));
  }

  // (2-2)
  @Test
  @DisplayName("논리 삭제된 도서도 물리 삭제할 수 있다")
  void hardDeleteSoftDeletedBook() {
    Book saved = savedBook();
    saved.softDelete();

    service.hardDelete(saved.getId());

    assertThat(bookRepositoryPort.findById(saved.getId())).isEmpty();
  }

  // (2-3)
  @Test
  @DisplayName("썸네일이 없던 도서를 물리 삭제하면 키가 null인 이벤트를 낸다")
  void hardDeleteWithoutThumbnail() {
    Book saved = savedBook();

    service.hardDelete(saved.getId());

    assertThat(events)
        .containsExactly(BookDeletingEvent.of(saved.getId()), new BookDeletedEvent(null));
  }

  // (2-4)
  @Test
  @DisplayName("없는 도서를 물리 삭제하면 NOT_FOUND 예외가 발생하고 리뷰 정리도 부르지 않는다")
  void hardDeleteNotFound() {
    assertThatThrownBy(() -> service.hardDelete(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.NOT_FOUND);
    assertThat(reviewCommand.cleanedBooks()).isEmpty();
  }
}
