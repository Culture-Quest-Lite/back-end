package org.sep490.backend.module.gamification.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.gamification.dto.request.XpHistoryRequest;
import org.sep490.backend.module.gamification.entity.XpHistory;
import org.sep490.backend.module.gamification.mapper.XpHistoryMapper;
import org.sep490.backend.module.gamification.repository.XpHistoryRepository;
import org.sep490.backend.module.gamification.service.XpHistoryService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class XpHistoryServiceImpl implements XpHistoryService {

    XpHistoryRepository xpHistoryRepository;
    XpHistoryMapper xpHistoryMapper;
    UserService userService;
    UserRepository userRepository;

    @Override
    public void create(XpHistoryRequest request) {

        User currUser = userService.getUserById(request.getUserId());
        XpHistory currXH = xpHistoryRepository
                .findFirstByUser_UserIdOrderByCreatedAtDesc(currUser.getUserId()).orElse(null);

        if(currXH != null) {
            if(currUser.getTotalXp() != Math.toIntExact(currXH.getBalanceAfter())) {
                throw new BusinessException("Tổng số kinh nghiệm không khớp");
            }
        }

        Long balanceAfter = currUser.getTotalXp() + request.getXpAmount();

        currUser.setTotalXp(Math.toIntExact(balanceAfter));
        userRepository.save(currUser);

        XpHistory xpHistory = xpHistoryMapper.toEntity(request, currUser, balanceAfter);
        xpHistory.setUser(currUser);

        xpHistoryRepository.save(xpHistory);
    }
}
