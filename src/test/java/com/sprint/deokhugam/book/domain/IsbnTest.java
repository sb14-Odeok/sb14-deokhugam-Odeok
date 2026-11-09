package com.sprint.deokhugam.book.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsbnTest {

  // ===== (1) new Isbn() : 정리 =====
  // (1-1)
  @Test
  @DisplayName("13자리 숫자는 그대로 value가 된다")
  void create13() {
    assertThat(new Isbn("9788966260959").value()).isEqualTo("9788966260959");
  }

  // (1-2)
  @Test
  @DisplayName("하이픈과 공백은 빼고 담는다")
  void createRemovesHyphensAndSpaces() {
    assertThat(new Isbn("978-89-6626-095-9").value()).isEqualTo("9788966260959");
    assertThat(new Isbn("9 788966 260959").value()).isEqualTo("9788966260959");
    assertThat(new Isbn("  9788966260959  ").value()).isEqualTo("9788966260959");
  }

  // (1-3)
  @Test
  @DisplayName("10자리는 같은 책의 13자리로 바꿔 담는다")
  void create10To13() {
    assertThat(new Isbn("8996991341").value()).isEqualTo("9788996991342");
    assertThat(new Isbn("89-96991-34-1").value()).isEqualTo("9788996991342");
  }

  // (1-4)
  @Test
  @DisplayName("10자리 끝자리 X는 대소문자 모두 받는다")
  void create10WithX() {
    assertThat(new Isbn("080442957X").value()).isEqualTo("9780804429573");
    assertThat(new Isbn("080442957x").value()).isEqualTo("9780804429573");
  }

  // (1-5)
  @Test
  @DisplayName("같은 책이면 어떤 모양으로 만들어도 같은 값 객체다")
  void equalsByValue() {
    assertThat(new Isbn("8966260950")).isEqualTo(new Isbn("978-89-6626-095-9"));
    assertThat(new Isbn("9788966260959").toString()).isEqualTo("9788966260959");
  }

  // ===== (2) new Isbn() : 검사 =====
  // (2-1)
  @ParameterizedTest
  @ValueSource(
      strings = {
        "9788966260950", // 13자리 체크섬 틀림
        "8996991340", // 10자리 체크섬 틀림
        "12345678901", // 11자리
        "978896626095", // 12자리
        "9768966260959", // 978, 979로 시작하지 않음
        "978896626095X", // 13자리에는 X가 없다
        "ISBN9788966260959", // 숫자가 아닌 글자
        "8966260950 9788966260959", // 두 개가 붙어 있음
        "", // 빈 값은 값 객체로 만들 수 없다
        "   "
      })
  @DisplayName("형식이나 체크섬이 틀리거나 비어 있으면 INVALID_ISBN 예외가 발생한다")
  void createInvalid(String raw) {
    assertThatThrownBy(() -> new Isbn(raw))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_ISBN);
  }

  // (2-2)
  @Test
  @DisplayName("null로는 만들 수 없다")
  void createNull() {
    assertThatThrownBy(() -> new Isbn(null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_ISBN);
  }

  // ===== (3) of() : 선택 항목용 =====
  // (3-1)
  @Test
  @DisplayName("of는 null, 빈 문자열, 공백만 있으면 null을 돌려주고 값이 있으면 값 객체를 만든다")
  void of() {
    assertThat(Isbn.of(null)).isNull();
    assertThat(Isbn.of("")).isNull();
    assertThat(Isbn.of("   ")).isNull();
    assertThat(Isbn.of("978-89-6626-095-9")).isEqualTo(new Isbn("9788966260959"));
  }

  // (3-2)
  @Test
  @DisplayName("of도 형식이 틀리면 INVALID_ISBN 예외가 발생한다")
  void ofInvalid() {
    assertThatThrownBy(() -> Isbn.of("9788966260950"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.INVALID_ISBN);
  }

  // ===== (4) isValid() =====
  // (4-1)
  @Test
  @DisplayName("isValid는 예외 대신 true, false로 알려준다")
  void isValid() {
    assertThat(Isbn.isValid("978-89-6626-095-9")).isTrue();
    assertThat(Isbn.isValid("9788966260950")).isFalse();
    assertThat(Isbn.isValid("")).isFalse();
  }
}
