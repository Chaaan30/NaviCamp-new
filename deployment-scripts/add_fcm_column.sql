-- Add FCM token column to user_table for push notifications
ALTER TABLE user_table ADD COLUMN fcm_token TEXT DEFAULT NULL;
 
-- Create index for faster FCM token lookups for Security Officers (with key length for TEXT column)
CREATE INDEX idx_user_fcm_token ON user_table(userType, verified, fcm_token(255)); 