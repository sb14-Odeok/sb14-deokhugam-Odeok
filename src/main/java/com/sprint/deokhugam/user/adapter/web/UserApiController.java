package com.sprint.deokhugam.user.adapter.web;

import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.common.web.annotation.RequestHeader;
import com.sprint.deokhugam.user.adapter.dto.req.UserLoginRequest;
import com.sprint.deokhugam.user.adapter.dto.req.UserRegisterRequest;
import com.sprint.deokhugam.user.adapter.dto.req.UserUpdateRequest;
import com.sprint.deokhugam.user.adapter.dto.res.PowerUserResponse;
import com.sprint.deokhugam.user.adapter.dto.res.UserResponse;
import com.sprint.deokhugam.user.application.dto.command.RegisterUserCommand;
import com.sprint.deokhugam.user.application.dto.query.LoginUserQuery;
import com.sprint.deokhugam.user.application.dto.query.PowerUserSearchCond;
import com.sprint.deokhugam.user.application.dto.result.PowerUserResult;
import com.sprint.deokhugam.user.application.dto.result.UserResult;
import com.sprint.deokhugam.user.application.provided.command.UserCleanupUseCase;
import com.sprint.deokhugam.user.application.provided.command.UserCommand;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import com.sprint.deokhugam.user.application.provided.query.UserLogin;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserApiController {

  private final UserCommand userCommand;
  private final UserLogin userLogin;
  private final UserFinder userFinder;
  private final UserCleanupUseCase userCleanupUseCase;

  @DeleteMapping("/hard/{userId}")
  public ResponseEntity<Void> hardDelete(@PathVariable UUID userId) {
    userCleanupUseCase.cleanupUserWithRelatedData(userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
    RegisterUserCommand command = request.toCommand();
    UserResult userResult = userCommand.registerUser(command);

    return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(userResult));
  }

  @PostMapping("/login")
  public ResponseEntity<UserResponse> login(@Valid @RequestBody UserLoginRequest request) {
    LoginUserQuery query = request.toQuery();
    UserResult result = userLogin.login(query);
    return ResponseEntity.ok(UserResponse.from(result));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
    UserResult result = userFinder.getUserDetail(userId);
    UserResponse userResponse = UserResponse.from(result);
    return ResponseEntity.ok(userResponse);
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<UserResponse> update(
      @RequestHeader("Deokhugam-Request-User-ID") UUID headerId,
      @PathVariable UUID userId,
      @RequestBody @Valid UserUpdateRequest request) {
    UserResult result = userCommand.update(userId, headerId, request.nickname());

    return ResponseEntity.ok(UserResponse.from(result));
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> delete(
      @RequestHeader("Deokhugam-Request-User-ID") UUID headerId, @PathVariable UUID userId) {

    userCommand.withdraw(userId, headerId);

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/power")
  public ResponseEntity<CursorPageResponse<PowerUserResponse>> getPowerUsers(
      @RequestParam(defaultValue = "ALL_TIME") RankingPeriod period,
      @RequestParam(defaultValue = "ASC") Direction direction,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant after,
      @RequestParam(defaultValue = "20") int limit) {
    PowerUserSearchCond condition =
        new PowerUserSearchCond(period, direction, cursor, after, limit);

    CursorPageResponse<PowerUserResult> result = userFinder.findPowerUsers(condition);

    List<PowerUserResponse> content =
        result.content().stream().map(PowerUserResponse::from).toList();

    CursorPageResponse<PowerUserResponse> response =
        new CursorPageResponse<>(
            content,
            result.nextCursor(),
            result.nextAfter(),
            result.size(),
            result.totalElements(),
            result.hasNext());

    return ResponseEntity.ok(response);
  }
}
