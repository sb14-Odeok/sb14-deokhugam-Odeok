package com.sprint.deokhugam.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
  LIKE("[%s]님이 나의 리뷰를 좋아합니다."),
  COMMENT("[%s]님이 나의 리뷰에 댓글을 남겼습니다."),
  RANKED("나의 리뷰가 %s에 진입했습니다.");

  private final String template;

  public String format(String value) {
    return template.formatted(value);
  }
}
