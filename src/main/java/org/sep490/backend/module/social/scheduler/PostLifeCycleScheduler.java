package org.sep490.backend.module.social.scheduler;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.social.service.PostService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostLifeCycleScheduler {
    private final PostService postService;

    @Scheduled(cron = "0 0 0 * * *")
    public void deleteDeletedPosts() {
        postService.deleteDeletedPosts();
    }
}
