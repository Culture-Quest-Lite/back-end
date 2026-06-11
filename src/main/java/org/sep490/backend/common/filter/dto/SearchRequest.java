package org.sep490.backend.common.filter.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchRequest {
    List<FilterRequest> filters = new ArrayList<>();
    private int page = 0;
    private int size = 10;
    private String sortBy = "createdAt";
    private String sortDirection = "ASC";
}
