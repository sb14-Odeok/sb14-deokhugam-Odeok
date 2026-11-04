package com.sprint.deokhugam.user.application.dto.req;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import java.time.Instant;
import org.springframework.data.domain.Sort.Direction;

public record PowerUserSearchCond(
    RankingPeriod period, Direction direction, String cursor, Instant after, int limit) {

  public PowerUserSearchCond {
    period = period == null ? RankingPeriod.ALL_TIME : period;
    direction = direction == null ? Direction.ASC : direction;

    if (limit < 1 || limit > 100) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    if ((cursor == null) != (after == null)) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
  }
}
