package com.sprint.deokhugam.book.application.required;

import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import java.util.Optional;
import java.util.UUID;

// 도서 저장·단건 조회·중복 확인 (기본 CRUD). 조건 검색은 BookSearchPort
public interface BookRepositoryPort {

  // 바로 INSERT 한다. UNIQUE 제약에 걸리면 UniqueConstraintViolationException
  Book save(Book book);

  // 논리 삭제된 도서는 빼고 찾는다
  Optional<Book> findActiveById(UUID bookId);

  // 논리 삭제된 도서도 찾는다 (물리 삭제용)
  Optional<Book> findById(UUID bookId);

  boolean existsByIsbn(Isbn isbn);

  void delete(Book book);
}
