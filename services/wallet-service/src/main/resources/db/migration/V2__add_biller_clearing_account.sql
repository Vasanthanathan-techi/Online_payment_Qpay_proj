INSERT INTO ledger_account(id,wallet_id,account_code,account_type,normal_side,currency,status,created_at)
VALUES(UUID_TO_BIN(UUID(),1),NULL,'BILLER_CLEARING','ASSET','DEBIT','INR','ACTIVE',CURRENT_TIMESTAMP(6));

