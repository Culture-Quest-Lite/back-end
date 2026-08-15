package org.sep490.backend.module.social.service.impl;

import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.user.repository.UserFollowRepository;
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
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.service.RewardTransactionService;
import org.sep490.backend.module.social.dto.request.CommentRequest;
import org.sep490.backend.module.social.dto.request.DeletePostRequest;
import org.sep490.backend.module.social.dto.request.RejectPostRequest;
import org.sep490.backend.module.social.dto.request.ShareRequest;
import org.sep490.backend.module.social.dto.request.UpdatePostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.entity.PostAction;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;
import org.sep490.backend.module.social.mapper.PostMapper;
import org.sep490.backend.module.social.repository.PostActionRepository;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.social.service.PostCounterService;
import org.sep490.backend.module.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho BÀI VIẾT (Social Post): sửa, kiểm duyệt, bình luận, chia sẻ.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceImplTest {

    @Mock private PostRepository postRepository;
    @Mock private PostActionRepository postActionRepository;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PostMapper postMapper;
    @Mock private UserService userService;
    @Mock private RewardTransactionService rewardTransactionService;
    @Mock private MediaService mediaService;
    @Mock private S3Service s3Service;
    @Mock private TransactionCompensationService txCompensation;
    @Mock private AuditLogService auditLogService;
    @Mock private PostCounterService postCounterService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserFollowRepository userFollowRepository;

    @InjectMocks private PostServiceImpl postService;

    private static User user(Long userId, String username) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setDisplayName(switch (username) {
            case "tacgia" -> "Tran Minh Anh";
            case "nguoikhac" -> "Le Hoang Nam";
            case "nguoibinhluan" -> "Pham Van Long";
            case "nguoichiase" -> "Vo Thi Mai";
            default -> "Nguoi dung " + userId;
        });
        user.setEmail(username + "@gmail.com");
        return user;
    }

    private static Post post(Long postId, User author, PostStatus status) {
        return Post.builder()
                .postId(postId)
                .user(author)
                .content("Hôm nay mình đi Đà Lạt")
                .visibility(PostVisibility.PUBLIC)
                .status(status)
                .medias(new ArrayList<>())
                .postActions(new ArrayList<>())
                .build();
    }

    /** postMapper.toResponse trả về response rỗng để toResponseWithLiked chạy được. */
    private void stubMapper() {
        when(postMapper.toResponse(any(Post.class))).thenAnswer(inv -> new PostResponse());
    }

    // =====================================================================
    // Function: updatePost
    // =====================================================================
    @Nested
    @DisplayName("updatePost")
    class UpdatePostTest {

        private static UpdatePostRequest updateRequest(String content) {
            UpdatePostRequest request = new UpdatePostRequest();
            request.setContent(content);
            return request;
        }

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void updatePost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.updatePost(1L, updateRequest("Nội dung mới")));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
            verify(postRepository, never()).saveAndFlush(any());
        }

        // UTCID02 - Abnormal: sửa bài viết của người khác
        @Test
        void updatePost_notOwner_throwsNoPermission() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.APPROVED)));
            when(userService.getCurrentUser()).thenReturn(user(2L, "nguoikhac"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.updatePost(1L, updateRequest("Nội dung mới")));

            assertEquals("Bạn không có quyền chỉnh sửa bài viết của người khác", ex.getMessage());
            verify(postRepository, never()).saveAndFlush(any());
        }

        // UTCID03 - Abnormal: gắn thẻ địa điểm không tồn tại
        @Test
        void updatePost_taggedHotspotNotFound_throwsHotspotNotFound() {
            User author = user(1L, "tacgia");
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, author, PostStatus.APPROVED)));
            when(userService.getCurrentUser()).thenReturn(author);
            when(hotspotRepository.findAllById(List.of(10L, 11L)))
                    .thenReturn(List.of(new Hotspot()));   // chỉ tìm thấy 1/2

            UpdatePostRequest request = updateRequest("Nội dung mới");
            request.setHotspotIds(List.of(10L, 11L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.updatePost(1L, request));

            assertEquals("Địa điểm được gắn thẻ không tồn tại", ex.getMessage());
        }

        // UTCID04 - Abnormal: gắn thẻ phân loại không tồn tại
        @Test
        void updatePost_tagNotFound_throwsTagNotFound() {
            User author = user(1L, "tacgia");
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, author, PostStatus.APPROVED)));
            when(userService.getCurrentUser()).thenReturn(author);
            when(tagRepository.findAllById(List.of(5L))).thenReturn(List.of());

            UpdatePostRequest request = updateRequest("Nội dung mới");
            request.setTagIds(List.of(5L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.updatePost(1L, request));

            assertEquals("Một số thẻ phân loại không tồn tại", ex.getMessage());
        }

        // UTCID05 - Abnormal: xóa media không thuộc bài viết này
        @Test
        void updatePost_removingForeignMedia_throwsMediaNotOwned() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.APPROVED);
            Media own = new Media();
            own.setMediaId(100L);
            target.getMedias().add(own);

            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(author);

            UpdatePostRequest request = updateRequest("Nội dung mới");
            request.setRemovedMediaIds(List.of(999L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.updatePost(1L, request));

            assertEquals("Media không thuộc bài viết này: [999]", ex.getMessage());
        }

        // UTCID06 - Normal: xóa media hợp lệ -> lên lịch xóa file S3 sau khi commit
        @Test
        void updatePost_removingOwnMedia_schedulesS3DeletionAfterCommit() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.APPROVED);
            Media own = new Media();
            own.setMediaId(100L);
            own.setFileUrl("https://s3/posts/anh.png");
            target.getMedias().add(own);

            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(author);
            when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapper();

            UpdatePostRequest request = updateRequest("Nội dung mới");
            request.setRemovedMediaIds(List.of(100L));

            postService.updatePost(1L, request);

            assertTrue(target.getMedias().isEmpty());
            verify(txCompensation).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID07 - Normal: cập nhật nội dung + visibility thành công
        @Test
        void updatePost_validOwner_updatesContentAndVisibility() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.APPROVED);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(author);
            when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapper();

            UpdatePostRequest request = updateRequest("Nội dung đã sửa");
            request.setVisibility(PostVisibility.PRIVATE);

            postService.updatePost(1L, request);

            assertEquals("Nội dung đã sửa", target.getContent());
            assertEquals(PostVisibility.PRIVATE, target.getVisibility());
        }

        // UTCID08 - Boundary: visibility = null -> giữ nguyên giá trị cũ
        @Test
        void updatePost_nullVisibility_keepsOldVisibility() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.APPROVED);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(author);
            when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapper();

            UpdatePostRequest request = updateRequest("Nội dung đã sửa");
            request.setVisibility(null);

            postService.updatePost(1L, request);

            assertEquals(PostVisibility.PUBLIC, target.getVisibility());
        }
    }

    // =====================================================================
    // Function: commentPost
    // =====================================================================
    @Nested
    @DisplayName("commentPost")
    class CommentPostTest {

        private static CommentRequest commentRequest(String comment, Long parentActionId) {
            CommentRequest request = new CommentRequest();
            request.setComment(comment);
            request.setParentActionId(parentActionId);
            return request;
        }

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void commentPost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.commentPost(1L, commentRequest("Đẹp quá!", null)));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
            verify(postActionRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: trả lời một bình luận gốc không tồn tại
        @Test
        void commentPost_parentCommentNotFound_throwsParentNotFound() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.APPROVED)));
            when(userService.getCurrentUser()).thenReturn(user(2L, "nguoibinhluan"));
            when(postActionRepository.findById(50L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.commentPost(1L, commentRequest("Đồng ý", 50L)));

            assertEquals("Bình luận gốc không tồn tại", ex.getMessage());
            verify(postActionRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: trả lời một lượt LIKE thay vì bình luận
        @Test
        void commentPost_parentIsNotComment_throwsOnlyReplyToComment() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.APPROVED)));
            when(userService.getCurrentUser()).thenReturn(user(2L, "nguoibinhluan"));
            when(postActionRepository.findById(50L)).thenReturn(Optional.of(
                    PostAction.builder()
                            .postActionId(50L)
                            .actionType(PostActionType.LIKE)
                            .build()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.commentPost(1L, commentRequest("Đồng ý", 50L)));

            assertEquals("Chỉ có thể phản hồi lại một bình luận", ex.getMessage());
            verify(postActionRepository, never()).save(any());
        }

        // UTCID04 - Normal: bình luận gốc (không có parent) -> lưu và xóa cache đếm
        @Test
        void commentPost_rootComment_savesAndEvictsCounter() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            User commenter = user(2L, "nguoibinhluan");
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(commenter);
            stubMapper();

            postService.commentPost(1L, commentRequest("Đẹp quá!", null));

            ArgumentCaptor<PostAction> captor = ArgumentCaptor.forClass(PostAction.class);
            verify(postActionRepository).save(captor.capture());
            PostAction saved = captor.getValue();
            assertEquals(PostActionType.COMMENT, saved.getActionType());
            assertEquals("Đẹp quá!", saved.getComment());
            assertNull(saved.getParentAction());
            assertSame(commenter, saved.getUser());
            verify(postCounterService).evict(1L);
        }

        // UTCID05 - Normal: trả lời bình luận hợp lệ -> gắn parentAction
        @Test
        void commentPost_replyToComment_linksParentAction() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            PostAction parent = PostAction.builder()
                    .postActionId(50L)
                    .actionType(PostActionType.COMMENT)
                    .comment("Bình luận gốc")
                    .build();
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(user(2L, "nguoibinhluan"));
            when(postActionRepository.findById(50L)).thenReturn(Optional.of(parent));
            stubMapper();

            postService.commentPost(1L, commentRequest("Mình cũng nghĩ vậy", 50L));

            ArgumentCaptor<PostAction> captor = ArgumentCaptor.forClass(PostAction.class);
            verify(postActionRepository).save(captor.capture());
            assertSame(parent, captor.getValue().getParentAction());
        }
    }

    // =====================================================================
    // Function: approvePost
    // =====================================================================
    @Nested
    @DisplayName("approvePost")
    class ApprovePostTest {

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void approvePost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.approvePost(1L));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: bài viết đã được duyệt trước đó
        @Test
        void approvePost_alreadyApproved_throwsWrongStatus() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.APPROVED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.approvePost(1L));

            assertEquals("Bài viết không ở trạng thại chờ phê duyệt", ex.getMessage());
            verify(rewardTransactionService, never()).createRewardTransaction(any());
        }

        // UTCID03 - Normal: duyệt bài -> APPROVED, cộng điểm cho tác giả, ghi người duyệt
        @Test
        void approvePost_pendingPost_approvesAndAwardsPoints() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.PENDING);
            User moderator = user(99L, "kiemduyetvien");
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(moderator);
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            postService.approvePost(1L);

            assertEquals(PostStatus.APPROVED, target.getStatus());
            assertEquals(99L, target.getModerateBy());
            assertNotNull(target.getModerateAt());

            ArgumentCaptor<RewardTransactionRequest> captor =
                    ArgumentCaptor.forClass(RewardTransactionRequest.class);
            verify(rewardTransactionService).createRewardTransaction(captor.capture());
            RewardTransactionRequest reward = captor.getValue();
            assertEquals(1L, reward.getUserId());
            assertEquals(TransactionType.POST_CREATION, reward.getTransactionType());
            assertEquals(0L, reward.getXpAmount());
        }
    }

    // =====================================================================
    // Function: rejectPost
    // =====================================================================
    @Nested
    @DisplayName("rejectPost")
    class RejectPostTest {

        private static RejectPostRequest rejectRequest(String reason) {
            RejectPostRequest request = new RejectPostRequest();
            request.setRejectReason(reason);
            return request;
        }

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void rejectPost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.rejectPost(1L, rejectRequest("Nội dung vi phạm")));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: bài viết đã bị từ chối trước đó
        @Test
        void rejectPost_alreadyRejected_throwsWrongStatus() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.REJECTED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.rejectPost(1L, rejectRequest("Nội dung vi phạm")));

            assertEquals("Bài viết không ở trạng thái chờ phê duyệt", ex.getMessage());
            verify(postRepository, never()).save(any());
        }

        // UTCID03 - Normal: từ chối bài -> REJECTED, lưu lý do, KHÔNG cộng điểm
        @Test
        void rejectPost_pendingPost_rejectsWithReasonAndNoPoints() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.PENDING);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(user(99L, "kiemduyetvien"));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            postService.rejectPost(1L, rejectRequest("Nội dung vi phạm quy định cộng đồng"));

            assertEquals(PostStatus.REJECTED, target.getStatus());
            assertEquals("Nội dung vi phạm quy định cộng đồng", target.getReason());
            assertEquals(99L, target.getModerateBy());
            verify(rewardTransactionService, never()).createRewardTransaction(any());
        }
    }

    // =====================================================================
    // Function: deletePost
    // =====================================================================
    @Nested
    @DisplayName("deletePost")
    class DeletePostTest {

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void deletePost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.deletePost(1L));

            assertEquals("Bài viết không tồn tại với ID: 1", ex.getMessage());
        }

        // UTCID02 - Abnormal: xóa bài viết của người khác
        @Test
        void deletePost_notOwner_throwsNoPermission() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.APPROVED)));
            when(userService.getCurrentUser()).thenReturn(user(2L, "nguoikhac"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.deletePost(1L));

            assertEquals("Bạn không có quyền xóa bài viết này!", ex.getMessage());
            verify(postRepository, never()).save(any());
        }

        // UTCID03 - Normal: chủ bài viết xóa -> xóa mềm, status DELETED
        @Test
        void deletePost_owner_setsStatusDeleted() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.APPROVED);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(author);

            postService.deletePost(1L);

            assertEquals(PostStatus.DELETED, target.getStatus());
            verify(postRepository).save(target);
            verify(postRepository, never()).delete(any());
        }

        // UTCID04 - Normal: xóa vĩnh viễn -> gọi delete thật sự
        @Test
        void deletePostPermanently_owner_deletesRow() {
            User author = user(1L, "tacgia");
            Post target = post(1L, author, PostStatus.APPROVED);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(author);

            postService.deletePostPermanently(1L);

            verify(postRepository).delete(target);
        }
    }

    // =====================================================================
    // Function: sharePost
    // =====================================================================
    @Nested
    @DisplayName("sharePost")
    class SharePostTest {

        private static ShareRequest shareRequest(PostVisibility visibility) {
            ShareRequest request = new ShareRequest();
            request.setContent("Chia sẻ bài hay");
            request.setVisibility(visibility);
            return request;
        }

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void sharePost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.sharePost(1L, shareRequest(PostVisibility.PUBLIC)));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: bài chưa được duyệt thì không cho chia sẻ
        @Test
        void sharePost_notApprovedPost_throwsOnlyApproved() {
            when(postRepository.findById(1L))
                    .thenReturn(Optional.of(post(1L, user(1L, "tacgia"), PostStatus.PENDING)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.sharePost(1L, shareRequest(PostVisibility.PUBLIC)));

            assertEquals("Chỉ có thể chia sẻ bài viết đã được phê duyệt", ex.getMessage());
            verify(postActionRepository, never()).save(any());
        }

        // UTCID03 - Normal: chia sẻ hợp lệ -> tạo bài mới trỏ về bài gốc, status APPROVED
        @Test
        void sharePost_approvedPost_createsSharedPost() {
            Post original = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            User sharer = user(2L, "nguoichiase");
            when(postRepository.findById(1L)).thenReturn(Optional.of(original));
            when(userService.getCurrentUser()).thenReturn(sharer);
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapper();

            postService.sharePost(1L, shareRequest(PostVisibility.PUBLIC));

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            Post shared = captor.getValue();
            assertSame(original, shared.getSharedPost());
            assertSame(sharer, shared.getUser());
            assertEquals(PostStatus.APPROVED, shared.getStatus());
            // Số lượt chia sẻ tăng trên bài GỐC
            verify(postCounterService).evict(1L);
        }

        // UTCID04 - Boundary: không truyền visibility -> mặc định PUBLIC
        @Test
        void sharePost_nullVisibility_defaultsToPublic() {
            Post original = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            when(postRepository.findById(1L)).thenReturn(Optional.of(original));
            when(userService.getCurrentUser()).thenReturn(user(2L, "nguoichiase"));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapper();

            postService.sharePost(1L, shareRequest(null));

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertEquals(PostVisibility.PUBLIC, captor.getValue().getVisibility());
        }
    }

    // =====================================================================
    // Function: toggleLikePost
    // =====================================================================
    @Nested
    @DisplayName("toggleLikePost")
    class ToggleLikePostTest {

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void toggleLikePost_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.toggleLikePost(1L));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
        }

        // UTCID02 - Normal: chưa like -> thêm lượt like mới
        @Test
        void toggleLikePost_notLikedYet_addsLikeAction() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            User liker = user(2L, "nguoithich");
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(liker);
            when(postActionRepository.findByPost_PostIdAndUser_UserIdAndActionType(
                    1L, 2L, PostActionType.LIKE)).thenReturn(Optional.empty());
            stubMapper();

            postService.toggleLikePost(1L);

            assertEquals(1, target.getPostActions().size());
            assertEquals(PostActionType.LIKE, target.getPostActions().get(0).getActionType());
            verify(postCounterService).evict(1L);
        }

        // UTCID03 - Normal: đã like rồi -> bỏ like
        @Test
        void toggleLikePost_alreadyLiked_removesLikeAction() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            User liker = user(2L, "nguoithich");
            PostAction existing = PostAction.builder()
                    .postActionId(77L)
                    .post(target)
                    .user(liker)
                    .actionType(PostActionType.LIKE)
                    .build();
            target.getPostActions().add(existing);

            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(liker);
            when(postActionRepository.findByPost_PostIdAndUser_UserIdAndActionType(
                    1L, 2L, PostActionType.LIKE)).thenReturn(Optional.of(existing));
            stubMapper();

            postService.toggleLikePost(1L);

            assertTrue(target.getPostActions().isEmpty());
            verify(postCounterService).evict(1L);
        }
    }

    // =====================================================================
    // Function: banPostByAdmin
    // =====================================================================
    @Nested
    @DisplayName("banPostByAdmin")
    class BanPostByAdminTest {

        private static DeletePostRequest banRequest(String reason) {
            DeletePostRequest request = new DeletePostRequest();
            request.setReason(reason);
            return request;
        }

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void banPostByAdmin_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.banPostByAdmin(1L, banRequest("Spam")));

            assertEquals("Bài viết không tồn tại hoặc đã bị xóa", ex.getMessage());
            verify(auditLogService, never()).log(any(), anyString(), anyString(), any(), any());
        }

        // UTCID02 - Normal: admin gỡ bài -> DELETED, lưu lý do và ghi audit log
        @Test
        void banPostByAdmin_validPost_deletesAndWritesAuditLog() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.APPROVED);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            postService.banPostByAdmin(1L, banRequest("Spam quảng cáo"));

            assertEquals(PostStatus.DELETED, target.getStatus());
            assertEquals("Spam quảng cáo", target.getReason());
            verify(auditLogService).log(eq(AuditAction.BAN_POST), eq("posts"), eq("1"),
                    any(), any());
        }

        // UTCID03 - Boundary: gỡ bài đang chờ duyệt -> audit log ghi lại status cũ là PENDING
        @Test
        void banPostByAdmin_pendingPost_auditLogsOldStatus() {
            Post target = post(1L, user(1L, "tacgia"), PostStatus.PENDING);
            when(postRepository.findById(1L)).thenReturn(Optional.of(target));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

            postService.banPostByAdmin(1L, banRequest("Spam"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> captor =
                    ArgumentCaptor.forClass(java.util.Map.class);
            verify(auditLogService).log(any(), anyString(), anyString(), captor.capture(), any());
            assertEquals(PostStatus.PENDING, captor.getValue().get("status"));
        }
    }

    // =====================================================================
    // Function: getCommentsByPostId
    // =====================================================================
    @Nested
    @DisplayName("getCommentsByPostId")
    class GetCommentsByPostIdTest {

        // UTCID01 - Abnormal: bài viết không tồn tại
        @Test
        void getCommentsByPostId_postNotFound_throwsPostNotFound() {
            when(postRepository.existsById(1L)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postService.getCommentsByPostId(1L, 0, 10));

            assertEquals("Bài viết không tồn tại", ex.getMessage());
            verify(postActionRepository, never())
                    .findByPost_PostIdAndActionTypeAndParentActionIsNullOrderByCreatedAtAsc(
                            anyLong(), any(), any());
        }

        // UTCID02 - Normal: chỉ lấy bình luận gốc (parentAction = null)
        @Test
        void getCommentsByPostId_valid_queriesRootCommentsOnly() {
            when(postRepository.existsById(1L)).thenReturn(true);
            when(postActionRepository
                    .findByPost_PostIdAndActionTypeAndParentActionIsNullOrderByCreatedAtAsc(
                            eq(1L), eq(PostActionType.COMMENT), any()))
                    .thenReturn(new org.springframework.data.domain.SliceImpl<>(List.of()));

            assertTrue(postService.getCommentsByPostId(1L, 0, 10).getContent().isEmpty());
            verify(postActionRepository)
                    .findByPost_PostIdAndActionTypeAndParentActionIsNullOrderByCreatedAtAsc(
                            eq(1L), eq(PostActionType.COMMENT), any());
        }
    }
}
