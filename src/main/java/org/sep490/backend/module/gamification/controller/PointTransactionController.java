package org.sep490.backend.module.gamification.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.gamification.dto.response.PointTransactionResponse;
import org.sep490.backend.module.gamification.service.PointTransactionService;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PointTransactionController {

    PointTransactionService pointTransactionService;

    @GetMapping("/history")
    public ResponseEntity<Page<PointTransactionResponse>> getMyPointHistory(
            @Valid @ParameterObject @ModelAttribute VoucherFilter filter) {
        return ResponseEntity.ok(pointTransactionService.getMyPointHistory(filter));
    }
}
