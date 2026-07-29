package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.filter.ReviewFilterRequest;
import org.sep490.backend.module.content.dto.request.ReviewRequest;
import org.sep490.backend.module.content.dto.request.ReviewUpdateRequest;
import org.sep490.backend.module.content.dto.response.ReviewResponse;
import org.sep490.backend.module.content.dto.response.ReviewSummaryResponse;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.sep490.backend.module.content.service.inter.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReviewController {

    ReviewService reviewService;

    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> getDetails(@PathVariable Long id) {
        ReviewResponse response = reviewService.getReviewDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> search(@Valid @ModelAttribute ReviewFilterRequest filter) {
        Page<ReviewResponse> response = reviewService.getAllReview(filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<ReviewResponse>> getMyReviews(@Valid @ModelAttribute ReviewFilterRequest filter) {
        Page<ReviewResponse> response = reviewService.getMyReviews(filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<ReviewSummaryResponse> getSummary(@RequestParam ReviewTargetType targetType,
                                                            @RequestParam Long targetId) {
        ReviewSummaryResponse response = reviewService.getSummary(targetType, targetId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewResponse> create(@Valid @ModelAttribute ReviewRequest reviewRequest) {
        ReviewResponse response = reviewService.createReview(reviewRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewResponse> update(@PathVariable Long id,
                                                 @Valid @ModelAttribute ReviewUpdateRequest reviewRequest) {
        ReviewResponse response = reviewService.updateReview(id, reviewRequest);
        return ResponseEntity.ok(response);
    }

    //Ẩn hoặc hiện đánh giá (dành cho kiểm duyệt viên)
    @PutMapping("/{id}/status")
    public ResponseEntity<ReviewResponse> updateStatus(@PathVariable Long id, @RequestParam ReviewStatus status) {
        ReviewResponse response = reviewService.updateReviewStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.ok("Deleted");
    }
}
