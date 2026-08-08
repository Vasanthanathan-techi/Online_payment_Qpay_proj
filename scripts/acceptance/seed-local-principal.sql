USE qpay_auth;
SET @principal_id = '11111111-1111-1111-1111-111111111111';
SET @owner_id = '22222222-2222-2222-2222-222222222222';
INSERT INTO principal
    (id, principal_type, external_owner_id, login_name, password_hash, status,
     password_changed_at, created_at, updated_at)
VALUES
    (UUID_TO_BIN(@principal_id, 1), 'MERCHANT', UUID_TO_BIN(@owner_id, 1),
     'local.merchant@qpay.test', '$2a$12$WvQtc//12v.yP5dSWJmeruahciAkhwplhHGdOt8p21xZ1WIBbtoDG',
     'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 'ACTIVE';
INSERT INTO principal_role(principal_id, role_code, created_at)
VALUES(UUID_TO_BIN(@principal_id, 1), 'MERCHANT', CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE role_code = VALUES(role_code);
