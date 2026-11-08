package com.sprint.deokhugam.book.domain;

import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import java.util.regex.Pattern;

// ISBN 값 객체. 만들어질 때 정리와 검사를 끝내므로 value는 항상 숫자 13자리다
public record Isbn(String value) {

  public static final int LENGTH = 13;

  private static final Pattern ISBN10 = Pattern.compile("\\d{9}[\\dX]");
  private static final Pattern ISBN13 = Pattern.compile("97[89]\\d{10}");

  // 하이픈과 공백은 빼고, 10자리는 13자리로 바꾼다. 비었거나 형식, 체크섬이 틀리면 BOOK_INVALID_ISBN
  public Isbn {
    if (value == null || value.isBlank()) {
      throw new BusinessException(BookErrorCode.INVALID_ISBN, "isbn 없음");
    }
    value = normalize(value);
  }

  // ISBN은 선택 항목이라 빈 값은 null로 돌려준다
  public static Isbn of(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return new Isbn(raw);
  }

  public static boolean isValid(String raw) {
    try {
      new Isbn(raw);
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }

  @Override
  public String toString() {
    return value;
  }

  private static String normalize(String raw) {
    String cleaned = raw.replaceAll("[\\s-]", "").toUpperCase();
    if (ISBN10.matcher(cleaned).matches() && isValidIsbn10(cleaned)) {
      return toIsbn13(cleaned);
    }
    if (ISBN13.matcher(cleaned).matches() && isValidIsbn13(cleaned)) {
      return cleaned;
    }
    throw new BusinessException(BookErrorCode.INVALID_ISBN, "isbn=" + raw);
  }

  // 앞에서부터 10, 9, ... 1을 곱해 더한 값이 11로 나누어떨어져야 한다. 마지막 자리 X는 10
  private static boolean isValidIsbn10(String isbn) {
    int sum = 0;
    for (int i = 0; i < 10; i++) {
      char c = isbn.charAt(i);
      int digit = c == 'X' ? 10 : c - '0';
      sum += digit * (10 - i);
    }
    return sum % 11 == 0;
  }

  // 1, 3, 1, 3, ... 을 번갈아 곱해 더한 값이 10으로 나누어떨어져야 한다
  private static boolean isValidIsbn13(String isbn) {
    int sum = 0;
    for (int i = 0; i < 13; i++) {
      int digit = isbn.charAt(i) - '0';
      sum += digit * (i % 2 == 0 ? 1 : 3);
    }
    return sum % 10 == 0;
  }

  // 978 + 앞 9자리 뒤에 13자리 규칙으로 계산한 검사 숫자를 붙인다
  private static String toIsbn13(String isbn10) {
    String body = "978" + isbn10.substring(0, 9);
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = body.charAt(i) - '0';
      sum += digit * (i % 2 == 0 ? 1 : 3);
    }
    int check = (10 - sum % 10) % 10;
    return body + check;
  }
}
