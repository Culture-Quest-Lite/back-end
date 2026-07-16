package org.sep490.backend.module.planner.service;

import org.sep490.backend.module.planner.dto.record.DistanceMatrixResult;

import java.util.List;

public interface GoongDistanceService {

    DistanceMatrixResult getMatrix(List<double[]> points);
}
