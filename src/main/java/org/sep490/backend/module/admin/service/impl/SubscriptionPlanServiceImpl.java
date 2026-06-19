package org.sep490.backend.module.admin.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.admin.dto.filter.SubscriptionPlanFilterRequest;
import org.sep490.backend.module.admin.dto.request.SubscriptionPlanRequest;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.SubscriptionPlanStatus;
import org.sep490.backend.module.admin.mapper.SubscriptionPlanMapper;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
import org.sep490.backend.module.admin.service.SubscriptionPlanService;
import org.sep490.backend.module.admin.specification.SubscriptionPlanSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    SubscriptionPlanMapper subscriptionPlanMapper;
    SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional
    public SubscriptionPlanResponse createSubscriptionPlan(SubscriptionPlanRequest request) {
        if (subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCase(request.getSubscriptionPlanName())) {
            throw new BusinessException("Gói dịch vụ với tên \"" + request.getSubscriptionPlanName() + "\" đã tồn tại");
        }
        SubscriptionPlan plan = subscriptionPlanMapper.toEntity(request);
        plan.setStatus(SubscriptionPlanStatus.ACTIVE);
        plan = subscriptionPlanRepository.save(plan);
        return subscriptionPlanMapper.toResponse(plan);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse updateSubscriptionPlan(Long id, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = getSubscriptionPlanById(id);
        if (plan.getStatus() == SubscriptionPlanStatus.INACTIVE) {
            throw new BusinessException("Gói dịch vụ đang bị vô hiệu hóa, không thể cập nhật");
        }
        if (subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCaseAndSubscriptionPlanIdNot(
                request.getSubscriptionPlanName(), id)) {
            throw new BusinessException("Gói dịch vụ với tên \"" + request.getSubscriptionPlanName() + "\" đã tồn tại");
        }
        subscriptionPlanMapper.updateEntityFromRequest(request, plan);
        plan = subscriptionPlanRepository.save(plan);
        return subscriptionPlanMapper.toResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanResponse getSubscriptionPlanDetail(Long id) {
        return subscriptionPlanMapper.toResponse(getSubscriptionPlanById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponse> getAllWithFilter(SubscriptionPlanFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Specification<SubscriptionPlan> spec = SubscriptionPlanSpecification.filter(
                filter.getSearch(), filter.getStatus());
        return subscriptionPlanRepository.findAll(spec, pageable).map(subscriptionPlanMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteSubscriptionPlan(Long id) {
        SubscriptionPlan plan = getSubscriptionPlanById(id);
        if (plan.getStatus() == SubscriptionPlanStatus.DELETED) {
            throw new BusinessException("Gói dịch vụ đã bị xóa trước đó");
        }
        plan.setStatus(SubscriptionPlanStatus.DELETED);
        subscriptionPlanRepository.save(plan);
    }

    @Override
    public SubscriptionPlan getSubscriptionPlanById(Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy gói dịch vụ với id: " + id));
        if (plan.getStatus() == SubscriptionPlanStatus.DELETED) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "Gói dịch vụ với id " + id + " đã bị xóa");
        }
        return plan;
    }
}
