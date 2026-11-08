package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.required.ThumbnailStoragePort;
import com.sprint.deokhugam.book.domain.BookDeletedEvent;
import com.sprint.deokhugam.book.domain.ThumbnailChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// S3 파일은 롤백이 안 되니 DB 트랜잭션 결과를 본 뒤에 지운다. 삭제 실패는 포트가 로그만 남긴다
@Component
@RequiredArgsConstructor
public class ThumbnailCleanupListener {

  private final ThumbnailStoragePort thumbnailStoragePort;

  // 롤백되면 이 이벤트는 버려져 이전 파일이 남는다. 새 파일은 BookWriterApplicationService가 지운다
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void deletePreviousOnCommit(ThumbnailChangedEvent event) {
    thumbnailStoragePort.delete(event.previousKey());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void deleteThumbnailOnBookDeleted(BookDeletedEvent event) {
    if (event.thumbnailKey() != null) {
      thumbnailStoragePort.delete(event.thumbnailKey());
    }
  }
}
