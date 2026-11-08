package com.sprint.deokhugam.book.application.provided.query;

import com.sprint.deokhugam.book.application.dto.BookInfoDto;

public interface BookInfoFinder {

  // ISBN으로 등록 폼에 채울 도서 정보 조회. 없으면 BOOK_INFO_NOT_FOUND
  BookInfoDto getBookInfo(String isbn);
}
