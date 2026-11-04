package com.sprint.deokhugam.user.application.dto.res;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String email, String nickname, Instant createdAt) {

  public static UserDto to(UUID id, String email, String nickname, Instant createdAt) {
    return new UserDto(id, email, nickname, createdAt);
  }
}
