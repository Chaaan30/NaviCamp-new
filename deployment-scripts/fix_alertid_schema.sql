-- Fix alertID schema to support string-based alert IDs
-- Change alertID column from INT to VARCHAR(8) to match the alphanumeric generation

USE campusnavigator;

-- First, let's see the current schema (for reference)
-- DESCRIBE incident_logs_table;

-- Modify the alertID column to VARCHAR(8)
ALTER TABLE incident_logs_table 
MODIFY COLUMN alertID VARCHAR(8) NOT NULL;

-- Verify the change
DESCRIBE incident_logs_table;

-- Note: This change is safe because:
-- 1. VARCHAR(8) can store the existing numeric IDs
-- 2. It allows future alphanumeric IDs like "NN3QE471"
-- 3. The PRIMARY KEY constraint will remain intact 