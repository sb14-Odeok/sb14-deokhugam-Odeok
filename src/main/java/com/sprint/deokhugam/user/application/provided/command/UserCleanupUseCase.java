package com.sprint.deokhugam.user.application.provided.command;

import java.util.UUID;

public interface UserCleanupUseCase {

  void cleanupUserWithRelatedData(UUID targetId);
}
