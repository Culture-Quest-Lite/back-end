package org.sep490.backend.module.social.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.mapper.PostMapper;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.social.service.PostService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {
    PostRepository postRepository;
    HotspotRepository hotspotRepository;
    RouteRepository routeRepository;
    TagRepository tagRepository;
    PostMapper postMapper;
    UserService userService;

    @Override
    @Transactional
    public PostResponse createPost(PostRequest request) {
        User user = userService.getCurrentUser();

        Post post = postMapper.toEntity(request);
        post.setUser(user);
        post.setStatus(PostStatus.PENDING);

        if (request.getHotspotIds() != null && !request.getHotspotIds().isEmpty()) {
            List<Hotspot> hotspots = hotspotRepository.findAllById(request.getHotspotIds());
            if (hotspots.size() != request.getHotspotIds().size()) {
                throw new BusinessException("Một số địa điểm được tag không tồn tại");
            }
            post.setTaggedHotspots(new HashSet<>(hotspots));
            post.setIsTaggedHotspot(true);
        } else {
            post.setIsTaggedHotspot(false);
        }

        if (request.getRouteIds() != null && !request.getRouteIds().isEmpty()) {
            List<Route> routes = routeRepository.findAllById(request.getRouteIds());
            if (routes.size() != request.getRouteIds().size()) {
                throw new BusinessException("Một số tuyến đường được tag không tồn tại");
            }
            post.setTaggedRoutes(new HashSet<>(routes));
            post.setIsTaggedRoute(true);
        } else {
            post.setIsTaggedRoute(false);
        }

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            if (tags.size() != request.getTagIds().size()) {
                throw new BusinessException("Một số thẻ phân loại (Tag) không tồn tại");
            }
            post.setTags(new HashSet<>(tags));
        }
        post = postRepository.save(post);
        return postMapper.toResponse(post);
    }
}
