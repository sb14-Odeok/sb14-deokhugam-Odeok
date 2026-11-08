package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookUpdateRequest;
import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.ThumbnailChangedEvent;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.book.exception.BookErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BookUpdater {

  private final BookRepositoryPort bookRepositoryPort;
  private final ApplicationEventPublisher eventPublisher;

  // newThumbnailUrl이 null이면 썸네일은 그대로 둔다
  public BookDto update(UUID bookId, BookUpdateRequest request, String newThumbnailUrl) {
    Book book =
        bookRepositoryPort
            .findActiveById(bookId)
            .orElseThrow(() -> new BusinessException(BookErrorCode.NOT_FOUND, "bookId=" + bookId));

    // 트랜잭션이 끝날 때 JPA가 바뀐 값을 알아서 UPDATE 한다 (변경 감지). save()를 따로 부르지 않는다
    book.update(
        request.title(),
        request.author(),
        request.description(),
        request.publisher(),
        request.publishedDate());
    if (newThumbnailUrl != null) {
      String previousUrl = book.getThumbnailUrl();
      book.changeThumbnailUrl(newThumbnailUrl);
      if (previousUrl != null) {
        // 트랜잭션 안에서 발행해야 커밋 뒤 리스너가 돈다. 롤백되면 버려져 이전 파일이 남는다
        eventPublisher.publishEvent(new ThumbnailChangedEvent(ThumbnailKeys.keyOf(previousUrl)));
      }
    }
    return BookDto.from(book);
  }
}
