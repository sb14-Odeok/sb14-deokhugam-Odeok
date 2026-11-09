package com.sprint.deokhugam.comment.domain.exception;

import com.sprint.deokhugam.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {
  NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글 정보가 없습니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "COMMENT_FORBIDDEN", "본인이 작성한 댓글만 수정·삭제할 수 있습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
