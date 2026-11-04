package com.sprint.deokhugam.user.application.dto.res;

import com.sprint.deokhugam.user.domain.ranking.RankingPeriod;
import java.time.Instant;
import java.util.UUID;

public record PowerUserDto(
    UUID userId,
    String nickname,
    RankingPeriod period,
    Instant createdAt,
    long rank,
    double score,
    double reviewScoreSum,
    long likeCount,
    long commentCount) {}
