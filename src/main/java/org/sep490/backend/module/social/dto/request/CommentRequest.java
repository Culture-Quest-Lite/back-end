package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentRequest {
    @NotBlank(message = "Nội dung bình luận không được để trống")
    private String comment;
    
    private Long parentActionId; 
}
