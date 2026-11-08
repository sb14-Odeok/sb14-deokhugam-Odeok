package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.book.application.provided.query.BookInfoFinder;
import com.sprint.deokhugam.book.application.required.BookProviderPort;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.book.exception.BookErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 카카오만 부르고 DB는 쓰지 않아서 트랜잭션을 두지 않는다 (커넥션을 쥔 채 기다리지 않게)
@Service
@RequiredArgsConstructor
public class BookInfoService implements BookInfoFinder {

  private final BookProviderPort bookProviderPort;

  // '없음'을 404로 바꾸는 건 우리 서비스의 규칙이라 어댑터가 아닌 여기서 한다
  @Override
  public BookInfoDto getBookInfo(String rawIsbn) {
    Isbn isbn = Isbn.of(rawIsbn);
    if (isbn == null) {
      throw new BusinessException(BookErrorCode.INVALID_ISBN, "isbn 없음");
    }
    BookInfoDto info =
        bookProviderPort
            .findByIsbn(isbn)
            .orElseThrow(() -> new BusinessException(BookErrorCode.INFO_NOT_FOUND, "isbn=" + isbn));
    // 카카오가 준 ISBN도 등록 때 저장되는 모양으로 맞춘다. 없으면 조회한 값을 쓴다
    Isbn provided = Isbn.of(info.isbn());
    return info.withIsbn((provided == null ? isbn : provided).value());
  }
}
