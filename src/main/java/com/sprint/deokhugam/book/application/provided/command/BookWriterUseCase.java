package com.sprint.deokhugam.book.application.provided.command;

import com.sprint.deokhugam.book.application.dto.BookCreateRequest;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookUpdateRequest;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

// 썸네일을 S3에 올리는 등록과 수정. 삭제는 BookRemover
public interface BookWriterUseCase {

  BookDto create(BookCreateRequest request, MultipartFile thumbnailImage);

  // 없는 도서, 논리 삭제된 도서는 BOOK_NOT_FOUND
  BookDto update(UUID bookId, BookUpdateRequest request, MultipartFile thumbnailImage);
}
