package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.FakeBookProviderPort;
import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.domain.book.exception.BookErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookInfoServiceTest {

  private final FakeBookProviderPort bookProviderPort = new FakeBookProviderPort();
  private final BookInfoService bookInfoService = new BookInfoService(bookProviderPort);

  // (4-1)
  @Test
  @DisplayName("포트가 찾은 도서 정보를 그대로 돌려준다")
  void getBookInfo() {
    BookInfoDto info =
        new BookInfoDto(
            "클린 코드", "로버트 C. 마틴", "소개", "인사이트", LocalDate.of(2013, 12, 24), "9788966260959", null);
    bookProviderPort.put(info);

    assertThat(bookInfoService.getBookInfo("9788966260959")).isEqualTo(info);
  }

  // (4-2)
  @Test
  @DisplayName("포트가 못 찾으면 INFO_NOT_FOUND 예외가 발생한다")
  void getBookInfoNotFound() {
    assertThatThrownBy(() -> bookInfoService.getBookInfo("9788966260959"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INFO_NOT_FOUND);
  }

  // (4-3)
  @Test
  @DisplayName("하이픈이나 10자리로 조회해도 13자리로 정리해서 포트에 넘긴다")
  void getBookInfoNormalizesInput() {
    BookInfoDto info =
        new BookInfoDto(
            "클린 코드", "로버트 C. 마틴", "소개", "인사이트", LocalDate.of(2013, 12, 24), "9788966260959", null);
    bookProviderPort.put(info);

    assertThat(bookInfoService.getBookInfo("978-89-6626-095-9")).isEqualTo(info);
    assertThat(bookInfoService.getBookInfo("8966260950")).isEqualTo(info);
  }

  // (4-4)
  @Test
  @DisplayName("포트가 준 10자리 ISBN은 13자리로 바꿔 돌려준다")
  void getBookInfoNormalizesResult() {
    bookProviderPort.put(
        "9788996991342",
        new BookInfoDto("옛 책", "저자", "소개", "출판사", LocalDate.of(2000, 1, 1), "8996991341", null));

    assertThat(bookInfoService.getBookInfo("9788996991342").isbn()).isEqualTo("9788996991342");
  }

  // (4-5)
  @Test
  @DisplayName("형식이 틀리거나 빈 ISBN이면 포트를 부르지 않고 INVALID_ISBN 예외가 발생한다")
  void getBookInfoInvalidIsbn() {
    assertThatThrownBy(() -> bookInfoService.getBookInfo("9788966260950"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_ISBN);
    assertThatThrownBy(() -> bookInfoService.getBookInfo(""))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_ISBN);
  }
}
