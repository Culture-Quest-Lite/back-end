package org.sep490.backend.module.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.annotation.Auditable;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.content.dto.request.CultureRejectRequest;
import org.sep490.backend.module.content.dto.response.PendingCultureResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.service.inter.CultureModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
public class ContentModerationController {

    private final CultureModerationService cultureModerationService;

    @GetMapping("/pending-culture")
    @PreAuthorize("hasAnyAuthority('PERM_TAG_MANAGE', 'PERM_STORY_MANAGE')")
    public ResponseEntity<PendingCultureResponse> getPending() {
        return ResponseEntity.ok(cultureModerationService.getPending());
    }

    @PutMapping("/tags/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_TAG_MANAGE')")
    @Auditable(value = AuditAction.APPROVE_CULTURE_CONTENT, tableName = "tags")
    public ResponseEntity<TagResponse> approveTag(@PathVariable Long id) {
        return ResponseEntity.ok(cultureModerationService.approveTag(id));
    }

    @PutMapping("/tags/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_TAG_MANAGE')")
    @Auditable(value = AuditAction.REJECT_CULTURE_CONTENT, tableName = "tags")
    public ResponseEntity<TagResponse> rejectTag(
            @PathVariable Long id,
            @RequestBody @Valid CultureRejectRequest request
    ) {
        return ResponseEntity.ok(cultureModerationService.rejectTag(id, request));
    }

    @PutMapping("/stories/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_STORY_MANAGE')")
    @Auditable(value = AuditAction.APPROVE_CULTURE_CONTENT, tableName = "stories")
    public ResponseEntity<StoryResponse> approveStory(@PathVariable Long id) {
        return ResponseEntity.ok(cultureModerationService.approveStory(id));
    }

    @PutMapping("/stories/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_STORY_MANAGE')")
    @Auditable(value = AuditAction.REJECT_CULTURE_CONTENT, tableName = "stories")
    public ResponseEntity<StoryResponse> rejectStory(
            @PathVariable Long id,
            @RequestBody @Valid CultureRejectRequest request
    ) {
        return ResponseEntity.ok(cultureModerationService.rejectStory(id, request));
    }
}
