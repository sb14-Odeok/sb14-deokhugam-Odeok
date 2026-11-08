package com.sprint.deokhugam.book.adapter.web;

import com.sprint.deokhugam.book.application.dto.BookCreateRequest;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.book.application.dto.BookSearchCondition;
import com.sprint.deokhugam.book.application.dto.BookSortField;
import com.sprint.deokhugam.book.application.dto.BookUpdateRequest;
import com.sprint.deokhugam.book.application.provided.command.BookRemover;
import com.sprint.deokhugam.book.application.provided.command.BookWriterUseCase;
import com.sprint.deokhugam.book.application.provided.query.BookFinder;
import com.sprint.deokhugam.book.application.provided.query.BookInfoFinder;
import com.sprint.deokhugam.book.application.provided.query.IsbnRecognizer;
import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
public class BookController {

  private final BookWriterUseCase bookWriterUseCase;
  private final BookRemover bookRemover;
  private final BookFinder bookFinder;
  private final BookInfoFinder bookInfoFinder;
  private final IsbnRecognizer isbnRecognizer;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<BookDto> create(
      @RequestPart("bookData") @Valid BookCreateRequest bookData,
      @RequestPart(value = "thumbnailImage", required = false) MultipartFile thumbnailImage) {
    BookDto bookDto = bookWriterUseCase.create(bookData, thumbnailImage);
    return ResponseEntity.status(HttpStatus.CREATED).body(bookDto);
  }

  // 기본값(orderBy=title, direction=DESC, limit=50)은 명세 그대로
  @GetMapping
  public ResponseEntity<CursorPageResponse<BookDto>> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "title") String orderBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction direction,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Instant after,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    BookSearchCondition condition =
        new BookSearchCondition(keyword, BookSortField.from(orderBy), direction, cursor, after);
    return ResponseEntity.ok(bookFinder.search(condition, limit));
  }

  // 글자 경로 /info 가 /{bookId} 보다 먼저 매칭된다
  @GetMapping("/info")
  public ResponseEntity<BookInfoDto> getInfo(@RequestParam String isbn) {
    return ResponseEntity.ok(bookInfoFinder.getBookInfo(isbn));
  }

  // 응답은 JSON이 아니라 ISBN 문자열 하나다. FE가 본문을 그대로 isbn 칸에 넣는다
  @PostMapping(value = "/isbn/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> recognizeIsbn(@RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(isbnRecognizer.recognizeIsbn(image));
  }

  @GetMapping("/{bookId}")
  public ResponseEntity<BookDto> get(@PathVariable UUID bookId) {
    return ResponseEntity.ok(bookFinder.getById(bookId));
  }

  // 등록과 같은 multipart 형식
  @PatchMapping(value = "/{bookId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<BookDto> update(
      @PathVariable UUID bookId,
      @RequestPart("bookData") @Valid BookUpdateRequest bookData,
      @RequestPart(value = "thumbnailImage", required = false) MultipartFile thumbnailImage) {
    return ResponseEntity.ok(bookWriterUseCase.update(bookId, bookData, thumbnailImage));
  }

  @DeleteMapping("/{bookId}")
  public ResponseEntity<Void> delete(@PathVariable UUID bookId) {
    bookRemover.delete(bookId);
    return ResponseEntity.noContent().build();
  }

  // UI에는 없는 기능. 리뷰, 댓글, 좋아요, 썸네일 파일까지 지운다
  @DeleteMapping("/{bookId}/hard")
  public ResponseEntity<Void> hardDelete(@PathVariable UUID bookId) {
    bookRemover.hardDelete(bookId);
    return ResponseEntity.noContent().build();
  }
}
