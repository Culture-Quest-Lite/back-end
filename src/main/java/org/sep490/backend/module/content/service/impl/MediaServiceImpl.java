package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.sep490.backend.module.admin.repository.PartnerInfoRepository;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.MediaType;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.mapper.MediaMapper;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.MediaRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.partner.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaServiceImpl implements MediaService {

    MediaRepository mediaRepository;
    StoryRepository storyRepository;
    HotspotRepository hotspotRepository;
    PostRepository postRepository;
    PartnerInfoRepository partnerInfoRepository;
    VoucherRepository voucherRepository;
    RouteRepository routeRepository;
    S3Service s3Service;
    MediaMapper mediaMapper;

    @Override
    @Transactional
    public List<MediaResponse> uploadAndSaveMedias(MultipartFile[] files, MediaTargetType entityType, Long entityId)
            throws IOException {
        if (files == null || files.length == 0) {
            return List.of();
        }
        int maxOrder = getMaxDisplayOrder(entityType, entityId);
        List<MediaResponse> responses = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            responses.add(uploadAndSaveMedia(files[i], entityType, entityId, maxOrder + 1 + i));
        }
        return responses;
    }

    private int getMaxDisplayOrder(MediaTargetType entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return 0;
        }
        switch (entityType) {
            case STORY:
                return mediaRepository.findMaxDisplayOrderByStoryId(entityId);
            case HOTSPOT:
                return mediaRepository.findMaxDisplayOrderByHotspotId(entityId);
            case POST:
                return mediaRepository.findMaxDisplayOrderByPostId(entityId);
            case PARTNER_SUBSCRIPTION:
                return mediaRepository.findMaxDisplayOrderByPartnerInfoId(entityId);
            case VOUCHER:
                return mediaRepository.findMaxDisplayOrderByVoucherId(entityId);
            case ROUTE:
                return mediaRepository.findMaxDisplayOrderByRouteId(entityId);
            default:
                return 0;
        }
    }

    @Override
    @Transactional
    public void deleteMedia(Long mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException("Media không tồn tại với ID: " + mediaId));
        mediaRepository.delete(media);
    }

    private String determineFolderAndSetEntityRelation(MediaTargetType entityType, Long entityId, Media media) {
        if (entityType == null) {
            throw new BusinessException("Entity type không được null");
        }
        if (entityId == null) {
            throw new BusinessException("Entity ID không được null");
        }
        switch (entityType) {
            case STORY:
                Story story = storyRepository.findById(entityId)
                        .orElseThrow(() -> new BusinessException("Story không tồn tại với ID: " + entityId));
                media.setStory(story);
                return "stories";
            case HOTSPOT:
                Hotspot hotspot = hotspotRepository.findById(entityId)
                        .orElseThrow(() -> new BusinessException("Hotspot không tồn tại với ID: " + entityId));
                media.setHotspot(hotspot);
                return "hotspots";
            case POST:
                Post post = postRepository.findById(entityId)
                        .orElseThrow(() -> new BusinessException("Post không tồn tại với ID: " + entityId));
                media.setPost(post);
                return "posts";
            case PARTNER_SUBSCRIPTION:
                PartnerInfo partnerInfo = partnerInfoRepository.findById(entityId)
                        .orElseThrow(
                                () -> new BusinessException("Thông tin đối tác không tồn tại với ID: " + entityId));
                media.setPartnerInfo(partnerInfo);
                return "partner_subscriptions";
            case VOUCHER:
                Voucher voucher = voucherRepository.findById(entityId)
                        .orElseThrow(() -> new BusinessException("Voucher không tồn tại với ID: " + entityId));
                media.setVoucher(voucher);
                return "vouchers";
            case ROUTE:
                Route route = routeRepository.findById(entityId)
                        .orElseThrow(() -> new BusinessException("Route không tồn tại với ID: " + entityId));
                media.setRoute(route);
                return "routes";
            default:
                throw new BusinessException("Không hỗ trợ loại thực thể: " + entityType);
        }
    }

    private MediaType determineMediaType(String mimeType) {
        if (mimeType == null) {
            return MediaType.OTHER;
        }
        String mime = mimeType.toLowerCase();
        if (mime.startsWith("image/")) {
            return MediaType.IMAGE;
        } else if (mime.startsWith("audio/")) {
            return MediaType.AUDIO;
        } else if (mime.startsWith("video/")) {
            return MediaType.VIDEO;
        }
        return MediaType.OTHER;
    }

    private static final double MAX_VIDEO_SIZE_MB = 200.0;
    private static final double MAX_IMAGE_SIZE_MB = 1.0;
    private static final double MAX_AUDIO_SIZE_MB = 20.0;

    private void validateFileSize(MediaType mediaType, double fileSizeMb, String fileName) {
        switch (mediaType) {
            case VIDEO:
                if (fileSizeMb > MAX_VIDEO_SIZE_MB) {
                    throw new BusinessException(
                            String.format("Video '%s' vượt quá dung lượng cho phép (%.1fMB). Tối đa: %.0fMB",
                                    fileName, fileSizeMb, MAX_VIDEO_SIZE_MB));
                }
                break;
            case IMAGE:
                if (fileSizeMb > MAX_IMAGE_SIZE_MB) {
                    throw new BusinessException(
                            String.format("Ảnh '%s' vượt quá dung lượng cho phép (%.1fMB). Tối đa: %.0fMB",
                                    fileName, fileSizeMb, MAX_IMAGE_SIZE_MB));
                }
                break;
            case AUDIO:
                if (fileSizeMb > MAX_AUDIO_SIZE_MB) {
                    throw new BusinessException(
                            String.format("Audio '%s' vượt quá dung lượng cho phép (%.1fMB). Tối đa: %.0fMB",
                                    fileName, fileSizeMb, MAX_AUDIO_SIZE_MB));
                }
                break;
            default:
                break;
        }
    }

    private String getSafeFileName(String originalFilename) {
        if (originalFilename == null) {
            return "file";
        }
        String cleaned = originalFilename.replaceAll("\\s+", "_");
        if (cleaned.length() > 50) {
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot != -1 && cleaned.length() - lastDot < 10) {
                String ext = cleaned.substring(lastDot);
                cleaned = cleaned.substring(0, 50 - ext.length()) + ext;
            } else {
                cleaned = cleaned.substring(0, 50);
            }
        }
        return cleaned;
    }

    private MediaResponse uploadAndSaveMedia(MultipartFile file, MediaTargetType entityType, Long entityId,
            int displayOrder)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File không được trống");
        }

        String mimeType = file.getContentType();
        double fileSizeMb = (double) file.getSize() / (1024 * 1024);
        MediaType mediaType = determineMediaType(mimeType);

        validateFileSize(mediaType, fileSizeMb, file.getOriginalFilename());

        Media media = new Media();
        String folder = determineFolderAndSetEntityRelation(entityType, entityId, media);

        String fileUrl = s3Service.uploadFile(file, folder);

        media.setFileUrl(fileUrl);
        media.setFileName(getSafeFileName(file.getOriginalFilename()));
        media.setFileSize(fileSizeMb);
        media.setMimeType(mimeType != null && mimeType.length() > 30 ? mimeType.substring(0, 30) : mimeType);
        media.setMediaType(mediaType);
        media.setDisplayOrder(displayOrder);

        media = mediaRepository.save(media);
        return mediaMapper.toResponse(media);
    }
}