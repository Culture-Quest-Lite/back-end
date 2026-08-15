package org.sep490.backend.module.notification.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.notification.dto.response.NotificationResponse;
import org.sep490.backend.module.notification.entity.Notification;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.mapper.NotificationMapper;
import org.sep490.backend.module.notification.repository.NotificationRepository;
import org.sep490.backend.module.notification.service.FcmService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho THÔNG BÁO trong ứng dụng (lưu DB, đẩy FCM, đếm chưa đọc có cache).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private FcmService fcmService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RedisCircuitBreaker circuitBreaker;

    @InjectMocks private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());
    }

    private static User user(Long userId, String displayName, String fcmToken) {
        User user = new User();
        user.setUserId(userId);
        user.setDisplayName(displayName);
        user.setFcmToken(fcmToken);
        return user;
    }

    private static Notification notification(Long id, User owner, boolean isRead) {
        return Notification.builder()
                .notificationId(id)
                .user(owner)
                .title("Tương tác mới trên bài viết")
                .message("Minh Anh đã tương tác với bài viết của bạn.")
                .notificationType(NotificationType.POST)
                .referenceId(500L)
                .isRead(isRead)
                .build();
    }

    // =====================================================================
    // Function: sendAndSave
    // =====================================================================
    @Nested
    @DisplayName("sendAndSave")
    class SendAndSaveTest {

        // UTCID01 - Normal: lưu thông báo chưa đọc vào DB với đủ nội dung
        @Test
        void sendAndSave_validInput_savesUnreadNotification() {
            User receiver = user(1L, "Minh Anh", "fcm-token-01");

            notificationService.sendAndSave(receiver, "Bạn có bình luận mới",
                    "Hoàng đã bình luận bài viết của bạn", NotificationType.POST, 500L);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals("Bạn có bình luận mới", captor.getValue().getTitle());
            assertEquals(NotificationType.POST, captor.getValue().getNotificationType());
            assertEquals(500L, captor.getValue().getReferenceId());
            assertFalse(captor.getValue().getIsRead());
            assertNotNull(captor.getValue().getCreatedAt());
        }

        // UTCID02 - Normal: đẩy push notification qua FCM bằng token của người nhận
        @Test
        void sendAndSave_validInput_pushesToFcmToken() {
            User receiver = user(1L, "Minh Anh", "fcm-token-01");

            notificationService.sendAndSave(receiver, "Bạn có bình luận mới",
                    "Hoàng đã bình luận bài viết của bạn", NotificationType.POST, 500L);

            verify(fcmService).sendPushNotification("fcm-token-01", "Bạn có bình luận mới",
                    "Hoàng đã bình luận bài viết của bạn", NotificationType.POST, 500L);
        }

        // UTCID03 - Normal: xóa cache số thông báo chưa đọc để badge cập nhật ngay
        @Test
        void sendAndSave_validInput_evictsUnreadCountCache() {
            User receiver = user(1L, "Minh Anh", "fcm-token-01");

            notificationService.sendAndSave(receiver, "Bạn có bình luận mới",
                    "Hoàng đã bình luận bài viết của bạn", NotificationType.POST, 500L);

            verify(redisTemplate).delete("notif:unread:1");
        }

        // UTCID04 - Boundary: người nhận chưa đăng ký thiết bị (fcmToken null) -> vẫn lưu DB
        @Test
        void sendAndSave_nullFcmToken_stillSavesToDatabase() {
            User receiver = user(1L, "Minh Anh", null);

            notificationService.sendAndSave(receiver, "Bạn có bình luận mới",
                    "Hoàng đã bình luận bài viết của bạn", NotificationType.POST, 500L);

            verify(notificationRepository).save(any(Notification.class));
            verify(fcmService).sendPushNotification(isNull(), anyString(), anyString(), any(), anyLong());
        }
    }

    // =====================================================================
    // Function: markAsRead
    // =====================================================================
    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTest {

        // UTCID01 - Abnormal: thông báo không tồn tại -> báo lỗi
        @Test
        void markAsRead_notificationNotFound_throwsNotFound() {
            when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.markAsRead(1L, 99L));

            assertEquals("Thông báo không tồn tại", ex.getMessage());
            verify(notificationRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: đọc thông báo của người khác -> chặn vì không sở hữu
        @Test
        void markAsRead_notOwner_throwsNoPermission() {
            Notification target = notification(10L, user(2L, "Minh Anh", "fcm-01"), false);
            when(notificationRepository.findById(10L)).thenReturn(Optional.of(target));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.markAsRead(1L, 10L));

            assertEquals("Bạn không có quyền sở hữu thông báo này", ex.getMessage());
            verify(notificationRepository, never()).save(any());
        }

        // UTCID03 - Normal: đúng chủ sở hữu -> đánh dấu đã đọc và ghi thời điểm đọc
        @Test
        void markAsRead_owner_marksReadWithTimestamp() {
            Notification target = notification(10L, user(1L, "Minh Anh", "fcm-01"), false);
            when(notificationRepository.findById(10L)).thenReturn(Optional.of(target));

            notificationService.markAsRead(1L, 10L);

            assertTrue(target.getIsRead());
            assertNotNull(target.getReadAt());
            verify(notificationRepository).save(target);
        }

        // UTCID04 - Normal: đọc xong phải xóa cache đếm chưa đọc
        @Test
        void markAsRead_owner_evictsUnreadCountCache() {
            Notification target = notification(10L, user(1L, "Minh Anh", "fcm-01"), false);
            when(notificationRepository.findById(10L)).thenReturn(Optional.of(target));

            notificationService.markAsRead(1L, 10L);

            verify(redisTemplate).delete("notif:unread:1");
        }
    }

    // =====================================================================
    // Function: countUnread
    // =====================================================================
    @Nested
    @DisplayName("countUnread")
    class CountUnreadTest {

        // UTCID01 - Normal: cache có sẵn -> trả từ cache, KHÔNG truy vấn DB
        @Test
        void countUnread_cacheHit_returnsFromCacheWithoutDbQuery() {
            when(valueOps.get("notif:unread:1")).thenReturn("7");

            assertEquals(7L, notificationService.countUnread(1L));

            verify(notificationRepository, never()).countByUser_UserIdAndIsReadFalse(anyLong());
        }

        // UTCID02 - Normal: cache trống -> đếm từ DB rồi ghi cache TTL 5 phút
        @Test
        void countUnread_cacheMiss_queriesDbAndWritesCache() {
            when(valueOps.get("notif:unread:1")).thenReturn(null);
            when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(12L);

            assertEquals(12L, notificationService.countUnread(1L));

            verify(valueOps).set("notif:unread:1", "12", Duration.ofMinutes(5));
        }

        // UTCID03 - Boundary: không còn thông báo chưa đọc -> trả 0 và vẫn cache lại
        @Test
        void countUnread_noUnread_returnsZeroAndCaches() {
            when(valueOps.get("notif:unread:1")).thenReturn(null);
            when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(0L);

            assertEquals(0L, notificationService.countUnread(1L));

            verify(valueOps).set("notif:unread:1", "0", Duration.ofMinutes(5));
        }

        // UTCID04 - Abnormal: Redis chết (circuit breaker mở) -> fallback về DB, không ném lỗi
        @Test
        void countUnread_redisDown_fallsBackToDatabase() {
            doReturn(null).when(circuitBreaker).read(anyString(), any(), any());
            when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(3L);

            assertEquals(3L, notificationService.countUnread(1L));
        }

        // UTCID05 - Abnormal: userId = null -> chặn trước khi dựng khóa cache
        @Test
        void countUnread_nullUserId_throwsUserNotIdentified() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.countUnread(null));

            assertEquals("Không xác định được người dùng", ex.getMessage());
            verify(notificationRepository, never()).countByUser_UserIdAndIsReadFalse(any());
        }
    }

    // =====================================================================
    // Function: updateDeviceToken
    // =====================================================================
    @Nested
    @DisplayName("updateDeviceToken")
    class UpdateDeviceTokenTest {

        // UTCID01 - Abnormal: user không tồn tại -> báo lỗi
        @Test
        void updateDeviceToken_userNotFound_throwsUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.updateDeviceToken(99L, "fcm-token-new"));

            assertEquals("User không tồn tại", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        // UTCID02 - Normal: cập nhật token thiết bị mới sau khi đăng nhập lại
        @Test
        void updateDeviceToken_existingUser_updatesToken() {
            User target = user(1L, "Minh Anh", "fcm-token-cu");
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));

            notificationService.updateDeviceToken(1L, "fcm-token-moi");

            assertEquals("fcm-token-moi", target.getFcmToken());
            verify(userRepository).save(target);
        }

        // UTCID03 - Boundary: đăng xuất gửi token null -> xóa token để ngừng nhận push
        @Test
        void updateDeviceToken_nullToken_clearsToken() {
            User target = user(1L, "Minh Anh", "fcm-token-cu");
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));

            notificationService.updateDeviceToken(1L, null);

            assertNull(target.getFcmToken());
            verify(userRepository).save(target);
        }
    }

    // =====================================================================
    // Function: sendOrUpdateInteractionNotification
    // =====================================================================
    @Nested
    @DisplayName("sendOrUpdateInteractionNotification")
    class SendOrUpdateInteractionNotificationTest {

        // UTCID01 - Boundary: tự tương tác bài viết của chính mình -> không tạo thông báo
        @Test
        void sendOrUpdateInteraction_selfInteraction_doesNothing() {
            User self = user(1L, "Minh Anh", "fcm-01");

            notificationService.sendOrUpdateInteractionNotification(self, self, 500L, 1L);

            verifyNoInteractions(notificationRepository);
            verifyNoInteractions(fcmService);
        }

        // UTCID02 - Normal: chưa có thông báo chưa đọc -> tạo mới và đẩy FCM
        @Test
        void sendOrUpdateInteraction_noExistingNotification_createsNew() {
            User sender = user(1L, "Minh Anh", "fcm-01");
            User receiver = user(2L, "Hoàng", "fcm-02");
            when(notificationRepository
                    .findFirstByUser_UserIdAndNotificationTypeAndReferenceIdAndIsReadFalseOrderByCreatedAtDesc(
                            2L, NotificationType.POST, 500L)).thenReturn(Optional.empty());

            notificationService.sendOrUpdateInteractionNotification(sender, receiver, 500L, 1L);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals("Minh Anh đã tương tác với bài viết của bạn.", captor.getValue().getMessage());
            verify(fcmService).sendPushNotification(eq("fcm-02"), eq("Tương tác mới trên bài viết"),
                    anyString(), eq(NotificationType.POST), eq(500L));
        }

        // UTCID03 - Normal: đã có thông báo chưa đọc -> gộp nội dung, KHÔNG spam push mới
        @Test
        void sendOrUpdateInteraction_existingUnread_mergesWithoutNewPush() {
            User sender = user(1L, "Minh Anh", "fcm-01");
            User receiver = user(2L, "Hoàng", "fcm-02");
            Notification existing = notification(10L, receiver, false);
            when(notificationRepository
                    .findFirstByUser_UserIdAndNotificationTypeAndReferenceIdAndIsReadFalseOrderByCreatedAtDesc(
                            2L, NotificationType.POST, 500L)).thenReturn(Optional.of(existing));

            notificationService.sendOrUpdateInteractionNotification(sender, receiver, 500L, 5L);

            assertEquals("Minh Anh và 4 người khác đã tương tác với bài viết của bạn.",
                    existing.getMessage());
            verify(notificationRepository).save(existing);
            verifyNoInteractions(fcmService);
        }

        // UTCID04 - Boundary: đúng 2 lượt tương tác -> nội dung hiển thị "và 1 người khác"
        @Test
        void sendOrUpdateInteraction_twoInteractions_showsOneOtherPerson() {
            User sender = user(1L, "Minh Anh", "fcm-01");
            User receiver = user(2L, "Hoàng", "fcm-02");
            Notification existing = notification(10L, receiver, false);
            when(notificationRepository
                    .findFirstByUser_UserIdAndNotificationTypeAndReferenceIdAndIsReadFalseOrderByCreatedAtDesc(
                            anyLong(), any(), anyLong())).thenReturn(Optional.of(existing));

            notificationService.sendOrUpdateInteractionNotification(sender, receiver, 500L, 2L);

            assertEquals("Minh Anh và 1 người khác đã tương tác với bài viết của bạn.",
                    existing.getMessage());
        }

        // UTCID05 - Boundary: totalInteractions = 0 (dữ liệu chưa kịp cập nhật) -> vẫn dùng câu số ít
        @Test
        void sendOrUpdateInteraction_zeroInteractions_usesSingularMessage() {
            User sender = user(1L, "Minh Anh", "fcm-01");
            User receiver = user(2L, "Hoàng", "fcm-02");
            Notification existing = notification(10L, receiver, false);
            when(notificationRepository
                    .findFirstByUser_UserIdAndNotificationTypeAndReferenceIdAndIsReadFalseOrderByCreatedAtDesc(
                            anyLong(), any(), anyLong())).thenReturn(Optional.of(existing));

            notificationService.sendOrUpdateInteractionNotification(sender, receiver, 500L, 0L);

            assertEquals("Minh Anh đã tương tác với bài viết của bạn.", existing.getMessage());
        }
    }

    // =====================================================================
    // Function: sendToMultipleUsers
    // =====================================================================
    @Nested
    @DisplayName("sendToMultipleUsers")
    class SendToMultipleUsersTest {

        // UTCID01 - Normal: 3 thành viên nhóm -> lưu batch 3 bản ghi, mỗi người 1 thông báo
        @Test
        void sendToMultipleUsers_threeUsers_savesThreeNotifications() {
            List<User> members = List.of(
                    user(1L, "Minh Anh", "fcm-01"),
                    user(2L, "Hoàng", "fcm-02"),
                    user(3L, "Lan", "fcm-03"));

            notificationService.sendToMultipleUsers(members, "Nhóm đã hoàn thành tuyến",
                    "Chúc mừng nhóm đã hoàn thành tuyến Phố cổ Hà Nội",
                    NotificationType.GROUP, 300L);

            ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationRepository).saveAll(captor.capture());
            assertEquals(3, captor.getValue().size());
            assertTrue(captor.getValue().stream().noneMatch(Notification::getIsRead));
        }

        // UTCID02 - Normal: đẩy push cho từng thành viên
        @Test
        void sendToMultipleUsers_threeUsers_pushesToEachToken() {
            List<User> members = List.of(
                    user(1L, "Minh Anh", "fcm-01"),
                    user(2L, "Hoàng", "fcm-02"),
                    user(3L, "Lan", "fcm-03"));

            notificationService.sendToMultipleUsers(members, "Nhóm đã hoàn thành tuyến",
                    "Chúc mừng nhóm", NotificationType.GROUP, 300L);

            verify(fcmService, times(3))
                    .sendPushNotification(anyString(), anyString(), anyString(), any(), anyLong());
            verify(fcmService).sendPushNotification(eq("fcm-02"), anyString(), anyString(), any(), anyLong());
        }

        // UTCID03 - Boundary: danh sách rỗng -> lưu batch rỗng, không gửi push nào
        @Test
        void sendToMultipleUsers_emptyList_savesNothingAndNoPush() {
            notificationService.sendToMultipleUsers(List.of(), "Tiêu đề", "Nội dung",
                    NotificationType.GROUP, 300L);

            verify(notificationRepository).saveAll(List.of());
            verifyNoInteractions(fcmService);
        }
    }

    // =====================================================================
    // Function: getMyNotification / updateUnReadNotification / deleteReadNotification
    // =====================================================================
    @Nested
    @DisplayName("getMyNotification")
    class GetMyNotificationTest {

        // UTCID01 - Normal: trả về danh sách đã map, mới nhất trước
        @Test
        void getMyNotification_hasRecords_returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(
                            notification(1L, user(1L, "Minh Anh", "fcm-01"), false),
                            notification(2L, user(1L, "Minh Anh", "fcm-01"), true)), pageable, 2));
            when(notificationMapper.toResponse(any())).thenReturn(new NotificationResponse());

            Page<NotificationResponse> result = notificationService.getMyNotification(1L, pageable);

            assertEquals(2, result.getTotalElements());
        }

        // UTCID02 - Boundary: user chưa có thông báo nào -> trang rỗng
        @Test
        void getMyNotification_noRecords_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(1L, pageable))
                    .thenReturn(Page.empty());

            assertTrue(notificationService.getMyNotification(1L, pageable).isEmpty());
        }

        // UTCID03 - Normal: job dọn dẹp đánh dấu đã đọc mọi thông báo quá 30 ngày
        @Test
        void updateUnReadNotification_marksOldNotificationsAsRead() {
            Notification old1 = notification(1L, user(1L, "Minh Anh", "fcm-01"), false);
            Notification old2 = notification(2L, user(1L, "Minh Anh", "fcm-01"), false);
            when(notificationRepository.findByIsReadAndCreatedAtBefore(eq(false), any()))
                    .thenReturn(List.of(old1, old2));

            notificationService.updateUnReadNotification();

            assertTrue(old1.getIsRead());
            assertTrue(old2.getIsRead());
            verify(notificationRepository).saveAll(List.of(old1, old2));
        }

        // UTCID04 - Normal: job dọn dẹp xóa hẳn thông báo đã đọc quá 90 ngày
        @Test
        void deleteReadNotification_removesOldReadNotifications() {
            List<Notification> stale = List.of(
                    notification(1L, user(1L, "Minh Anh", "fcm-01"), true));
            when(notificationRepository.findByIsReadAndReadAtBefore(eq(true), any())).thenReturn(stale);

            notificationService.deleteReadNotification();

            verify(notificationRepository).deleteAll(stale);
        }

        // UTCID05 - Abnormal: userId = null -> không xác định được người dùng
        @Test
        void getMyNotification_nullUserId_throwsUserNotIdentified() {
            Pageable pageable = PageRequest.of(0, 10);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.getMyNotification(null, pageable));

            assertEquals("Không xác định được người dùng", ex.getMessage());
            verify(notificationRepository, never())
                    .findByUser_UserIdOrderByCreatedAtDesc(any(), any());
        }

        // UTCID06 - Abnormal: pageable = null -> thiếu thông tin phân trang
        @Test
        void getMyNotification_nullPageable_throwsMissingPageable() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.getMyNotification(1L, null));

            assertEquals("Thiếu thông tin phân trang", ex.getMessage());
            verify(notificationRepository, never())
                    .findByUser_UserIdOrderByCreatedAtDesc(any(), any());
        }

        // UTCID07 - Boundary: size = 101 -> vượt trần 100 bản ghi mỗi trang
        @Test
        void getMyNotification_sizeOverLimit_throwsSizeExceeded() {
            Pageable pageable = PageRequest.of(0, 101);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> notificationService.getMyNotification(1L, pageable));

            assertEquals("Kích thước trang không được vượt quá 100", ex.getMessage());
            verify(notificationRepository, never())
                    .findByUser_UserIdOrderByCreatedAtDesc(any(), any());
        }
    }
}
