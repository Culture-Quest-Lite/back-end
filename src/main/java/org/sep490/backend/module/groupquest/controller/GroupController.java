package org.sep490.backend.module.groupquest.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.dto.request.StartGroupQuestRoute;
import org.sep490.backend.module.groupquest.dto.request.GroupRequest;
import org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.dto.response.GroupResponse;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.service.inter.GroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupController {

    GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@RequestBody GroupRequest groupRequest) {
        GroupResponse groupResponse = groupService.createGroup(groupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(groupResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getDetails(@PathVariable("id") Long groupId) {
        GroupResponse groupResponse = groupService.getDetails(groupId);
        return ResponseEntity.ok(groupResponse);
    }

    @PostMapping("/join/{code}")
    public ResponseEntity<GroupResponse> joinGroup(@PathVariable("code") String sharedToken) {
        GroupResponse groupResponse = groupService.joinGroup(sharedToken);
        return ResponseEntity.ok(groupResponse);
    }

    @PutMapping("/{id}/leave")
    public ResponseEntity<GroupResponse> leaveGroup(@PathVariable("id") Long groupId) {
        GroupResponse groupResponse = groupService.leaveGroup(groupId);
        return ResponseEntity.ok(groupResponse);
    }

    @PutMapping("/{id}/kick/{userId}")
    public ResponseEntity<GroupResponse> kickUser(@PathVariable("id") Long groupId, @PathVariable("userId") Long userId) {
        GroupResponse groupResponse = groupService.kickUserFromGroup(userId, groupId);
        return ResponseEntity.ok(groupResponse);
    }

    @PutMapping("/{id}/refresh-token")
    public ResponseEntity<GroupResponse> refreshSharedToken(@PathVariable("id") Long groupId) {
        GroupResponse groupResponse = groupService.refreshSharedToken(groupId);
        return ResponseEntity.ok(groupResponse);
    }

    @GetMapping("/{id}/member")
    public ResponseEntity<List<GroupParticipantResponse>> getMembers(@PathVariable("id") Long groupId,
                                                                     @RequestParam(required = false) GroupParticipantAction action) {
        List<GroupParticipantResponse> groupParticipants = groupService.getGroupParticipantsByAction(groupId, action);
        return ResponseEntity.ok(groupParticipants);
    }

    @PostMapping("/{id}/add/{userId}")
    public ResponseEntity<GroupResponse> addMember(@PathVariable("id") Long groupId, @PathVariable("userId") Long userId) {
        GroupResponse groupResponse = groupService.addUserToGroup(userId, groupId);
        return ResponseEntity.ok(groupResponse);
    }

    @GetMapping()
    public ResponseEntity<List<GroupResponse>> getMyGroups() {
        List<GroupResponse> groups = groupService.getMyGroups();
        return ResponseEntity.ok(groups);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable("id") Long groupId, @RequestBody GroupUpdateRequest groupRequest) {
        GroupResponse groupResponse = groupService.updateGroup(groupId, groupRequest);
        return ResponseEntity.ok(groupResponse);
    }

    @PutMapping("/participant/{participantId}")
    public ResponseEntity<GroupParticipantResponse> updateParticipantStatus(@PathVariable("participantId") Long gpId, @RequestParam GroupParticipantAction action) {
        return ResponseEntity.ok(groupService.updateGroupParticipantAction(gpId, action));
    }
}
