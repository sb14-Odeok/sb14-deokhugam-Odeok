package com.sprint.deokhugam.book;

import com.sprint.deokhugam.book.application.required.BookRepositoryPort;
import com.sprint.deokhugam.book.application.required.UniqueConstraintViolationException;
import com.sprint.deokhugam.book.domain.Book;
import com.sprint.deokhugam.book.domain.Isbn;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

// DB 대신 리스트에 도서를 담아 두는 가짜 저장소. 검색은 FakeBookSearchPort가 이 리스트를 읽는다
public class FakeBookRepositoryPort implements BookRepositoryPort {

  // 저장 순서대로 1초씩 늦게 등록된 것으로 본다 → createdAt 정렬을 예측할 수 있다
  private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

  private final List<Book> books = new ArrayList<>();
  private boolean failNextSave;

  // 사전 확인은 통과시키고 저장에서만 UNIQUE 위반을 낸다 (같은 ISBN 동시 등록 흉내)
  public void failNextSaveWithUniqueViolation() {
    failNextSave = true;
  }

  @Override
  public Book save(Book book) {
    if (failNextSave) {
      failNextSave = false;
      throw new UniqueConstraintViolationException(new RuntimeException("23505"));
    }
    if (book.getId() == null) {
      // 실제 DB가 없으니 JPA 대신 여기서 id와 createdAt을 채운다
      ReflectionTestUtils.setField(book, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(book, "createdAt", BASE_TIME.plusSeconds(books.size()));
    }
    books.add(book);
    return book;
  }

  @Override
  public Optional<Book> findActiveById(UUID bookId) {
    return books.stream()
        .filter(book -> Objects.equals(book.getId(), bookId))
        .filter(book -> !book.isDeleted())
        .findFirst();
  }

  @Override
  public Optional<Book> findById(UUID bookId) {
    return books.stream().filter(book -> Objects.equals(book.getId(), bookId)).findFirst();
  }

  @Override
  public boolean existsByIsbn(Isbn isbn) {
    return books.stream().anyMatch(book -> Objects.equals(book.getIsbn(), isbn));
  }

  @Override
  public void delete(Book book) {
    books.remove(book);
  }

  public int count() {
    return books.size();
  }

  // 저장된 도서 전부 (논리 삭제 포함). FakeBookSearchPort가 검색할 때 쓴다
  public List<Book> all() {
    return books;
  }
}
