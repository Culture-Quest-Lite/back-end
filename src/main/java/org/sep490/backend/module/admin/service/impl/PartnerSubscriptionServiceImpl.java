package org.sep490.backend.module.admin.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.entity.PartnerSubscription;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.PartnerSubscriptionStatus;
import org.sep490.backend.module.admin.mapper.PartnerSubscriptionMapper;
import org.sep490.backend.module.admin.repository.PartnerSubscriptionRepository;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PartnerSubscriptionServiceImpl implements PartnerSubscriptionService {
    SubscriptionPlanRepository planRepository;
    PartnerSubscriptionRepository partnerSubscriptionRepository;
    PartnerSubscriptionMapper subscriptionMapper;
    UserService userService;

    @Override
    @Transactional
    public PartnerSubscriptionResponse registerSubscription(PartnerSubscriptionRequest request) {
        User currentPartner = userService.getCurrentUser();
        SubscriptionPlan plan = planRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new BusinessException("Gói đăng ký không tồn tại"));

//        if (!partnerSubscriptionRepository.isLocationInVietnam(request.getLongitude(), request.getLatitude())) {
//            throw new BusinessException("Vị trí của shop phải nằm trong lãnh thổ Việt Nam");
//        }

        PartnerSubscription subscription = subscriptionMapper.toEntity(request);
        subscription.setPartner(currentPartner);
        subscription.setSubscriptionPlan(plan);

        subscription.setStatus(PartnerSubscriptionStatus.PENDING);

        subscription.setStartDate(null);
        subscription.setEndDate(null);
        subscription = partnerSubscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional
    public PartnerSubscriptionResponse approveSubscription(Long subscriptionId) {
        PartnerSubscription subscription = partnerSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Yêu cầu đăng ký gói không tồn tại"));

        if (!PartnerSubscriptionStatus.PENDING.equals(subscription.getStatus())) {
            throw new BusinessException("Chỉ có thể duyệt gói dịch vụ đang ở trạng thái chờ duyệt");
        }

        subscription.setStatus(PartnerSubscriptionStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        subscription.setStartDate(now);

        if (BillingCycleEnum.MONTHLY.equals(subscription.getBillingCycle())) {
            subscription.setEndDate(now.plusMonths(1));
        } else if (BillingCycleEnum.YEARLY.equals(subscription.getBillingCycle())) {
            subscription.setEndDate(now.plusYears(1));
        }

        subscription = partnerSubscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    public PartnerSubscriptionResponse rejectSubscription(Long subscriptionId) {
        PartnerSubscription subscription = partnerSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Yêu cầu đăng ký gói không tồn tại"));

        if (!PartnerSubscriptionStatus.PENDING.equals(subscription.getStatus())) {
            throw new BusinessException("Chỉ có thể từ chối gói dịch vụ đang ở trạng thái chờ duyệt (PENDING)");
        }

        subscription.setStatus(PartnerSubscriptionStatus.REJECTED);
        subscription = partnerSubscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(subscription);
    }
}