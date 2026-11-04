package com.sprint.deokhugam.user.application.provided.query;

import com.sprint.deokhugam.user.application.dto.req.UserLoginRequest;
import com.sprint.deokhugam.user.application.dto.res.UserDto;

public interface UserLogin {

  UserDto login(UserLoginRequest request);
}
