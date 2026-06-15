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

        LocalDateTime now = LocalDateTime.now();
        subscription.setStartDate(now);
        if (BillingCycleEnum.MONTHLY.equals(request.getBillingCycle())) {
            subscription.setEndDate(now.plusMonths(1));
        } else if (BillingCycleEnum.YEARLY.equals(request.getBillingCycle())) {
            subscription.setEndDate(now.plusYears(1));
        }

        subscription.setStatus(PartnerSubscriptionStatus.ACTIVE);
        subscription = partnerSubscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(subscription);
    }
}
