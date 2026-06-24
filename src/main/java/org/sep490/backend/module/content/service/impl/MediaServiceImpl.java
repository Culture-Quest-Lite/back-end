package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.admin.entity.PartnerSubscription;
import org.sep490.backend.module.admin.repository.PartnerSubscriptionRepository;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.enums.MediaType;
import org.sep490.backend.module.content.enums.MediaTargetType;
import org.sep490.backend.module.content.mapper.MediaMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.MediaRepository;
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
import java.io.InputStream;
import java.io.BufferedInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
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
    PartnerSubscriptionRepository partnerSubscriptionRepository;
    VoucherRepository voucherRepository;
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
                return mediaRepository.findMaxDisplayOrderByPartnerSubscriptionId(entityId);
            case VOUCHER:
                return mediaRepository.findMaxDisplayOrderByVoucherId(entityId);
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
                PartnerSubscription subscription = partnerSubscriptionRepository.findById(entityId)
                        .orElseThrow(
                                () -> new BusinessException("Gói đăng ký đối tác không tồn tại với ID: " + entityId));
                media.setPartnerSubscription(subscription);
                return "partner_subscriptions";
            case VOUCHER:
                Voucher voucher = voucherRepository.findById(entityId)
                        .orElseThrow(() -> new BusinessException("Voucher không tồn tại với ID: " + entityId));
                media.setVoucher(voucher);
                return "vouchers";
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

        Media media = new Media();
        String folder = determineFolderAndSetEntityRelation(entityType, entityId, media);

        String fileUrl = s3Service.uploadFile(file, folder);

        String mimeType = file.getContentType();
        double fileSizeMb = (double) file.getSize() / (1024 * 1024);
        MediaType mediaType = determineMediaType(mimeType);

        media.setFileUrl(fileUrl);
        media.setFileName(getSafeFileName(file.getOriginalFilename()));
        media.setFileSize(fileSizeMb);
        media.setMimeType(mimeType != null && mimeType.length() > 30 ? mimeType.substring(0, 30) : mimeType);
        media.setMediaType(mediaType);
        media.setDisplayOrder(displayOrder);

        if (mediaType == MediaType.AUDIO) {
            media.setDuration(estimateAudioDuration(file));
        }

        media = mediaRepository.save(media);
        return mediaMapper.toResponse(media);
    }

    private boolean skipFully(InputStream is, long n) throws IOException {
        long totalSkipped = 0;
        while (totalSkipped < n) {
            long skipped = is.skip(n - totalSkipped);
            if (skipped == 0) {
                if (is.read() == -1) {
                    return false;
                }
                totalSkipped++;
            } else {
                totalSkipped += skipped;
            }
        }
        return true;
    }

    private Double estimateAudioDuration(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return null;
        }
        String mime = contentType.toLowerCase();
        if (!mime.startsWith("audio/")) {
            return null;
        }

        Double m4aDuration = parseMp4Duration(file);
        if (m4aDuration != null) {
            return m4aDuration;
        }

        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(is);
            AudioFormat format = audioInputStream.getFormat();
            long frames = audioInputStream.getFrameLength();
            if (frames > 0 && format.getFrameRate() > 0) {
                return (double) frames / format.getFrameRate();
            }
        } catch (Exception e) {
            // Ignore
        }

        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            byte[] header = new byte[10];
            if (is.read(header) != 10) {
                return null;
            }

            int id3Size = 0;
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                int sizeByte0 = header[6] & 0x7F;
                int sizeByte1 = header[7] & 0x7F;
                int sizeByte2 = header[8] & 0x7F;
                int sizeByte3 = header[9] & 0x7F;
                id3Size = (sizeByte0 << 21) | (sizeByte1 << 14) | (sizeByte2 << 7) | sizeByte3;
                id3Size += 10;
            }

            long fileSize = file.getSize();
            long audioDataSize = fileSize - id3Size;
            if (audioDataSize <= 0) {
                return null;
            }

            try (InputStream ais = new BufferedInputStream(file.getInputStream())) {
                if (!skipFully(ais, id3Size)) {
                    return null;
                }

                byte[] frameHeader = new byte[4];
                int readBytes = ais.read(frameHeader);
                if (readBytes == 4) {
                    if ((frameHeader[0] & 0xFF) == 0xFF && (frameHeader[1] & 0xE0) == 0xE0) {
                        int mpegVersion = (frameHeader[1] & 0x18) >> 3;
                        int layer = (frameHeader[1] & 0x06) >> 1;
                        int bitrateIndex = (frameHeader[2] & 0xF0) >> 4;

                        int bitrate = getMp3Bitrate(mpegVersion, layer, bitrateIndex);
                        if (bitrate > 0) {
                            return (double) (audioDataSize * 8) / (bitrate * 1000);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    private int getMp3Bitrate(int mpegVersion, int layer, int bitrateIndex) {
        if (bitrateIndex <= 0 || bitrateIndex >= 15) {
            return 0;
        }
        if (mpegVersion == 3) {
            if (layer == 1) {
                int[] bitrates = { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320 };
                return bitrates[bitrateIndex];
            }
        } else if (mpegVersion == 2) {
            if (layer == 1) {
                int[] bitrates = { 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160 };
                return bitrates[bitrateIndex];
            }
        }
        return 128;
    }

    private Double parseMp4Duration(MultipartFile file) {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            byte[] target = { 0x6D, 0x76, 0x68, 0x64 }; // "mvhd"
            int targetLen = target.length;
            int matched = 0;
            int nextByte;

            while ((nextByte = is.read()) != -1) {
                if (nextByte == (target[matched] & 0xFF)) {
                    matched++;
                    if (matched == targetLen) {
                        // Found "mvhd"!
                        int version = is.read();
                        if (version == -1)
                            return null;

                        if (!skipFully(is, 3))
                            return null;

                        long timescale = 0;
                        long duration = 0;

                        if (version == 0) {
                            if (!skipFully(is, 8))
                                return null;

                            byte[] tsBuf = new byte[4];
                            if (is.read(tsBuf) != 4)
                                return null;
                            timescale = ((tsBuf[0] & 0xFFL) << 24) |
                                    ((tsBuf[1] & 0xFFL) << 16) |
                                    ((tsBuf[2] & 0xFFL) << 8) |
                                    (tsBuf[3] & 0xFFL);

                            byte[] durBuf = new byte[4];
                            if (is.read(durBuf) != 4)
                                return null;
                            duration = ((durBuf[0] & 0xFFL) << 24) |
                                    ((durBuf[1] & 0xFFL) << 16) |
                                    ((durBuf[2] & 0xFFL) << 8) |
                                    (durBuf[3] & 0xFFL);
                        } else if (version == 1) {
                            if (!skipFully(is, 16))
                                return null;

                            byte[] tsBuf = new byte[4];
                            if (is.read(tsBuf) != 4)
                                return null;
                            timescale = ((tsBuf[0] & 0xFFL) << 24) |
                                    ((tsBuf[1] & 0xFFL) << 16) |
                                    ((tsBuf[2] & 0xFFL) << 8) |
                                    (tsBuf[3] & 0xFFL);

                            byte[] durBuf = new byte[8];
                            if (is.read(durBuf) != 8)
                                return null;
                            duration = ((durBuf[0] & 0xFFL) << 56) |
                                    ((durBuf[1] & 0xFFL) << 48) |
                                    ((durBuf[2] & 0xFFL) << 40) |
                                    ((durBuf[3] & 0xFFL) << 32) |
                                    ((durBuf[4] & 0xFFL) << 24) |
                                    ((durBuf[5] & 0xFFL) << 16) |
                                    ((durBuf[6] & 0xFFL) << 8) |
                                    (durBuf[7] & 0xFFL);
                        } else {
                            return null;
                        }

                        if (timescale > 0) {
                            return (double) duration / timescale;
                        }
                        return null;
                    }
                } else {
                    if (nextByte == (target[0] & 0xFF)) {
                        matched = 1;
                    } else {
                        matched = 0;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}