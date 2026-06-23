package org.sep490.backend.module.partner.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.gamification.entity.PointTransaction;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.repository.PointTransactionRepository;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.dto.request.VoucherRequest;
import org.sep490.backend.module.partner.dto.response.UserVoucherResponse;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.sep490.backend.module.partner.entity.UserVoucher;
import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.partner.mapper.UserVoucherMapper;
import org.sep490.backend.module.partner.mapper.VoucherMapper;
import org.sep490.backend.module.partner.repository.UserVoucherRepository;
import org.sep490.backend.module.partner.repository.VoucherRepository;
import org.sep490.backend.module.partner.service.VoucherService;
import org.sep490.backend.module.user.service.UserService;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.sep490.backend.module.partner.specification.VoucherSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoucherServiceImpl implements VoucherService {

    VoucherRepository voucherRepository;
    VoucherMapper voucherMapper;
    UserService userService;
    UserVoucherRepository userVoucherRepository;
    UserVoucherMapper userVoucherMapper;
    PointTransactionRepository pointTransactionRepository;

    static SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;

    @Override
    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("Ngày bắt đầu phải trước ngày kết thúc");
        }

        if (voucherRepository.existsByVoucherCode(request.getVoucherCode())) {
            throw new BusinessException("Mã voucher đã tồn tại");
        }

        String generateCode;
        do {
            generateCode = generateRandomHexCode(8);
        } while (voucherRepository.existsByVoucherCode(generateCode));

        User partner = userService.getCurrentUser();

        Voucher voucher = voucherMapper.toEntity(request);
        voucher.setPartner(partner);
        voucher.setVoucherCode(generateCode);
        voucher.setStatus(VoucherStatus.PENDING);
        voucher = voucherRepository.save(voucher);
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Voucher không tồn tại"));

        if (voucher.getStatus() == VoucherStatus.DELETED) {
            throw new BusinessException("Voucher không tồn tại");
        }

        if (!voucher.getVoucherCode().equals(request.getVoucherCode())) {
            throw new BusinessException("Không được phép thay đổi mã voucher");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("Ngày bắt đầu phải trước ngày kết thúc");
        }

        VoucherStatus oldStatus = voucher.getStatus();
        voucherMapper.updateEntityFromRequest(request, voucher);
        if (request.getStatus() == null) {
            voucher.setStatus(oldStatus);
        }
        voucher = voucherRepository.save(voucher);
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Voucher không tồn tại"));
        if (voucher.getStatus() == VoucherStatus.DELETED) {
            throw new BusinessException("Voucher không tồn tại");
        }
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoucherResponse> getVouchers(VoucherFilter filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<Voucher> spec = VoucherSpecification.filterVouchers(filter);
        return voucherRepository.findAll(spec, pageable).map(voucherMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Voucher không tồn tại"));
        if (voucher.getStatus() == VoucherStatus.DELETED) {
            throw new BusinessException("Voucher đã bị xóa trước đó");
        }
        voucher.setStatus(VoucherStatus.DELETED);
        voucherRepository.save(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoucherResponse> getAvailableVouchers(VoucherFilter filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<Voucher> availableSpec = (root, query, cb) -> {
            LocalDateTime now = LocalDateTime.now();
            return cb.and(
                    cb.equal(root.get("status"), VoucherStatus.ACTIVE),
                    cb.greaterThan(root.get("quantityRemaining"), 0),
                    cb.lessThanOrEqualTo(root.get("startDate"), now),
                    cb.greaterThanOrEqualTo(root.get("endDate"), now)
            );
        };
        return voucherRepository.findAll(availableSpec, pageable).map(voucherMapper::toResponse);
    }

    @Override
    @Transactional
    public UserVoucherResponse redeemVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException("Voucher không tồn tại"));

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() == VoucherStatus.DELETED || voucher.getQuantityRemaining() <= 0
                || now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new BusinessException("Voucher này hiện tại không khả dụng để đổi");
        }

        User currentUser = userService.getCurrentUser();

        if (currentUser.getTotalPoints() < voucher.getPointsRequired()) {
            throw new BusinessException("Bạn không đủ số điểm tích lũy để đổi voucher này");
        }

        if (userVoucherRepository.existsByUserUserIdAndVoucherVoucherId(currentUser.getUserId(), voucherId)) {
            throw new BusinessException("Bạn đã đổi voucher này trước đó");
        }
        int updatedUsers = userRepository.deductPoints(currentUser.getUserId(), voucher.getPointsRequired().intValue());
        if (updatedUsers == 0) {
            throw new BusinessException("Giao dịch thất bại do số dư điểm thay đổi. Vui lòng thử lại!");
        }

        int updatedVouchers = voucherRepository.decrementQuantityRemaining(voucherId);
        if (updatedVouchers == 0) {
            throw new BusinessException("Rất tiếc, voucher vừa mới hết số lượng!");
        }

        UserVoucher userVoucher = UserVoucher.builder()
                .user(currentUser)
                .voucher(voucher)
                .voucherCode(voucher.getVoucherCode())
                .pointsRequired(voucher.getPointsRequired())
                .isUsed(false)
                .redeemedAt(LocalDateTime.now())
                .expiredAt(voucher.getEndDate())
                .build();

        userVoucher = userVoucherRepository.save(userVoucher);

        long balanceRemaining = currentUser.getTotalPoints() - voucher.getPointsRequired();
        currentUser.setTotalPoints((int) balanceRemaining);

        PointTransaction pointHistory = PointTransaction.builder()
                .user(currentUser)
                .pointAmount(-voucher.getPointsRequired())
                .transactionType(TransactionType.REDEEM_VOUCHER)
                .description(voucher.getDescription())
                .balanceRemaining(balanceRemaining)
                .referenceId(userVoucher.getUserVoucherId())
                .build();
        pointTransactionRepository.save(pointHistory);

        return userVoucherMapper.toResponse(userVoucher);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserVoucherResponse> getMyRedeemedVouchers(VoucherFilter filter) {
        User currentUser = userService.getCurrentUser();

        String sortBy = filter.getSortBy();
        if ("createdAt".equalsIgnoreCase(sortBy)) {
            sortBy = "redeemedAt";
        }

        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<UserVoucher> userVouchers = userVoucherRepository.findByUser_UserId(currentUser.getUserId(), pageable);
        return userVouchers.map(userVoucherMapper::toResponse);
    }

    @Override
    @Transactional
    public UserVoucherResponse useVoucher(String voucherCode) {
        UserVoucher userVoucher = userVoucherRepository.findByVoucherCode(voucherCode)
                .orElseThrow(() -> new BusinessException("Mã voucher không tồn tại hoặc không hợp lệ"));

        if (Boolean.TRUE.equals(userVoucher.getIsUsed())) {
            throw new BusinessException("Voucher này đã được sử dụng trước đó");
        }

        if (userVoucher.getExpiredAt() != null && LocalDateTime.now().isAfter(userVoucher.getExpiredAt())) {
            throw new BusinessException("Voucher này đã hết hạn sử dụng");
        }

        userVoucher.setIsUsed(true);
        userVoucher.setUsedAt(LocalDateTime.now());
        userVoucher = userVoucherRepository.save(userVoucher);

        return userVoucherMapper.toResponse(userVoucher);
    }

    private String generateRandomHexCode(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append(Integer.toHexString(random.nextInt()).toUpperCase());
        }
        return sb.substring(0, length);
    }
}
