package org.sep490.backend.module.content.entity.enumeration;

/**
 * Các thực thể có gallery nhiều file trong bảng medias.
 * Ảnh đơn (tag, voucher, route) dùng cột image_url qua EntityImageService, không nằm ở đây.
 */
public enum MediaTargetType {
    STORY,
    HOTSPOT,
    POST,
    PARTNER_SUBSCRIPTION,
    REVIEW
}

