package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.content.dto.filter.TagFilterRequest;
import org.sep490.backend.module.content.dto.request.TagRequest;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.mapper.TagMapper;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.TagService;
import org.sep490.backend.module.content.specification.TagSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TagServiceImpl implements TagService {

    TagRepository tagRepository;
    TagMapper tagMapper;

    @Override
    @Transactional
    public TagResponse create(TagRequest request) {
        if (tagRepository.existsByTagNameIgnoreCase(request.getTagName())) {
            throw new BusinessException("Tag với tên \"" + request.getTagName() + "\" đã tồn tại");
        }
        Tag tag = tagMapper.toEntity(request);
        tag.setTagStatus(TagStatus.ACTIVE);
        tag = tagRepository.save(tag);
        return tagMapper.toResponse(tag);
    }

    @Override
    @Transactional
    public TagResponse update(Long id, TagRequest request) {
        Tag tag = getById(id);
        if (tag.getTagStatus() == TagStatus.INACTIVE) {
            throw new BusinessException("Tag đang bị vô hiệu hóa, không thể cập nhật");
        }
        if (tagRepository.existsByTagNameIgnoreCaseAndTagIdNot(request.getTagName(), id)) {
            throw new BusinessException("Tag với tên \"" + request.getTagName() + "\" đã tồn tại");
        }
        tag.setTagName(request.getTagName().trim());
        tag = tagRepository.save(tag);
        return tagMapper.toResponse(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponse getDetail(Long id) {
        return tagMapper.toResponse(getById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TagResponse> getAllWithFilter(TagFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Specification<Tag> spec = TagSpecification.filterTags(filter.getSearch(), filter.getStatus());
        return tagRepository.findAll(spec, pageable).map(tagMapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Tag tag = getById(id);
        if (tag.getTagStatus() == TagStatus.DELETED) {
            throw new BusinessException("Tag đã bị xóa trước đó");
        }
        tag.setTagStatus(TagStatus.DELETED);
        tagRepository.save(tag);
    }

    @Override
    public Tag getById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tag với id: " + id));
        if (tag.getTagStatus() == TagStatus.DELETED) {
            throw new BusinessException("Tag với id " + id + " đã bị xóa");
        }
        return tag;
    }
}

