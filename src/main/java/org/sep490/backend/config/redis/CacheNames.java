package org.sep490.backend.config.redis;

import java.time.Duration;

public class CacheNames {

    private CacheNames() {}

    // Phase 1 - master data
    public static final String LEVELS = "levels";
    public static final String LEVEL_BY_XP = "levelByXp";
    public static final String TAGS = "tags";
    public static final String SUBSCRIPTION_PLANS = "subscriptionPlans";

    // Phase 2 - dashboard + API ngoài
    public static final String ADMIN_DASHBOARD = "adminDashboard";
    public static final String CURATOR_DASHBOARD = "curatorDashboard";
    public static final String GOONG_MATRIX = "goongMatrix";
    public static final String AI_RERANK = "aiRerank";

    // Phase 6 - user
    public static final String LEADERBOARD = "leaderboard";
    public static final String MY_RANK = "myRank";
    public static final String USER_BY_KEYCLOAK = "userByKeycloak";

    // Phase 7 - content + geo
    public static final String GEO_IN_VIETNAM = "geoInVietnam";
    public static final String GEO_NEARBY = "geoNearby";
    public static final String HOTSPOT_DETAIL = "hotspotDetail";
    public static final String ROUTE_DETAIL = "routeDetail";
    public static final String STORY_DETAIL = "storyDetail";

    //TTL
    public static final Duration TTL_MASTER_DATA = Duration.ofHours(6);
    public static final Duration TTL_TAGS = Duration.ofHours(1);
    public static final Duration TTL_DASHBOARD = Duration.ofMinutes(3);
    public static final Duration TTL_GOONG = Duration.ofDays(30);
    public static final Duration TTL_AI = Duration.ofHours(24);
    public static final Duration TTL_LEADERBOARD = Duration.ofSeconds(60);
    public static final Duration TTL_USER = Duration.ofMinutes(30);
    public static final Duration TTL_GEO_STATIC = Duration.ofDays(30);
    public static final Duration TTL_GEO_NEARBY = Duration.ofMinutes(30);
    public static final Duration TTL_CONTENT_DETAIL = Duration.ofMinutes(15);

    // Key prefix cho thao tác Redis thủ công (không qua CacheManager)
    public static final String KEY_RATING = "rating:";
    public static final String KEY_CHECKIN_USER = "checkin:user:";
    public static final String KEY_POST_COUNTS = "post:%d:counts";
    public static final String KEY_POST_LIKERS = "post:%d:likers";
    public static final String KEY_USER_COUNTS = "user:%d:counts";
    public static final String KEY_NOTIF_UNREAD = "notif:unread:";
    public static final String KEY_OTP = "otp:";
    public static final String KEY_OTP_COOLDOWN = "otp:cooldown:";
    public static final String KEY_OTP_ATTEMPT = "otp:attempt:";
    public static final String KEY_PWRESET = "pwreset:";
    public static final String KEY_PWRESET_USER = "pwreset:user:";
    public static final String KEY_DENYLIST = "denylist:jti:";
    public static final String KEY_KC_ADMIN_TOKEN = "kc:admin-token";
    public static final String KEY_GEO_VN = "geo:vn:";
    public static final String KEY_GEO_NEARBY = "geo:nearby:";
}
