ALTER TABLE partner_info ADD COLUMN IF NOT EXISTS shop_account_user_id BIGINT;

ALTER TABLE partner_info DROP CONSTRAINT IF EXISTS fk_partner_info_shop_account;
ALTER TABLE partner_info ADD CONSTRAINT fk_partner_info_shop_account
    FOREIGN KEY (shop_account_user_id) REFERENCES users(user_id);

UPDATE partner_info pi
SET shop_account_user_id = u.user_id
FROM users u
WHERE u.email = pi.shop_email
  AND u.role = 'PARTNER'
  AND pi.shop_account_user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_partner_info_shop_account
    ON partner_info (shop_account_user_id);

SELECT COUNT(*)                                                   AS tong,
       COUNT(*) FILTER (WHERE shop_account_user_id IS NOT NULL)   AS da_gan_shop_account,
       COUNT(*) FILTER (WHERE shop_account_user_id IS NULL)       AS chua_gan
FROM partner_info;

SELECT pi.partner_info_id, pi.shop_email
FROM partner_info pi
JOIN invoices i ON i.partner_info_id = pi.partner_info_id
WHERE i.status = 'ACTIVE'
  AND pi.shop_account_user_id IS NULL;
