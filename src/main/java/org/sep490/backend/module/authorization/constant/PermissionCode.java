package org.sep490.backend.module.authorization.constant;

public final class PermissionCode {

    private PermissionCode() {}

    public static final String PREFIX = "PERM_";

    // --- USER ---
    public static final String USER_VIEW_ALL    = "USER_VIEW_ALL";
    public static final String USER_LOCK        = "USER_LOCK";
    public static final String USER_UPDATE_ROLE = "USER_UPDATE_ROLE";

    // --- POST ---
    public static final String POST_CREATE     = "POST_CREATE";
    public static final String POST_UPDATE_ANY = "POST_UPDATE_ANY";
    public static final String POST_DELETE_ANY = "POST_DELETE_ANY";
    public static final String POST_MODERATE   = "POST_MODERATE";

    // --- CONTENT ---
    public static final String HOTSPOT_MANAGE = "HOTSPOT_MANAGE";
    public static final String ROUTE_MANAGE   = "ROUTE_MANAGE";
    public static final String STORY_MANAGE   = "STORY_MANAGE";
    public static final String TAG_MANAGE     = "TAG_MANAGE";

    // --- REVIEW ---
    public static final String REVIEW_MODERATE   = "REVIEW_MODERATE";
    public static final String REVIEW_DELETE_ANY = "REVIEW_DELETE_ANY";

    // --- VOUCHER ---
    public static final String VOUCHER_MANAGE         = "VOUCHER_MANAGE";
    public static final String VOUCHER_PARTNER_MANAGE = "VOUCHER_PARTNER_MANAGE";

    // --- SUBSCRIPTION ---
    public static final String SUBSCRIPTION_PLAN_MANAGE = "SUBSCRIPTION_PLAN_MANAGE";
    public static final String SUBSCRIPTION_VERIFY      = "SUBSCRIPTION_VERIFY";

    // --- GAMIFICATION ---
    public static final String LEVEL_MANAGE = "LEVEL_MANAGE";

    // --- SYSTEM ---
    public static final String DASHBOARD_ADMIN_VIEW   = "DASHBOARD_ADMIN_VIEW";
    public static final String DASHBOARD_CURATOR_VIEW = "DASHBOARD_CURATOR_VIEW";
    public static final String DASHBOARD_PARTNER_VIEW = "DASHBOARD_PARTNER_VIEW";
    public static final String AUDIT_LOG_VIEW         = "AUDIT_LOG_VIEW";
    public static final String PERMISSION_MANAGE      = "PERMISSION_MANAGE";
}
