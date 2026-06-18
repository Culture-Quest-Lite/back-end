package org.sep490.backend.module.exploration.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.dto.request.CheckInRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInResponse;
import org.sep490.backend.module.exploration.service.inter.CheckInService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/check-ins")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckInController {

    CheckInService checkInService;

    @PostMapping
    public ResponseEntity<CheckInResponse> checkIn(@RequestBody CheckInRequest checkInRequest) {
        CheckInResponse checkInResponse = checkInService.checkIn(checkInRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(checkInResponse);
    }
}
