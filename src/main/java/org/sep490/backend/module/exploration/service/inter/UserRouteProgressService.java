package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.exploration.dto.filter.UserRouteProgressFilter;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressResponse;
import org.springframework.data.domain.Page;

import java.util.HashMap;

public interface UserRouteProgressService {
    HashMap<Integer, UserRouteProgressResponse> startRouteProgress(Long routeId);
    UserRouteProgressResponse abandonRouteProgress(Long routeId);
    Page<UserRouteProgressResponse> getAll(UserRouteProgressFilter filter);
}
