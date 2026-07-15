package org.sep490.backend.module.groupquest.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByCreatedBy(User user);
}
