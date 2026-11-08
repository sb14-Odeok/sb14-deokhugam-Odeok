package com.sprint.deokhugam.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.FakeOcrTextPort;
import com.sprint.deokhugam.book.domain.IsbnExtractor;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class IsbnRecognitionServiceTest {

  private final FakeOcrTextPort ocrTextPort = new FakeOcrTextPort();
  private final IsbnRecognitionService isbnRecognitionService =
      new IsbnRecognitionService(ocrTextPort, new IsbnExtractor());

  private static MockMultipartFile image(String filename, String contentType, byte[] bytes) {
    return new MockMultipartFile("image", filename, contentType, bytes);
  }

  private static MockMultipartFile jpeg() {
    return image("cover.jpg", "image/jpeg", new byte[] {1, 2, 3});
  }

  // (5-1)
  @Test
  @DisplayName("OCR 글에서 ISBN을 찾아 숫자만 돌려준다")
  void recognizeIsbn() {
    ocrTextPort.setText("ISBN 978-89-6626-095-9\n9 788966 260959");

    assertThat(isbnRecognitionService.recognizeIsbn(jpeg())).isEqualTo("9788966260959");
  }

  // (5-2)
  @Test
  @DisplayName("OCR 글이 비어 있으면 OCR_FAILED 예외")
  void recognizeIsbnEmptyText() {
    ocrTextPort.setText("");

    assertThatThrownBy(() -> isbnRecognitionService.recognizeIsbn(jpeg()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.OCR_FAILED);
  }

  // (5-3)
  @Test
  @DisplayName("글은 있는데 ISBN 모양이 없으면 OCR_FAILED 예외")
  void recognizeIsbnNoIsbnInText() {
    ocrTextPort.setText("클린 코드 로버트 C. 마틴 33000원");

    assertThatThrownBy(() -> isbnRecognitionService.recognizeIsbn(jpeg()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.OCR_FAILED);
  }

  // (5-3b)
  @Test
  @DisplayName("숫자를 잘못 읽어 체크섬이 틀린 후보는 건너뛴다")
  void recognizeIsbnSkipsInvalidChecksum() {
    ocrTextPort.setText("ISBN 978-89-6626-095-0\n9 788966 260959");

    assertThat(isbnRecognitionService.recognizeIsbn(jpeg())).isEqualTo("9788966260959");
  }

  // (5-4)
  @Test
  @DisplayName("빈 파일이면 OCR을 부르지 않고 INVALID_IMAGE 예외")
  void recognizeIsbnEmptyFile() {
    assertThatThrownBy(
            () ->
                isbnRecognitionService.recognizeIsbn(image("cover.jpg", "image/jpeg", new byte[0])))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
  }

  // (5-5)
  @Test
  @DisplayName("1MB를 넘으면 INVALID_IMAGE 예외")
  void recognizeIsbnTooLarge() {
    MockMultipartFile big = image("cover.jpg", "image/jpeg", new byte[1_000_001]);

    assertThatThrownBy(() -> isbnRecognitionService.recognizeIsbn(big))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
  }

  // (5-6)
  @Test
  @DisplayName("Content-Type이나 확장자가 jpg, png가 아니면 INVALID_IMAGE 예외")
  void recognizeIsbnWrongType() {
    ocrTextPort.setText("ISBN 9788966260959");

    assertThatThrownBy(
            () ->
                isbnRecognitionService.recognizeIsbn(
                    image("cover.gif", "image/gif", new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThatThrownBy(
            () ->
                isbnRecognitionService.recognizeIsbn(
                    image("cover.pdf", "image/jpeg", new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
    assertThatThrownBy(
            () -> isbnRecognitionService.recognizeIsbn(image("cover.jpg", null, new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_IMAGE);
  }
}
