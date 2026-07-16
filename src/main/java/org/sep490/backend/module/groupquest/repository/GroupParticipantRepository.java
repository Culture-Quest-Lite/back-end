package org.sep490.backend.module.groupquest.repository;

import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupParticipantRepository extends JpaRepository<GroupParticipant, Long> {
    Optional<GroupParticipant> findByGroup_GroupIdAndUser_UserId(Long groupId, Long userId);
    Boolean existsByGroup_GroupIdAndUser_UserId(Long groupId, Long userId);
    List<GroupParticipant> findAllByUser_UserIdAndActionAndStatus(Long userId, GroupParticipantAction action, GroupStatus status);
    List<GroupParticipant> findAllByGroup_GroupId(Long groupId);
}
