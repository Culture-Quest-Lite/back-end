package org.sep490.backend.module.partner.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.dto.request.VoucherRequest;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.partner.mapper.VoucherMapper;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoucherServiceImpl implements VoucherService {

    VoucherRepository voucherRepository;
    VoucherMapper voucherMapper;
    UserService userService;

    static SecureRandom random = new SecureRandom();

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

        voucherMapper.updateEntityFromRequest(request, voucher);
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

    private String generateRandomHexCode(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append(Integer.toHexString(random.nextInt()).toUpperCase());
        }
        return sb.substring(0, length);
    }
}
