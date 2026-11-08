package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sprint.deokhugam.book.adapter.persistence.BookJpaRepository;
import com.sprint.deokhugam.book.application.dto.BookCreateRequest;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.provided.command.BookWriterUseCase;
import com.sprint.deokhugam.book.application.required.ThumbnailStoragePort;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 트랜잭션은 스프링 프록시가 붙이므로 컨텍스트에서 확인한다. 테스트에 트랜잭션을 걸면 확인이 안 돼서 만든 도서는 직접 지운다
@SpringBootTest
@ActiveProfiles("test")
class BookWriterTransactionTest {

  @MockitoBean private ThumbnailStoragePort thumbnailStoragePort;
  @Autowired private BookWriterUseCase bookWriterUseCase;
  @Autowired private BookJpaRepository bookJpaRepository;

  @Test
  @DisplayName("썸네일을 올리는 동안 활성 트랜잭션이 없다")
  void uploadRunsWithoutTransaction() {
    AtomicBoolean active = new AtomicBoolean(true);
    given(thumbnailStoragePort.upload(any(), any(), any()))
        .willAnswer(
            invocation -> {
              active.set(TransactionSynchronizationManager.isActualTransactionActive());
              return "https://test-bucket.s3.amazonaws.com/" + invocation.getArgument(0);
            });
    BookCreateRequest request =
        new BookCreateRequest("클린 코드", "로버트 C. 마틴", "소개", "인사이트", LocalDate.of(2013, 12, 24), null);
    MockMultipartFile image =
        new MockMultipartFile("thumbnailImage", "cover.png", "image/png", new byte[] {1, 2, 3});

    BookDto created = bookWriterUseCase.create(request, image);
    bookJpaRepository.deleteById(created.id());

    assertThat(active).isFalse();
  }
}
