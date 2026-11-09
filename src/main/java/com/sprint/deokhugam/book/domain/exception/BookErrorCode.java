package com.sprint.deokhugam.book.domain.exception;

import com.sprint.deokhugam.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BookErrorCode implements ErrorCode {
  NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "도서 정보가 없습니다."),
  DUPLICATE_ISBN(HttpStatus.CONFLICT, "BOOK_DUPLICATE_ISBN", "이미 등록된 ISBN입니다."),
  INVALID_ISBN(HttpStatus.BAD_REQUEST, "BOOK_INVALID_ISBN", "ISBN 형식이 올바르지 않습니다."),
  INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_INFO_NOT_FOUND", "ISBN에 해당하는 도서 정보가 없습니다."),
  INVALID_IMAGE(HttpStatus.BAD_REQUEST, "BOOK_INVALID_IMAGE", "지원하지 않는 이미지 형식입니다."),
  OCR_FAILED(HttpStatus.BAD_REQUEST, "BOOK_OCR_FAILED", "이미지에서 ISBN을 인식하지 못했습니다."),
  OCR_UNAVAILABLE(
      HttpStatus.INTERNAL_SERVER_ERROR, "BOOK_OCR_UNAVAILABLE", "ISBN 인식 서비스에 연결할 수 없습니다."),
  THUMBNAIL_UPLOAD_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "BOOK_THUMBNAIL_UPLOAD_FAILED", "썸네일을 저장하지 못했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
