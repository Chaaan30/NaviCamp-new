-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: campusnavigator.c10aiyo64bnv.ap-southeast-1.rds.amazonaws.com    Database: campusnavigator
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '';

--
-- Table structure for table `devices_table`
--

DROP TABLE IF EXISTS `devices_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `devices_table` (
  `deviceID` varchar(255) NOT NULL,
  `userID` int DEFAULT NULL,
  `status` varchar(45) NOT NULL,
  `latitude` decimal(10,8) DEFAULT NULL COMMENT 'Device GPS latitude',
  `longitude` decimal(11,8) DEFAULT NULL COMMENT 'Device GPS longitude',
  `floorLevel` varchar(50) DEFAULT NULL COMMENT 'Current floor level of the device',
  `connectedUntil` datetime DEFAULT NULL,
  `rssi` int DEFAULT NULL,
  `distance` float DEFAULT NULL,
  PRIMARY KEY (`deviceID`),
  UNIQUE KEY `deviceID_UNIQUE` (`deviceID`),
  KEY `userID_idx` (`userID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `devices_table`
--

LOCK TABLES `devices_table` WRITE;
/*!40000 ALTER TABLE `devices_table` DISABLE KEYS */;
INSERT INTO `devices_table` VALUES ('202501',NULL,'available',14.24379863,121.11138234,'First Floor',NULL,NULL,NULL);
/*!40000 ALTER TABLE `devices_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `incident_logs_table`
--

DROP TABLE IF EXISTS `incident_logs_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `incident_logs_table` (
  `alertID` varchar(8) NOT NULL,
  `userID` int NOT NULL,
  `deviceID` varchar(255) NOT NULL,
  `locationID` varchar(8) NOT NULL,
  `status` varchar(45) DEFAULT NULL,
  `alertDateTime` varchar(45) DEFAULT NULL,
  `alertDescription` longtext,
  `officerResponded` varchar(45) DEFAULT NULL,
  `resolvedOn` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`alertID`),
  KEY `userID_idx` (`userID`),
  KEY `locationID_idx` (`locationID`),
  KEY `deviceID_idx` (`deviceID`),
  CONSTRAINT `fk_incident_logs_deviceID` FOREIGN KEY (`deviceID`) REFERENCES `devices_table` (`deviceID`),
  CONSTRAINT `locationID` FOREIGN KEY (`locationID`) REFERENCES `location_table` (`locationID`),
  CONSTRAINT `userID` FOREIGN KEY (`userID`) REFERENCES `user_table` (`userID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `incident_logs_table`
--

LOCK TABLES `incident_logs_table` WRITE;
/*!40000 ALTER TABLE `incident_logs_table` DISABLE KEYS */;
INSERT INTO `incident_logs_table` VALUES ('ALT001A1',20250006,'202501','47BC44F3','pending','2025-06-03 08:58:41',NULL,NULL,NULL),('ALT002B2',20250006,'202501','72EF0A74','pending','2025-06-03 08:58:42',NULL,NULL,NULL),('ALT006F6',20250006,'202501','1CDBA86D','resolved','2025-06-03 14:31:32',NULL,'Jay Delos Santos','2025-06-06 11:47:38'),('ALT007G7',20250006,'202501','896E1F32','pending','2025-06-03 08:58:43',NULL,NULL,NULL),('ALT008H8',20250006,'202501','A1F59609','false alarm','2025-06-03 08:58:44','gumanon','Jay Delos Santos','2025-06-06 12:03:56'),('ALT009I9',20250006,'202501','B81C057D','pending','2025-06-03 03:35:31',NULL,NULL,NULL),('ALT010J0',20250008,'202501','BB487FB9','pending','2025-06-08 02:22:41',NULL,NULL,NULL),('ALT011K1',20250008,'202501','D0662E7C','pending','2025-06-08 02:39:17',NULL,NULL,NULL);
/*!40000 ALTER TABLE `incident_logs_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `location_table`
--

DROP TABLE IF EXISTS `location_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `location_table` (
  `locationID` varchar(8) NOT NULL,
  `userID` int DEFAULT NULL,
  `deviceID` varchar(255) DEFAULT NULL,
  `latitude` varchar(45) NOT NULL,
  `longitude` varchar(45) NOT NULL,
  `floorLevel` varchar(45) DEFAULT NULL,
  `dateTime` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`locationID`),
  KEY `fk_location_table_deviceID` (`deviceID`),
  CONSTRAINT `fk_location_table_deviceID` FOREIGN KEY (`deviceID`) REFERENCES `devices_table` (`deviceID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `location_table`
--

LOCK TABLES `location_table` WRITE;
/*!40000 ALTER TABLE `location_table` DISABLE KEYS */;
INSERT INTO `location_table` VALUES ('15EAFC9C',20250004,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-02-11 18:35:48'),('1CDBA86D',20250006,'202501','0.0','0.0','Unknown','2025-06-03 14:31:32'),('293328B8',20250004,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-02-10 06:44:12'),('2A319B23',20250004,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-02-11 14:19:54'),('47BC44F3',20250006,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-06-03 08:58:41'),('72EF0A74',20250006,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-06-03 08:58:42'),('896E1F32',20250006,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-06-03 08:58:43'),('A1F59609',20250006,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-06-03 08:58:44'),('B81C057D',20250006,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-06-03 03:35:31'),('BAF94D1F',20250005,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-02-11 14:20:25'),('BB487FB9',20250008,'202501','14.24379863','121.11138234','First Floor','2025-06-08 02:22:41'),('C7122AF6',20250004,NULL,'14.243667','121.111429','Einstein Building Ground Floor','2025-02-11 18:35:48'),('D0662E7C',20250008,'202501','14.24379863','121.11138234','First Floor','2025-06-08 02:39:17');
/*!40000 ALTER TABLE `location_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_table`
--

DROP TABLE IF EXISTS `user_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_table` (
  `userID` int NOT NULL,
  `fullName` varchar(255) DEFAULT NULL,
  `email` varchar(45) DEFAULT NULL,
  `contactNumber` varchar(45) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `userType` varchar(50) DEFAULT NULL,
  `createdOn` datetime DEFAULT NULL,
  `updatedOn` datetime DEFAULT NULL,
  `proofPicture` varchar(255) DEFAULT NULL,
  `verified` tinyint DEFAULT '0',
  PRIMARY KEY (`userID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_table`
--

LOCK TABLES `user_table` WRITE;
/*!40000 ALTER TABLE `user_table` DISABLE KEYS */;
INSERT INTO `user_table` VALUES (20250001,'Jay Delos Santos','jay013003@gmail.com','98646192665','8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414','Security Officer','2025-06-08 01:12:49',NULL,'proof_of_officer/1749316368254_compressed_7307252648281483666.jpg',1),(20250006,'Nicca Quintillan','niccagnesq@gmail.com','09083356963','7003d16ad2fc576a1d563d8a2e76933389f8ca8a969374a22a31faeeca1f0cff','Student','2025-02-07 08:16:48',NULL,'proof_of_disability/20250527_181925.jpg',1),(20250008,'dcristian','dcristianjay@gmail.com','12345678999','8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414','Student','2025-05-29 20:32:53',NULL,'proof_of_disability/IMG20250511125025.jpg',1);
/*!40000 ALTER TABLE `user_table` ENABLE KEYS */;
UNLOCK TABLES;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-06-08  2:42:10
