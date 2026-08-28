package org.sep490.backend.module.content.scheduler;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.content.service.inter.StoryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TempContentScheduler {

    private final HotspotService hotspotService;
    private final StoryService storyService;
    private final RouteService routeService;

    @Scheduled(cron = "0 0 17 * * *") // Run daily at midnight
    public void processInvalidTempContent() {
        List<Long> storyIds = hotspotService.processInvalidTempHotspot();

        List<Story> belongToRoute = storyService.processInvalidTempStory(storyIds);

        routeService.processRouteWithInvalidTempStory(belongToRoute);
    }
}
