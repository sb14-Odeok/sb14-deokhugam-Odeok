package com.sprint.deokhugam.book.adapter.persistence;

import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.application.required.UniqueConstraintViolationException;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BookRepositoryAdapter implements BookRepositoryPort {

  // PostgreSQL의 UNIQUE 위반 코드. books의 UNIQUE는 isbn 하나라 이것만 보면 된다
  private static final String UNIQUE_VIOLATION = "23505";

  private final BookJpaRepository bookJpaRepository;

  @Override
  public Book save(Book book) {
    try {
      // 커밋까지 미루지 않고 INSERT를 보내 위반이 서비스 안에서 나게 한다
      return bookJpaRepository.saveAndFlush(book);
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) {
        throw new UniqueConstraintViolationException(e);
      }
      throw e;
    }
  }

  // 제약 이름은 ddl-auto가 자동으로 붙여서 SQLState로 본다
  private boolean isUniqueViolation(DataIntegrityViolationException e) {
    return e.getMostSpecificCause() instanceof SQLException sql
        && UNIQUE_VIOLATION.equals(sql.getSQLState());
  }

  @Override
  public Optional<Book> findActiveById(UUID bookId) {
    return bookJpaRepository.findByIdAndDeletedAtIsNull(bookId);
  }

  @Override
  public Optional<Book> findById(UUID bookId) {
    return bookJpaRepository.findById(bookId);
  }

  @Override
  public boolean existsByIsbn(Isbn isbn) {
    return bookJpaRepository.existsByIsbn(isbn);
  }

  @Override
  public void delete(Book book) {
    bookJpaRepository.delete(book);
  }
}
