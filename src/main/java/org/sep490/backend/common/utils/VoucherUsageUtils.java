package org.sep490.backend.common.utils;

public class VoucherUsageUtils {

    public record ShareVoucherTokenInfo(long voucherId,  long userId) {
    }

    public static String generateToken(long voucherId, long userId) {
        String voucherPart = ShareTokenUtils.encodeToBase62(voucherId, 5);
        String userPart = ShareTokenUtils.encodeToBase62(userId, 5);

        return voucherPart + userPart;
    }

    public static ShareVoucherTokenInfo parseToken(String token) {
        if (token == null || token.length() != 10) {
            throw new IllegalArgumentException("Token không hợp lệ. Độ dài bắt buộc là 10 ký tự.");
        }

        String voucherPart = token.substring(0, 5);
        String userPart = token.substring(5, 10);

        long voucherId = ShareTokenUtils.decodeFromBase62(voucherPart);
        long userId = ShareTokenUtils.decodeFromBase62(userPart);

        return new ShareVoucherTokenInfo(voucherId, userId);
    }
}
