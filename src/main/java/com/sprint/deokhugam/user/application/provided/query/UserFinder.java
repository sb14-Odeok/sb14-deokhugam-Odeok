package com.sprint.deokhugam.user.application.provided.query;

import com.sprint.deokhugam.common.pagination.CursorPageResponse;
import com.sprint.deokhugam.user.application.dto.req.PowerUserSearchCond;
import com.sprint.deokhugam.user.application.dto.res.PowerUserDto;
import com.sprint.deokhugam.user.application.dto.res.UserDto;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserFinder {

  User findByIdOrThrow(UUID userId);

  UserDto getUserDetail(UUID userId);

  List<UUID> findCleanupIds(Instant cutoff, int limit);

  List<UUID> findRankingTargetIds();

  CursorPageResponse<PowerUserDto> findPowerUsers(PowerUserSearchCond cond);
}
