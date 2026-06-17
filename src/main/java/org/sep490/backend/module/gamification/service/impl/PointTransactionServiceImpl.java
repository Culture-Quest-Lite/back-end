package org.sep490.backend.module.gamification.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.gamification.dto.response.PointTransactionResponse;
import org.sep490.backend.module.gamification.entity.PointTransaction;
import org.sep490.backend.module.gamification.mapper.PointTransactionMapper;
import org.sep490.backend.module.gamification.repository.PointTransactionRepository;
import org.sep490.backend.module.gamification.service.PointTransactionService;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PointTransactionServiceImpl implements PointTransactionService {

    UserService userService;
    PointTransactionRepository pointTransactionRepository;
    PointTransactionMapper pointTransactionMapper;


    @Override
    @Transactional(readOnly = true)
    public Page<PointTransactionResponse> getMyPointHistory(VoucherFilter filter) {
        User currentUser = userService.getCurrentUser(); //

        String sortBy = filter.getSortBy().equals("id") ? "createdAt" : filter.getSortBy();
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<PointTransaction> histories = pointTransactionRepository.findByUser_UserId(currentUser.getUserId(), pageable);
        return histories.map(pointTransactionMapper::toResponse);
    }
}
