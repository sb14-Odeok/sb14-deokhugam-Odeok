package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.book.application.provided.query.BookInfoFinder;
import com.sprint.deokhugam.book.application.provided.query.IsbnRecognizer;
import com.sprint.deokhugam.book.application.required.BookProviderPort;
import com.sprint.deokhugam.book.application.required.OcrTextPort;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 트랜잭션은 스프링 프록시가 붙이므로 컨텍스트에서 확인한다
@SpringBootTest
@ActiveProfiles("test")
class BookExternalCallTransactionTest {

  @MockitoBean private BookProviderPort bookProviderPort;
  @MockitoBean private OcrTextPort ocrTextPort;
  @Autowired private BookInfoFinder bookInfoFinder;
  @Autowired private IsbnRecognizer isbnRecognizer;

  @Test
  @DisplayName("카카오 포트가 불릴 때 활성 트랜잭션이 없다")
  void getBookInfo_runsWithoutTransaction() {
    AtomicBoolean active = new AtomicBoolean(true);
    given(bookProviderPort.findByIsbn(any()))
        .willAnswer(
            invocation -> {
              active.set(TransactionSynchronizationManager.isActualTransactionActive());
              return Optional.of(
                  new BookInfoDto("제목", null, null, null, null, "9788966260959", null));
            });

    bookInfoFinder.getBookInfo("9788966260959");

    assertThat(active).isFalse();
  }

  @Test
  @DisplayName("OCR 포트가 불릴 때 활성 트랜잭션이 없다")
  void recognizeIsbn_runsWithoutTransaction() {
    AtomicBoolean active = new AtomicBoolean(true);
    given(ocrTextPort.readText(any(), any(), any()))
        .willAnswer(
            invocation -> {
              active.set(TransactionSynchronizationManager.isActualTransactionActive());
              return "ISBN 978-89-6626-095-9";
            });
    MockMultipartFile image =
        new MockMultipartFile("image", "isbn.png", "image/png", new byte[] {1, 2, 3});

    isbnRecognizer.recognizeIsbn(image);

    assertThat(active).isFalse();
  }
}
