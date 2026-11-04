package com.sprint.deokhugam.user.adapter.persistence;

import com.sprint.deokhugam.user.application.dto.PowerUserCursor;
import com.sprint.deokhugam.user.application.dto.req.PowerUserSearchCond;
import com.sprint.deokhugam.user.application.required.UserRepositoryPort;
import com.sprint.deokhugam.user.domain.Email;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryPortAdapter implements UserRepositoryPort {

  private final UserJpaRepository userJpaRepository;
  private final UserQuerydslRepository querydslRepository;

  @Override
  public User save(User user) {
    return userJpaRepository.save(user);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return userJpaRepository.existsByEmail(email);
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return userJpaRepository.findByEmail(email);
  }

  @Override
  public Optional<User> findById(UUID userId) {
    return userJpaRepository.findById(userId);
  }

  /*
   * 물리 삭제시 한 번에 전부 삭제할지 나눠서 할지 논의해봐야할듯.
   * */
  @Override
  public List<UUID> findCleanupIds(Instant cutoff, int limit) {
    return querydslRepository.findCleanupIds(cutoff, limit);
  }

  @Override
  public void remove(UUID userId) {
    userJpaRepository.deleteById(userId);
  }

  @Override
  public List<UUID> findRankingTargetIds() {
    return userJpaRepository.findRankingTargetIds();
  }

  @Override
  public List<User> findPowerUsers(PowerUserSearchCond condition, LocalDate publishedOn) {
    return querydslRepository.findPowerUsers(condition, publishedOn);
  }

  @Override
  public long countPowerUsers(LocalDate publishedOn) {
    return querydslRepository.countPowerUsers(publishedOn);
  }

  @Override
  public long countPowerUsersAhead(PowerUserCursor point, Instant createdAt) {
    return querydslRepository.countPowerUserAhead(point, createdAt);
  }
}
