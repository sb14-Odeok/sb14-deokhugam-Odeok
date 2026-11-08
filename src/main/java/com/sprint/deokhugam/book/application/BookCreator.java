package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.dto.BookCreateRequest;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.application.required.UniqueConstraintViolationException;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BookCreator {

  private final BookRepositoryPort bookRepositoryPort;

  public BookDto create(BookCreateRequest request, String thumbnailUrl) {
    Isbn isbn = Isbn.of(request.isbn());
    if (isbn != null && bookRepositoryPort.existsByIsbn(isbn)) {
      throw new BusinessException(BookErrorCode.DUPLICATE_ISBN, "isbn=" + isbn);
    }
    Book book =
        Book.create(
            request.title(),
            request.author(),
            request.description(),
            request.publisher(),
            request.publishedDate(),
            isbn,
            thumbnailUrl);
    try {
      return BookDto.from(bookRepositoryPort.save(book));
    } catch (UniqueConstraintViolationException e) {
      // 사전 확인을 함께 통과한 같은 ISBN 동시 요청이다. 데이터는 UNIQUE가 막았고 응답만 409로 맞춘다
      throw new BusinessException(BookErrorCode.DUPLICATE_ISBN, "isbn=" + isbn, e);
    }
  }
}
