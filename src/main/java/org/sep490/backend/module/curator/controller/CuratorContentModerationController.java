package org.sep490.backend.module.curator.controller;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.content.dto.response.CultureContentResponse;
import org.sep490.backend.module.content.service.inter.CultureModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curator/content")
@RequiredArgsConstructor
public class CuratorContentModerationController {

    private final CultureModerationService cultureModerationService;

    @GetMapping("/rejected")
    @PreAuthorize("hasAnyAuthority('PERM_TAG_MANAGE', 'PERM_STORY_MANAGE')")
    public ResponseEntity<CultureContentResponse> getRejected() {
        return ResponseEntity.ok(cultureModerationService.getRejected());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('PERM_TAG_MANAGE', 'PERM_STORY_MANAGE')")
    public ResponseEntity<CultureContentResponse> getPending() {
        return ResponseEntity.ok(cultureModerationService.getPending());
    }
}
