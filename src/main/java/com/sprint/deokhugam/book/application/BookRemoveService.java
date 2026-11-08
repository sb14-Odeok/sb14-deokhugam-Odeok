package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.provided.command.BookRemover;
import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.BookDeletedEvent;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.dashboard.book.domain.BookDeletingEvent;
import com.sprint.deokhugam.review.application.ReviewCleanupUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BookRemoveService implements BookRemover {

  private final BookRepositoryPort bookRepositoryPort;
  private final ApplicationEventPublisher eventPublisher;
  private final ReviewCleanupUseCase reviewCleanupUseCase;

  @Override
  public void delete(UUID bookId) {
    Book book =
        bookRepositoryPort
            .findActiveById(bookId)
            .orElseThrow(() -> new BusinessException(BookErrorCode.NOT_FOUND, "bookId=" + bookId));
    eventPublisher.publishEvent(BookDeletingEvent.of(bookId));
    // deletedAt만 찍는다. 변경 감지로 UPDATE 되고, 리뷰와 파일은 그대로 둔다
    book.softDelete();
  }

  @Override
  public void hardDelete(UUID bookId) {
    Book book =
        bookRepositoryPort
            .findById(bookId)
            .orElseThrow(() -> new BusinessException(BookErrorCode.NOT_FOUND, "bookId=" + bookId));

    // 리뷰 아래 데이터(좋아요, 댓글)는 리뷰 쪽이 책임진다. 리뷰가 없어도 그대로 부른다
    reviewCleanupUseCase.deleteReviewsWithRelatedDataByBook(book);

    eventPublisher.publishEvent(BookDeletingEvent.of(bookId));
    // 행이 사라지기 전에 썸네일 키를 뽑아 둔다
    String thumbnailKey =
        book.getThumbnailUrl() == null ? null : ThumbnailKeys.keyOf(book.getThumbnailUrl());
    bookRepositoryPort.delete(book);
    // 파일은 커밋 뒤에 ThumbnailCleanupListener가 지운다. 키가 null이면 리스너가 건너뛴다
    eventPublisher.publishEvent(new BookDeletedEvent(thumbnailKey));
  }
}
