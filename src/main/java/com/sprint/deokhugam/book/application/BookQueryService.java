package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookSearchCondition;
import com.sprint.deokhugam.book.application.provided.query.BookFinder;
import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.application.required.BookSearchPort;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// DB 조회만 둔다. 외부 조회는 BookInfoService, IsbnRecognitionService가 트랜잭션 없이 한다
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookQueryService implements BookFinder {

  private final BookRepositoryPort bookRepositoryPort;
  private final BookSearchPort bookSearchPort;

  @Override
  public Book findById(UUID bookId) {
    return bookRepositoryPort
        .findActiveById(bookId)
        .orElseThrow(() -> new BusinessException(BookErrorCode.NOT_FOUND, "bookId=" + bookId));
  }

  @Override
  public BookDto getById(UUID bookId) {
    return BookDto.from(findById(bookId));
  }

  @Override
  public CursorPageResponse<BookDto> search(BookSearchCondition condition, int limit) {
    // 다음 페이지가 있는지 알기 위해 limit보다 1개 더 가져온다. 넘치는 1개는 응답에서 잘린다
    List<BookDto> rows =
        bookSearchPort.search(condition, limit + 1).stream().map(BookDto::from).toList();
    long totalElements = bookSearchPort.countByKeyword(condition.keyword());

    // nextCursor = 마지막 도서의 정렬 기준 값, nextAfter = 마지막 도서의 createdAt
    return CursorPageResponse.of(
        rows, limit, condition.sortField()::cursorOf, BookDto::createdAt, totalElements);
  }
}
