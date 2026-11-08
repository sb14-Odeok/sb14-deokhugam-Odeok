package com.sprint.deokhugam.book.application.required;

// 저장소는 UNIQUE 제약에 걸렸다는 사실만 알린다. 무슨 업무 오류인지는 서비스가 정한다
public class UniqueConstraintViolationException extends RuntimeException {

  public UniqueConstraintViolationException(Throwable cause) {
    super(cause);
  }
}
