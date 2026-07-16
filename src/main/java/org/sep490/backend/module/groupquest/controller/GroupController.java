package org.sep490.backend.module.groupquest.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.groupquest.dto.request.GroupRequest;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.dto.response.GroupResponse;
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
    public ResponseEntity<List<GroupParticipantResponse>> getMembers(@PathVariable("id") Long groupId) {
        List<GroupParticipantResponse> groupParticipants = groupService.getGroupParticipants(groupId);
        return ResponseEntity.ok(groupParticipants);
    }
}
