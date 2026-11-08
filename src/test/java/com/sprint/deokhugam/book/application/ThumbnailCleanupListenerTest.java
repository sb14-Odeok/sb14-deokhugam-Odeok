package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.deokhugam.book.FakeThumbnailStoragePort;
import com.sprint.deokhugam.book.domain.BookDeletedEvent;
import com.sprint.deokhugam.book.domain.ThumbnailChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThumbnailCleanupListenerTest {

  private final FakeThumbnailStoragePort thumbnailStoragePort = new FakeThumbnailStoragePort();
  private final ThumbnailCleanupListener listener =
      new ThumbnailCleanupListener(thumbnailStoragePort);

  // ===== (1) deletePreviousOnCommit() =====
  // (1-1)
  @Test
  @DisplayName("커밋 뒤에는 이전 파일만 지운다")
  void commitDeletesPrevious() {
    listener.deletePreviousOnCommit(new ThumbnailChangedEvent("books/old.png"));

    assertThat(thumbnailStoragePort.deletedKeys()).containsExactly("books/old.png");
  }

  // ===== (2) deleteThumbnailOnBookDeleted() =====
  // (2-1)
  @Test
  @DisplayName("도서가 지워진 뒤에는 그 썸네일 파일을 지운다")
  void bookDeletedDeletesThumbnail() {
    listener.deleteThumbnailOnBookDeleted(new BookDeletedEvent("books/old.png"));

    assertThat(thumbnailStoragePort.deletedKeys()).containsExactly("books/old.png");
  }

  // (2-2)
  @Test
  @DisplayName("썸네일이 없던 도서면 지울 것이 없다")
  void bookDeletedWithoutThumbnail() {
    listener.deleteThumbnailOnBookDeleted(new BookDeletedEvent(null));

    assertThat(thumbnailStoragePort.deletedKeys()).isEmpty();
  }
}
