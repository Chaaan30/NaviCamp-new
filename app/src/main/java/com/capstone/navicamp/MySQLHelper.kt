package com.capstone.navicamp

import android.util.Log
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.ResultSet
import java.util.*
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

// Data class to hold active connection information
data class ActiveConnectionInfo(
    val deviceID: String,
    val expiryTime: Long
)

data class PwdProfileData(
    val disabilityType: String?,
    val expiryDate: String?,
    val emergencyContactPerson: String?,
    val emergencyContactNumber: String?
)

enum class DeviceStatus {
    AVAILABLE,
    IN_USE,
    MAINTENANCE,
    UNKNOWN
}

data class OngoingIncidentInfo(
    val alertID: String,
    val locationID: String,
    val officerName: String,
    val officerUserID: String?
)

data class ActiveAssistanceGps(
    val userID: String,
    val fullName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val status: String = "pending",
    val locationID: String = ""
)

data class VerificationGeneratorAccess(
    val campusAffiliation: String,
    val department: String,
    val position: String,
    val isAdmin: Boolean
)

object MySQLHelper {

    // Database credentials

    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val JDBC_URL =
        "jdbc:mariadb://campusnavigator.c10aiyo64bnv.ap-southeast-1.rds.amazonaws.com:3306/campusnavigator"
    private const val USERNAME = "navicamp"
    private const val PASSWORD = "navicamp"

    // Deferred Object
    private val isInitialized = CompletableDeferred<Boolean>()
    private var connection: Connection? = null

    // Generate random 8-character alphanumeric alert ID
    private fun generateAlertID(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    init {
        try {
            Class.forName("org.mariadb.jdbc.Driver")
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
        helperScope.launch(Dispatchers.IO) {
            try {
                connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)
                Log.d("MySQLHelper", "Database connection successful.")
                isInitialized.complete(true)
            } catch (e: Exception) {
                Log.e("MySQLHelper", "Database connection failed: ${e.message}", e)
                isInitialized.complete(false)
            }
        }
    }

    suspend fun awaitInitialized() {
        isInitialized.await()
    }

    // Get the connection to the database
    fun getConnection(): Connection? {
        return try {
            DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }

    fun countPendingItems(location: String): Int {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return 0
            }

            val query = """
                SELECT COUNT(*) AS count
                FROM location_table l
                JOIN incident_logs_table i ON l.locationID = i.locationID
                WHERE l.floorLevel = ? AND i.status = 'pending'
            """.trimIndent()
            statement = connection.prepareStatement(query)
            statement.setString(1, location)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getInt("count")
            } else {
                0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            0
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getPendingItems(currentOfficerName: String? = null): List<LocationItem> {
        val pendingItems = mutableListOf<LocationItem>()
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return pendingItems
            }

            val query = if (!currentOfficerName.isNullOrBlank()) {
                """
                    SELECT 
                        l.locationID,
                        l.userID,
                        l.deviceID,
                        u.fullName,
                        l.floorLevel,
                        i.status,
                        l.latitude,
                        l.longitude,
                        l.dateTime,
                        i.officerResponded,
                        i.relocatedLocation,
                        p.emergencyContactPerson,
                        p.emergencyContactNumber
                    FROM incident_logs_table i
                    JOIN (
                        SELECT
                            locationID,
                            MAX(CONCAT(alertDateTime, '|', alertID)) AS latestKey
                        FROM incident_logs_table
                        GROUP BY locationID
                    ) latest ON latest.locationID = i.locationID
                        AND CONCAT(i.alertDateTime, '|', i.alertID) = latest.latestKey
                    JOIN location_table l ON i.locationID = l.locationID
                    JOIN user_table u ON l.userID = u.userID
                    LEFT JOIN pwd_profiles_table p ON l.userID = p.userID
                    WHERE LOWER(TRIM(i.status)) IN ('pending', 'ongoing')
                       OR (
                           LOWER(TRIM(i.status)) = 'resolved'
                           AND i.officerResponded = ?
                           AND (i.relocatedLocation IS NULL OR i.relocatedLocation = '')
                       )
                    ORDER BY FIELD(LOWER(TRIM(i.status)), 'ongoing', 'pending', 'resolved'), i.alertDateTime DESC
                """.trimIndent()
            } else {
                """
                    SELECT 
                        l.locationID,
                        l.userID,
                        l.deviceID,
                        u.fullName,
                        l.floorLevel,
                        i.status,
                        l.latitude,
                        l.longitude,
                        l.dateTime,
                        i.officerResponded,
                        i.relocatedLocation,
                        p.emergencyContactPerson,
                        p.emergencyContactNumber
                    FROM incident_logs_table i
                    JOIN (
                        SELECT
                            locationID,
                            MAX(CONCAT(alertDateTime, '|', alertID)) AS latestKey
                        FROM incident_logs_table
                        GROUP BY locationID
                    ) latest ON latest.locationID = i.locationID
                        AND CONCAT(i.alertDateTime, '|', i.alertID) = latest.latestKey
                    JOIN location_table l ON i.locationID = l.locationID
                    JOIN user_table u ON l.userID = u.userID
                    LEFT JOIN pwd_profiles_table p ON l.userID = p.userID
                    WHERE LOWER(TRIM(i.status)) IN ('pending', 'ongoing')
                    ORDER BY FIELD(LOWER(TRIM(i.status)), 'ongoing', 'pending', 'resolved'), i.alertDateTime DESC
                """.trimIndent()
            }
            statement = connection.prepareStatement(query)
            if (!currentOfficerName.isNullOrBlank()) {
                statement.setString(1, currentOfficerName)
            }
            resultSet = statement.executeQuery()

            while (resultSet.next()) {
                val relocatedLocation = resultSet.getString("relocatedLocation") ?: ""
                val assistanceType =
                    if (relocatedLocation.contains("WHEELCHAIR FALL DETECTED", ignoreCase = true)) {
                        "FALL_DETECTION"
                    } else {
                        "MANUAL"
                    }

                val locationItem = LocationItem(
                    resultSet.getString("locationID") ?: "",
                    resultSet.getString("userID") ?: "",
                    resultSet.getString("fullName") ?: "",
                    resultSet.getString("floorLevel") ?: "",
                    resultSet.getString("status") ?: "",
                    resultSet.getDouble("latitude"),
                    resultSet.getDouble("longitude"),
                    resultSet.getString("dateTime") ?: "",
                    resultSet.getString("officerResponded") ?: "",
                    resultSet.getString("deviceID") ?: "",
                    assistanceType,
                    resultSet.getString("emergencyContactPerson"),
                    resultSet.getString("emergencyContactNumber")
                )
                pendingItems.add(locationItem)
            }
            return pendingItems

        } catch (e: SQLException) {
            e.printStackTrace()
            Log.e("MySQLHelper", "Error fetching pending items: ${e.message}")
            return pendingItems

        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getFullNameByUserName(userName: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT fullName FROM user_table WHERE userName = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, userName)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("fullName")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun insertFeedback(userID: String, fbDescription: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            // Generate a random feedbackID
            val feedbackID =
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()

            // Get current datetime in UTC+8 and format it to 24-hour format
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val query =
                "INSERT INTO feedback_table (feedbackID, userID, fbDescription, createdOn) VALUES (?, ?, ?, ?)"
            statement = connection.prepareStatement(query)
            statement.setString(1, feedbackID)
            statement.setString(2, userID)
            statement.setString(3, fbDescription)
            statement.setString(4, currentDateTime)

            val rowsAffected = statement.executeUpdate()
            Log.d("MySQLHelper", "Feedback inserted: $rowsAffected rows affected.")
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun fetchUserEmailByEmail(email: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT email FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("email")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getUserCreationDate(userID: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT createdOn FROM user_table WHERE userID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, userID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("createdOn")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun insertLocationData(
        context: Context,
        userID: String,
        deviceID: String,
        fullName: String
    ): Boolean {
        var connection: Connection? = null
        var locationStmt: PreparedStatement? = null
        var incidentStmt: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            // generate a random locationID
            val locationID =
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()

            // get current datetime in UTC+8 and format it to 24-hour format
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))            // Fixed latitude, longitude, and floorLevel values for testing
            val latitude = 14.243667
            val longitude = 121.111429
            val floorLevel = "Einstein Building Ground Floor"

            val locationQuery = """
                INSERT INTO location_table (locationID, userID, deviceID, fullName, dateTime, latitude, longitude, floorLevel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            locationStmt = connection.prepareStatement(locationQuery)
            locationStmt.setString(1, locationID)
            locationStmt.setString(2, userID)
            locationStmt.setString(3, deviceID)
            locationStmt.setString(4, fullName)
            locationStmt.setString(5, currentDateTime)
            locationStmt.setDouble(6, latitude)
            locationStmt.setDouble(7, longitude)
            locationStmt.setString(8, floorLevel)

            val locationRows = locationStmt.executeUpdate()
            Log.d("MySQLHelper", "Location data inserted: $locationRows rows affected.")

            val alertID = generateAlertID()
            val incidentQuery = """
                INSERT INTO incident_logs_table (alertID, userID, deviceID, locationID, status, alertDateTime)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            incidentStmt = connection.prepareStatement(incidentQuery)
            incidentStmt.setString(1, alertID)
            incidentStmt.setString(2, userID)
            incidentStmt.setString(3, deviceID)
            incidentStmt.setString(4, locationID)
            incidentStmt.setString(5, "pending")
            incidentStmt.setString(6, currentDateTime)

            val incidentRows = incidentStmt.executeUpdate()
            Log.d("MySQLHelper", "Incident data inserted: $incidentRows rows affected.")

            if (locationRows > 0 && incidentRows > 0) {
                upsertLiveGPS(userID, latitude, longitude, 0f)

                val intent = Intent("com.capstone.navicamp.DATA_CHANGED")
                intent.setClassName(
                    "com.capstone.navicamp",
                    "com.capstone.navicamp.DataChangeReceiver"
                )
                context.sendBroadcast(intent)
                SmartPollingManager.getInstance().triggerFastUpdate()
            }

            locationRows > 0 && incidentRows > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            locationStmt?.close()
            incidentStmt?.close()
            connection?.close()
        }
    }

    fun getUserTypeByUserName(userName: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT userType FROM user_table WHERE userName = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, userName)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("userType")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getUserIDByEmail(email: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT userID FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("userID")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getDisabilityTypeByUserID(userID: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT disabilityType FROM pwd_profiles_table WHERE userID = ? LIMIT 1"
            statement = connection.prepareStatement(query)
            statement.setString(1, userID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("disabilityType")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getSafetyOfficerPositionByUserID(userID: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT position FROM safety_officer_profiles_table WHERE userID = ? LIMIT 1"
            statement = connection.prepareStatement(query)
            statement.setString(1, userID)
            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getString("position")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getSystemRoleByUserID(userID: String): String? {
        val position = getSafetyOfficerPositionByUserID(userID)
        if (!position.isNullOrBlank()) {
            return position.trim()
        }
        return getDisabilityTypeByUserID(userID)
    }

    fun isSafetyOfficerAdmin(userID: String): Boolean {
        val position = getSafetyOfficerPositionByUserID(userID) ?: return false
        return position.trim().equals("admin", ignoreCase = true)
    }

    fun getVerificationGeneratorAccess(userID: String): VerificationGeneratorAccess? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            val userIDInt = userID.toIntOrNull() ?: return null
            connection = getConnection() ?: return null

            val query = """
                SELECT
                    u.userType AS campusAffiliation,
                    u.department AS department,
                    COALESCE(s.position, '') AS position
                FROM user_table u
                LEFT JOIN safety_officer_profiles_table s ON u.userID = s.userID
                WHERE u.userID = ?
                LIMIT 1
            """.trimIndent()

            statement = connection.prepareStatement(query)
            statement.setInt(1, userIDInt)
            resultSet = statement.executeQuery()

            if (!resultSet.next()) {
                return null
            }

            val campusAffiliation = resultSet.getString("campusAffiliation")?.trim().orEmpty()
            val department = resultSet.getString("department")?.trim().orEmpty()
            val position = resultSet.getString("position")?.trim().orEmpty()
            val isAdmin = campusAffiliation.equals("Employee", ignoreCase = true) &&
                position.equals("Admin", ignoreCase = true)

            VerificationGeneratorAccess(
                campusAffiliation = campusAffiliation,
                department = department,
                position = position,
                isAdmin = isAdmin
            )
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun validateUserPasswordByUserID(userID: String, password: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            val userIDInt = userID.toIntOrNull() ?: return false
            connection = getConnection() ?: return false

            val query = "SELECT password, verified FROM user_table WHERE userID = ? LIMIT 1"
            statement = connection.prepareStatement(query)
            statement.setInt(1, userIDInt)
            resultSet = statement.executeQuery()

            if (!resultSet.next()) {
                return false
            }

            val storedPassword = resultSet.getString("password")
            val verified = resultSet.getInt("verified")
            hashPassword(password) == storedPassword && verified == 1
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    suspend fun issueVerificationQrNonce(
        nonce: String,
        staffUserID: String,
        roleToken: String,
        expiresAt: ZonedDateTime
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var createStatement: PreparedStatement? = null
            var insertStatement: PreparedStatement? = null
            try {
                val staffUserIDInt = staffUserID.toIntOrNull() ?: return@withContext false
                connection = getConnection() ?: return@withContext false

                val createQuery = """
                    CREATE TABLE IF NOT EXISTS verification_qr_tokens (
                        tokenNonce VARCHAR(64) PRIMARY KEY,
                        staffUserID INT NOT NULL,
                        roleToken VARCHAR(32) NOT NULL,
                        issuedAt DATETIME NOT NULL,
                        expiresAt DATETIME NOT NULL,
                        consumedAt DATETIME NULL
                    )
                """.trimIndent()
                createStatement = connection.prepareStatement(createQuery)
                createStatement.execute()

                val now = ZonedDateTime.now(ZoneId.of("UTC+8"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                val expiry = expiresAt.withZoneSameInstant(ZoneId.of("UTC+8"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                val insertQuery = """
                    INSERT INTO verification_qr_tokens (tokenNonce, staffUserID, roleToken, issuedAt, expiresAt, consumedAt)
                    VALUES (?, ?, ?, ?, ?, NULL)
                """.trimIndent()
                insertStatement = connection.prepareStatement(insertQuery)
                insertStatement.setString(1, nonce)
                insertStatement.setInt(2, staffUserIDInt)
                insertStatement.setString(3, roleToken)
                insertStatement.setString(4, now)
                insertStatement.setString(5, expiry)
                insertStatement.executeUpdate() > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                insertStatement?.close()
                createStatement?.close()
                connection?.close()
            }
        }
    }

    suspend fun consumeVerificationQrNonce(
        nonce: String,
        staffUserID: String,
        roleToken: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            try {
                val staffUserIDInt = staffUserID.toIntOrNull() ?: return@withContext false
                connection = getConnection() ?: return@withContext false

                val query = """
                    UPDATE verification_qr_tokens
                    SET consumedAt = ?
                    WHERE tokenNonce = ?
                      AND staffUserID = ?
                      AND roleToken = ?
                      AND consumedAt IS NULL
                      AND expiresAt >= ?
                """.trimIndent()

                val now = ZonedDateTime.now(ZoneId.of("UTC+8"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                statement = connection.prepareStatement(query)
                statement.setString(1, now)
                statement.setString(2, nonce)
                statement.setInt(3, staffUserIDInt)
                statement.setString(4, roleToken)
                statement.setString(5, now)

                statement.executeUpdate() > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    fun isVerificationQrNonceConsumed(
        nonce: String,
        staffUserID: String,
        roleToken: String
    ): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            val staffUserIDInt = staffUserID.toIntOrNull() ?: return false
            connection = getConnection() ?: return false

            val query = """
                SELECT consumedAt
                FROM verification_qr_tokens
                WHERE tokenNonce = ?
                  AND staffUserID = ?
                  AND roleToken = ?
                LIMIT 1
            """.trimIndent()

            statement = connection.prepareStatement(query)
            statement.setString(1, nonce)
            statement.setInt(2, staffUserIDInt)
            statement.setString(3, roleToken)
            resultSet = statement.executeQuery()

            resultSet.next() && resultSet.getString("consumedAt") != null
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getSafetyOfficerOnDutyStatus(userID: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            val parsedUserID = userID.toIntOrNull() ?: return false
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "SELECT isOnDuty FROM safety_officer_profiles_table WHERE userID = ? LIMIT 1"
            statement = connection.prepareStatement(query)
            statement.setInt(1, parsedUserID)
            resultSet = statement.executeQuery()
            resultSet.next() && resultSet.getInt("isOnDuty") == 1
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun updateSafetyOfficerOnDutyStatus(userID: String, isOnDuty: Boolean): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            val parsedUserID = userID.toIntOrNull() ?: return false
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "UPDATE safety_officer_profiles_table SET isOnDuty = ? WHERE userID = ?"
            statement = connection.prepareStatement(query)
            statement.setInt(1, if (isOnDuty) 1 else 0)
            statement.setInt(2, parsedUserID)
            statement.executeUpdate() > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun updateSafetyOfficerDispatchedStatus(userID: String, isDispatched: Boolean): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            val parsedUserID = userID.toIntOrNull() ?: return false
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "UPDATE safety_officer_profiles_table SET isDispatched = ? WHERE userID = ?"
            statement = connection.prepareStatement(query)
            statement.setInt(1, if (isDispatched) 1 else 0)
            statement.setInt(2, parsedUserID)
            statement.executeUpdate() > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun doesUserIDExist(userID: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            val userIDInt = userID.toIntOrNull() ?: return false
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "SELECT COUNT(*) AS count FROM user_table WHERE userID = ?"
            statement = connection.prepareStatement(query)
            statement.setInt(1, userIDInt)
            resultSet = statement.executeQuery()
            resultSet.next() && resultSet.getInt("count") > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getUserFullNameByUserID(userID: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            // First, let's try a simple query like the working loginUser method
            val query = "SELECT fullName FROM user_table WHERE userID = ?"
            statement = connection.prepareStatement(query)

            // Convert userID string to integer like the database expects
            try {
                statement.setInt(1, userID.toInt())
            } catch (e: NumberFormatException) {
                Log.e("MySQLHelper", "Invalid userID format: $userID")
                return null
            }

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val fullName = resultSet.getString("fullName")
                Log.d("MySQLHelper", "Found fullName: '$fullName' for userID: $userID")
                fullName
            } else {
                Log.w("MySQLHelper", "No user found with userID: $userID")
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            Log.e("MySQLHelper", "SQL Error getting fullName for userID $userID: ${e.message}")
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    /**
     * Resolves an incident with optional relocated location.
     * @param relocatedLocation User's new location in format: "Building - Floor - Room"
     *                          Example: "Einstein Building - Fifth Floor - E515"
     */
    suspend fun resolveIncident(
        locationID: String,
        status: String,
        officerName: String,
        relocatedLocation: String? = null,
        officerUserID: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        try {
            connection = getConnection()
            if (connection == null) return@withContext false

            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            // Update with relocated location
            val query = if (relocatedLocation != null) {
                "UPDATE incident_logs_table SET status = ?, officerResponded = ?, relocatedLocation = ?, resolvedOn = ? WHERE locationID = ?"
            } else {
                "UPDATE incident_logs_table SET status = ?, officerResponded = ?, resolvedOn = ? WHERE locationID = ?"
            }
            statement = connection.prepareStatement(query)
            statement.setString(1, status)
            statement.setString(2, officerName)
            if (relocatedLocation != null) {
                statement.setString(3, relocatedLocation)
                statement.setString(4, currentDateTime)
                statement.setString(5, locationID)
            } else {
                statement.setString(3, currentDateTime)
                statement.setString(4, locationID)
            }
            val rowsAffected = statement.executeUpdate()
            if (rowsAffected > 0) {
                val normalizedStatus = status.trim().lowercase(Locale.US)
                val resolvedOfficerUserID = officerUserID?.takeIf { it.isNotBlank() } ?: getUserIDByFullName(officerName)
                if (!resolvedOfficerUserID.isNullOrBlank()) {
                    updateSafetyOfficerDispatchedStatus(resolvedOfficerUserID, false)
                }

                if (normalizedStatus == "resolved" || normalizedStatus == "false alarm") {
                    val userID = getUserIDByLocationID(locationID)
                    if (!userID.isNullOrBlank()) {
                        deleteLiveGPS(userID)
                    }
                    if (!resolvedOfficerUserID.isNullOrBlank()) {
                        deleteLiveGPS(resolvedOfficerUserID)
                    }
                }

                SmartPollingManager.getInstance().triggerFastUpdate()

                // Send FCM notification about assistance resolution
                sendAssistanceResolvedNotification(
                    locationID,
                    status,
                    officerName,
                    relocatedLocation
                )
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    suspend fun submitIncidentReport(
        locationID: String,
        relocatedLocation: String,
        actionFA: String? = null,
        actionINFO: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        try {
            connection = getConnection()
            if (connection == null) return@withContext false

            val hasOtherFields = !actionFA.isNullOrBlank() || !actionINFO.isNullOrBlank()
            val query = if (hasOtherFields) {
                "UPDATE incident_logs_table SET relocatedLocation = ?, actionFA = ?, actionINFO = ? WHERE locationID = ?"
            } else {
                "UPDATE incident_logs_table SET relocatedLocation = ? WHERE locationID = ?"
            }
            statement = connection.prepareStatement(query)
            statement.setString(1, relocatedLocation)
            if (hasOtherFields) {
                statement.setString(2, actionFA ?: "")
                statement.setString(3, actionINFO ?: "")
                statement.setString(4, locationID)
            } else {
                statement.setString(2, locationID)
            }

            val rowsAffected = statement.executeUpdate()
            if (rowsAffected > 0) {
                SmartPollingManager.getInstance().triggerFastUpdate()
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun insertAssistanceRequestFromDevice(
        context: Context,
        userID: String,
        fullName: String,
        deviceID: String,
        latitude: Double,
        longitude: Double,
        floorLevel: String
    ): Boolean {
        var connection: Connection? = null
        var locationStmt: PreparedStatement? = null
        var incidentStmt: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            // Generate a random locationID
            val locationID =
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()

            // Get current datetime in Philippine time (UTC+8)
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            // Insert into location_table (fullName removed - will be referenced via userID)
            val locationQuery = """
                INSERT INTO location_table (locationID, userID, deviceID, latitude, longitude, floorLevel, dateTime)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            locationStmt = connection.prepareStatement(locationQuery)
            locationStmt.setString(1, locationID)
            locationStmt.setString(2, userID)
            locationStmt.setString(3, deviceID)
            locationStmt.setDouble(4, latitude)
            locationStmt.setDouble(5, longitude)
            locationStmt.setString(6, floorLevel)
            locationStmt.setString(7, currentDateTime)

            val locationRows = locationStmt.executeUpdate()
            Log.d("MySQLHelper", "Location data inserted: $locationRows rows affected.")

            // Insert into incident_logs_table to create the assistance request
            val alertID = generateAlertID()
            val incidentQuery = """
                INSERT INTO incident_logs_table (alertID, userID, deviceID, locationID, status, alertDateTime)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            incidentStmt = connection.prepareStatement(incidentQuery)
            incidentStmt.setString(1, alertID)
            incidentStmt.setString(2, userID)
            incidentStmt.setString(3, deviceID)
            incidentStmt.setString(4, locationID)
            incidentStmt.setString(5, "pending")
            incidentStmt.setString(6, currentDateTime)

            val incidentRows = incidentStmt.executeUpdate()
            Log.d("MySQLHelper", "Incident data inserted: $incidentRows rows affected.")

            if (locationRows > 0 && incidentRows > 0) {
                upsertLiveGPS(userID, latitude, longitude, 0f)

                val intent = Intent("com.capstone.navicamp.DATA_CHANGED")
                intent.setClassName(
                    "com.capstone.navicamp",
                    "com.capstone.navicamp.DataChangeReceiver"
                )
                context.sendBroadcast(intent)
                SmartPollingManager.getInstance().triggerFastUpdate()

                // Send FCM notification to all Security Officers about new assistance request
                sendAssistanceRequestNotification(locationID, userID)
            }
            locationRows > 0 && incidentRows > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            Log.e("MySQLHelper", "SQL Error in insertAssistanceRequestFromDevice: ${e.message}")
            false
        } finally {
            locationStmt?.close()
            incidentStmt?.close()
            connection?.close()
        }
    }

    fun updateDeviceConnectionStatus(
        deviceID: String,
        newUserID: String?, // Renamed for clarity
        newConnectedUntil: String? // Renamed for clarity
    ): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection() ?: return false
            val query = if (newUserID != null) {
                // CONNECTING: Set status to in_use, userID to current user, and time to NULL
                "UPDATE devices_table SET userID = ?, connectedUntil = NULL, status = 'in_use' " +
                        "WHERE deviceID = ? AND status = 'available'"
            } else {
                // DISCONNECTING: Set everything back to default
                "UPDATE devices_table SET userID = NULL, connectedUntil = NULL, status = 'available' WHERE deviceID = ?"
            }

            statement = connection.prepareStatement(query)
            if (newUserID != null) {
                statement.setString(1, newUserID)
                statement.setString(2, deviceID)
            } else {
                statement.setString(1, deviceID)
            }

            val rowsAffected = statement.executeUpdate()
            rowsAffected > 0
        } catch (e: SQLException) {
            Log.e(
                "MySQLHelper",
                "Error updating device connection status for $deviceID: ${e.message}",
                e
            )
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    // Generate a new userID starting from 20250001 and auto-incrementing
    fun generateUserID(): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            // Find the first available userID starting from 20250001
            var currentID = 20250001L
            val checkQuery = "SELECT COUNT(*) AS count FROM user_table WHERE userID = ?"

            while (currentID <= 20259999) {
                statement = connection.prepareStatement(checkQuery)
                statement.setString(1, currentID.toString())
                resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val count = resultSet.getInt("count")
                    if (count == 0) {
                        // Found an available ID
                        Log.d("MySQLHelper", "Found available userID: $currentID")
                        break
                    }
                }

                // Close resources before next iteration
                resultSet?.close()
                statement?.close()

                // Try next ID
                currentID++
            }

            val nextID = if (currentID <= 20259999) {
                currentID
            } else {
                Log.e("MySQLHelper", "All userIDs from 20250001 to 20259999 are taken")
                null
            }

            if (nextID != null) {
                Log.d("MySQLHelper", "Generated unique userID: $nextID")
                nextID.toString()
            } else {
                null
            }

        } catch (e: SQLException) {
            e.printStackTrace()
            Log.e("MySQLHelper", "Error generating userID: ${e.message}")
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }    // Insert user data into the database

    fun insertUser(
        userID: String,
        fullName: String,
        userType: String,
        email: String,
        contactNumber: String,
        password: String,
        proofPicture: String?
    ): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            // Check if the email already exists
            val checkQuery = "SELECT COUNT(*) AS count FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(checkQuery)
            statement.setString(1, email)
            resultSet = statement.executeQuery()
            if (resultSet.next() && resultSet.getInt("count") > 0) {
                println("Email already exists.")
                return false
            }            // Get current datetime in UTC+8 and format it to 24-hour format
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val query =
                "INSERT INTO user_table (userID, fullName, userType, email, contactNumber, password, proofPicture, createdOn) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            statement = connection.prepareStatement(query)
            statement.setString(1, userID)
            statement.setString(2, fullName)
            statement.setString(3, userType)
            statement.setString(4, email)
            statement.setString(5, contactNumber)
            statement.setString(6, password)
            statement.setString(7, proofPicture)
            statement.setString(8, currentDateTime)

            val rowsAffected = statement.executeUpdate()
            Log.d("MySQLHelper", "User inserted: $rowsAffected rows affected.")
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun insertUserWithPwdProfile(
        userID: String,
        fullName: String,
        campusAffiliation: String,
        department: String,
        email: String,
        contactNumber: String,
        password: String,
        disabilityType: String
    ): Boolean {
        var connection: Connection? = null
        var checkStatement: PreparedStatement? = null
        var userStatement: PreparedStatement? = null
        var pwdProfileStatement: PreparedStatement? = null
        var safetyProfileStatement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            connection.autoCommit = false

            val checkQuery = "SELECT COUNT(*) AS count FROM user_table WHERE email = ?"
            checkStatement = connection.prepareStatement(checkQuery)
            checkStatement.setString(1, email)
            resultSet = checkStatement.executeQuery()
            if (resultSet.next() && resultSet.getInt("count") > 0) {
                println("Email already exists.")
                connection.rollback()
                return false
            }

            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val insertUserQuery = """
                INSERT INTO user_table (userID, fullName, userType, department, email, contactNumber, password, proofPicture, createdOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            userStatement = connection.prepareStatement(insertUserQuery)
            userStatement.setString(1, userID)
            userStatement.setString(2, fullName)
            userStatement.setString(3, campusAffiliation)
            userStatement.setString(4, department)
            userStatement.setString(5, email)
            userStatement.setString(6, contactNumber)
            userStatement.setString(7, password)
            userStatement.setString(8, null)
            userStatement.setString(9, currentDateTime)
            val userRowsAffected = userStatement.executeUpdate()
            if (userRowsAffected <= 0) {
                connection.rollback()
                return false
            }

            val normalizedRole = disabilityType.trim().lowercase()
            val isSafetyOfficerRole = normalizedRole.contains("safety") ||
                normalizedRole.contains("security") ||
                normalizedRole.contains("officer")

            if (isSafetyOfficerRole) {
                val insertSafetyProfileQuery = """
                    INSERT INTO safety_officer_profiles_table (userID, position)
                    VALUES (?, ?)
                """.trimIndent()
                safetyProfileStatement = connection.prepareStatement(insertSafetyProfileQuery)
                safetyProfileStatement.setString(1, userID)
                safetyProfileStatement.setString(2, "safety officer")
                val safetyRowsAffected = safetyProfileStatement.executeUpdate()
                if (safetyRowsAffected <= 0) {
                    connection.rollback()
                    return false
                }
            } else {
                val resolvedDisabilityType = resolveDisabilityTypeForDatabase(connection, disabilityType)
                if (resolvedDisabilityType == null) {
                    Log.e(
                        "MySQLHelper",
                        "Unable to map disability type '$disabilityType' to pwd_profiles_table ENUM values."
                    )
                    connection.rollback()
                    return false
                }

                val insertPwdProfileQuery = """
                    INSERT INTO pwd_profiles_table (userID, disabilityType)
                    VALUES (?, ?)
                """.trimIndent()
                pwdProfileStatement = connection.prepareStatement(insertPwdProfileQuery)
                pwdProfileStatement.setString(1, userID)
                pwdProfileStatement.setString(2, resolvedDisabilityType)
                val profileRowsAffected = pwdProfileStatement.executeUpdate()
                if (profileRowsAffected <= 0) {
                    connection.rollback()
                    return false
                }
            }

            connection.commit()
            true
        } catch (e: SQLException) {
            e.printStackTrace()
            try {
                connection?.rollback()
            } catch (rollbackException: SQLException) {
                rollbackException.printStackTrace()
            }
            false
        } finally {
            resultSet?.close()
            checkStatement?.close()
            userStatement?.close()
            pwdProfileStatement?.close()
            safetyProfileStatement?.close()
            try {
                connection?.autoCommit = true
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            connection?.close()
        }
    }

    private fun resolveDisabilityTypeForDatabase(connection: Connection, selectedType: String): String? {
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            statement = connection.prepareStatement("SHOW COLUMNS FROM pwd_profiles_table LIKE 'disabilityType'")
            resultSet = statement.executeQuery()
            if (!resultSet.next()) {
                return selectedType
            }

            val typeDefinition = resultSet.getString("Type") ?: return selectedType
            val enumValues = Regex("'((?:\\\\'|[^'])*)'")
                .findAll(typeDefinition)
                .map { it.groupValues[1].replace("\\'", "'") }
                .toList()

            if (enumValues.isEmpty()) {
                return selectedType
            }

            enumValues.firstOrNull { it.equals(selectedType, ignoreCase = true) }?.let { return it }

            val normalizedSelected = selectedType.trim().lowercase()
            val keyword = when {
                normalizedSelected.contains("tempor") -> "tempor"
                normalizedSelected.contains("perman") -> "perman"
                normalizedSelected.contains("safety") ||
                    normalizedSelected.contains("security") ||
                    normalizedSelected.contains("officer") -> "officer"
                else -> null
            }

            if (keyword != null) {
                enumValues.firstOrNull { it.lowercase().contains(keyword) }?.let { return it }
            }

            null
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
        }
    }

    // Update user with userID
    suspend fun updateUserWithUserID(
        newFullName: String,
        newSchoolID: String,
        newEmail: String,
        newContactNumber: String,
        newEmergencyName: String,
        newEmergencyNumber: String,
        userID: String,
        updatedOn: String,
        newDepartment: String = ""
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            try {
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }

                val query = StringBuilder("UPDATE user_table u LEFT JOIN pwd_profiles_table p ON u.userID = p.userID SET u.updatedOn = ?")
                if (newFullName.isNotBlank()) {
                    query.append(", u.fullName = ?")
                }
                if (newSchoolID.isNotBlank()) {
                    query.append(", p.schoolID = ?")
                }
                if (newEmail.isNotBlank()) {
                    query.append(", u.email = ?")
                }
                if (newContactNumber.isNotBlank()) {
                    query.append(", u.contactNumber = ?")
                }
                if (newDepartment.isNotBlank()) {
                    query.append(", u.department = ?")
                }
                if (newEmergencyName.isNotBlank()) {
                    query.append(", p.emergencyContactPerson  = ?")
                }
                if (newEmergencyNumber.isNotBlank()) {
                    query.append(", p.emergencyContactNumber  = ?")
                }
                query.append(" WHERE u.userID = ?")

                statement = connection.prepareStatement(query.toString())
                statement.setString(1, updatedOn)
                var index = 2
                if (newFullName.isNotBlank()) {
                    statement.setString(index++, newFullName)
                }
                if (newSchoolID.isNotBlank()) {
                    statement.setString(index++, newSchoolID)
                }
                if (newEmail.isNotBlank()) {
                    statement.setString(index++, newEmail)
                }
                if (newContactNumber.isNotBlank()) {
                    statement.setString(index++, newContactNumber)
                }
                if (newDepartment.isNotBlank()) {
                    statement.setString(index++, newDepartment)
                }
                if (newEmergencyName.isNotBlank()) {
                    statement.setString(index++, newEmergencyName)
                }
                if (newEmergencyNumber.isNotBlank()) {
                    statement.setString(index++, newEmergencyNumber)
                }
                statement.setString(index, userID)

                Log.d("MySQLHelper", "Executing query: $query")
                Log.d(
                    "MySQLHelper",
                    "Parameters: updatedOn=$updatedOn, newFullName=$newFullName, newSchoolID=$newSchoolID, newEmail=$newEmail, newContactNumber=$newContactNumber, newDepartment=$newDepartment, newEmergencyName=$newEmergencyName, newEmergencyNumber=$newEmergencyNumber, userID=$userID"
                )
                val rowsAffected = statement.executeUpdate()
                Log.d("MySQLHelper", "Rows affected: $rowsAffected")
                rowsAffected > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    // Update officer with userID
    suspend fun updateOfficerWithUserID(
        newFullName: String,
        newEmail: String,
        newContactNumber: String,
        userID: String,
        updatedOn: String,
        newDepartment: String = ""
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            try {
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }

                val query = StringBuilder("UPDATE user_table SET updatedOn = ?")
                if (newFullName.isNotBlank()) {
                    query.append(", fullName = ?")
                }
                if (newEmail.isNotBlank()) {
                    query.append(", email = ?")
                }
                if (newContactNumber.isNotBlank()) {
                    query.append(", contactNumber = ?")
                }
                if (newDepartment.isNotBlank()) {
                    query.append(", department = ?")
                }
                query.append(" WHERE userID = ?")

                statement = connection.prepareStatement(query.toString())
                statement.setString(1, updatedOn)
                var index = 2
                if (newFullName.isNotBlank()) {
                    statement.setString(index++, newFullName)
                }
                if (newEmail.isNotBlank()) {
                    statement.setString(index++, newEmail)
                }
                if (newContactNumber.isNotBlank()) {
                    statement.setString(index++, newContactNumber)
                }
                if (newDepartment.isNotBlank()) {
                    statement.setString(index++, newDepartment)
                }
                statement.setString(index, userID)

                Log.d("MySQLHelper", "Executing query: $query")
                Log.d(
                    "MySQLHelper",
                    "Parameters: updatedOn=$updatedOn, newFullName=$newFullName, newEmail=$newEmail, newContactNumber=$newContactNumber, newDepartment=$newDepartment, userID=$userID"
                )
                val rowsAffected = statement.executeUpdate()
                Log.d("MySQLHelper", "Rows affected: $rowsAffected")
                rowsAffected > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    suspend fun isContactNumberExists(contactNumber: String, userID: String): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null
            try {
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }

                val query =
                    "SELECT COUNT(*) AS count FROM user_table WHERE contactNumber = ? AND userID != ?"
                statement = connection.prepareStatement(query)
                statement.setString(1, contactNumber)
                statement.setString(2, userID)

                resultSet = statement.executeQuery()
                resultSet.next() && resultSet.getInt("count") > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                resultSet?.close()
                statement?.close()
                connection?.close()
            }
        }
    }

    suspend fun isEmailExists(email: String, userID: String): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null
            try {
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }

                val query =
                    "SELECT COUNT(*) AS count FROM user_table WHERE email = ? AND userID != ?"
                statement = connection.prepareStatement(query)
                statement.setString(1, email)
                statement.setString(2, userID)

                resultSet = statement.executeQuery()
                resultSet.next() && resultSet.getInt("count") > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                resultSet?.close()
                statement?.close()
                connection?.close()
            }
        }
    }

    fun getUserCount(): Int {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return 0
            }

            val query =
                "SELECT COUNT(*) AS count FROM user_table WHERE userType IN ('Temporarily Disabled', 'Permanently Disabled') AND userID IS NOT NULL AND userID != ''"
            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getInt("count")
            } else {
                0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            0
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getDeviceCount(): Int {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return 0
            }

            val query = "SELECT COUNT(*) AS count FROM devices_table"
            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getInt("count")
            } else {
                0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            0
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun validateUserWithType(email: String, password: String, userType: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "SELECT password, userType, verified FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val storedPassword = resultSet.getString("password")
                val storedUserType = resultSet.getString("userType")
                val verified = resultSet.getInt("verified")
                val hashedPassword = hashPassword(password)
                val isValidUserType = storedUserType == userType
                hashedPassword == storedPassword && isValidUserType && verified == 1 // Verify the password, userType, and check if verified
            } else {
                false
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun validateUser(email: String, password: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "SELECT password, verified FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val storedPassword = resultSet.getString("password")
                val verified = resultSet.getInt("verified")
                val hashedPassword = hashPassword(password)
                storedPassword == hashedPassword && verified == 1 // Compare the hashed password with the stored password and check if verified
            } else {
                false
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest(password.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    fun getLocationItemById(locationID: String): LocationItem {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                throw SQLException("Database connection failed.")
            }

            val query = """
                SELECT
                    l.locationID,
                    l.userID,
                    l.deviceID,
                    u.fullName,
                    l.floorLevel,
                    i.status,
                    l.latitude,
                    l.longitude,
                    l.dateTime,
                    i.officerResponded,
                    i.relocatedLocation,
                    u.contactNumber,
                    p.emergencyContactPerson,
                    p.emergencyContactNumber
                FROM incident_logs_table i
                JOIN location_table l ON i.locationID = l.locationID
                JOIN user_table u ON l.userID = u.userID
                LEFT JOIN pwd_profiles_table p ON l.userID = p.userID
                WHERE l.locationID = ?
                ORDER BY i.alertDateTime DESC
                LIMIT 1
            """.trimIndent()
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val relocatedLocation = resultSet.getString("relocatedLocation") ?: ""
                val assistanceType =
                    if (relocatedLocation.contains("WHEELCHAIR FALL DETECTED", ignoreCase = true)) {
                        "FALL_DETECTION"
                    } else {
                        "MANUAL"
                    }

                LocationItem(
                    locationID = resultSet.getString("locationID") ?: "",
                    userID = resultSet.getString("userID") ?: "",
                    deviceID = resultSet.getString("deviceID") ?: "",
                    fullName = resultSet.getString("fullName") ?: "",
                    floorLevel = resultSet.getString("floorLevel") ?: "",
                    status = resultSet.getString("status") ?: "",
                    latitude = resultSet.getDouble("latitude"),
                    longitude = resultSet.getDouble("longitude"),
                    dateTime = resultSet.getString("dateTime") ?: "",
                    officerName = resultSet.getString("officerResponded") ?: "",
                    assistanceType = assistanceType,
                    contactNumber = resultSet.getString("contactNumber"),
                    emergencyContactPerson = resultSet.getString("emergencyContactPerson"),
                    emergencyContactNumber = resultSet.getString("emergencyContactNumber")
                )
            } else {
                throw SQLException("Location item not found.")
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            throw e
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun loginUser(email: String, password: String): UserData? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }
            val query = """
            SELECT userID, fullName, userType, email, contactNumber, createdOn, updatedOn, proofPicture, password, verified
            FROM user_table
            WHERE email = ?
        """
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val storedPassword = resultSet.getString("password")
                val hashedPassword = hashPassword(password)
                if (storedPassword == hashedPassword) {
                    UserData(
                        resultSet.getString("userID") ?: "",
                        resultSet.getString("fullName") ?: "",
                        resultSet.getString("userType") ?: "",
                        resultSet.getString("email") ?: "",
                        resultSet.getString("contactNumber") ?: "",
                        resultSet.getString("createdOn") ?: "",
                        resultSet.getString("updatedOn") ?: "",
                        resultSet.getString("proofPicture") ?: "",
                        resultSet.getInt("verified") // Add this line
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun isTemporaryUserAccessExpired(userID: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        return try {
            val parsedUserID = userID.toIntOrNull() ?: return false
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = """
                SELECT disabilityType, expiryDate
                FROM pwd_profiles_table
                WHERE userID = ?
                LIMIT 1
            """.trimIndent()
            statement = connection.prepareStatement(query)
            statement.setInt(1, parsedUserID)
            resultSet = statement.executeQuery()

            if (!resultSet.next()) {
                return false
            }

            val disabilityType = resultSet.getString("disabilityType")?.trim()?.lowercase(Locale.US).orEmpty()
            if (!disabilityType.contains("tempor")) {
                return false
            }

            val expiryDateRaw = resultSet.getString("expiryDate")?.trim().orEmpty()
            if (expiryDateRaw.isBlank()) {
                return false
            }

            val expiryDate = try {
                LocalDate.parse(expiryDateRaw, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                return false
            }
            val today = LocalDate.now(ZoneId.of("UTC+8"))
            !expiryDate.isAfter(today)
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    suspend fun updateUserPasswordByEmail(email: String, newPassword: String): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            try {
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }

                val query = "UPDATE user_table SET password = ? WHERE email = ?"
                statement = connection.prepareStatement(query)
                statement.setString(1, newPassword)
                statement.setString(2, email)

                val rowsAffected = statement.executeUpdate()
                rowsAffected > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    fun updateStatus(locationID: String, newStatus: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "UPDATE incident_logs_table SET status = ? WHERE locationID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, newStatus)
            statement.setString(2, locationID)

            val rowsAffected = statement.executeUpdate()
            if (rowsAffected > 0) {
                val normalizedStatus = newStatus.trim().lowercase(Locale.US)
                if (normalizedStatus == "resolved" || normalizedStatus == "false alarm") {
                    val userID = getUserIDByLocationID(locationID)
                    if (!userID.isNullOrBlank()) {
                        deleteLiveGPS(userID)
                    }
                }
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun updateIncidentResponse(
        locationID: String,
        status: String,
        officerName: String,
        officerUserID: String? = null
    ): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query =
                "UPDATE incident_logs_table SET status = ?, officerResponded = ? WHERE locationID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, status)
            statement.setString(2, officerName)
            statement.setString(3, locationID)

            val rowsAffected = statement.executeUpdate()
            Log.d("MySQLHelper", "Updated incident response: $rowsAffected rows affected")
            if (rowsAffected > 0) {
                val resolvedOfficerUserID = officerUserID?.takeIf { it.isNotBlank() } ?: getUserIDByFullName(officerName)
                if (!resolvedOfficerUserID.isNullOrBlank()) {
                    updateSafetyOfficerDispatchedStatus(resolvedOfficerUserID, true)
                }

                SmartPollingManager.getInstance().triggerFastUpdate()

                // Send FCM notification about officer response
                sendOfficerResponseNotification(locationID, officerName)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            Log.e("MySQLHelper", "Error updating incident response: ${e.message}")
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun insertEmergencyAlerts() {
        var connection: Connection? = null
        var selectStmt: PreparedStatement? = null
        var insertStmt: PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return
            }

            val selectQuery = """
                SELECT l.userID, l.deviceID, l.locationID, l.dateTime
                FROM location_table l
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM incident_logs_table i
                    WHERE i.locationID = l.locationID
                )
            """.trimIndent()

            selectStmt = connection.prepareStatement(selectQuery)
            resultSet = selectStmt.executeQuery()

            val insertQuery = """
                INSERT INTO incident_logs_table
                (alertID, userID, deviceID, locationID, alertDateTime, status, relocatedLocation, officerResponded, resolvedOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            insertStmt = connection.prepareStatement(insertQuery)

            while (resultSet.next()) {
                val userID = resultSet.getString("userID")
                val deviceID = resultSet.getString("deviceID")
                val locationID = resultSet.getString("locationID")
                val alertDateTime = resultSet.getTimestamp("dateTime")
                val alertID = generateAlertID()

                insertStmt.setString(1, alertID)
                insertStmt.setString(2, userID)
                insertStmt.setString(3, deviceID)
                insertStmt.setString(4, locationID)
                insertStmt.setTimestamp(5, alertDateTime)
                insertStmt.setString(6, "pending") // default status
                insertStmt.setNull(7, java.sql.Types.VARCHAR) // relocatedLocation NULL
                insertStmt.setNull(8, java.sql.Types.VARCHAR) // officerResponded NULL
                insertStmt.setNull(9, java.sql.Types.TIMESTAMP) // resolvedOn NULL

                insertStmt.addBatch()
            }

            val rowsInsertedArray = insertStmt.executeBatch()
            val totalInserted = rowsInsertedArray.sum()
            println("$totalInserted new rows inserted into incident_logs_table.")

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            resultSet?.close()
            selectStmt?.close()
            insertStmt?.close()
            connection?.close()
        }
    }

    fun getIncidentData(): List<List<String>> {
        val data = mutableListOf<List<String>>()
        insertEmergencyAlerts() // Ensure data is inserted first
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null

        try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return data
            }

            val query = """
                SELECT
                    i.alertID,
                    i.userID,
                    i.deviceID,
                    u.fullName,
                    CONCAT(l.latitude, ', ', l.longitude) AS coordinates,
                    l.floorLevel,
                    i.status,
                    i.alertDateTime,
                    i.resolvedOn,
                    i.officerResponded,
                    i.relocatedLocation,
                    u.userType,
                    u.department,
                    i.actionFA,
                    i.actionINFO
                FROM incident_logs_table i
                JOIN (
                    SELECT
                        locationID,
                        MAX(CONCAT(alertDateTime, '|', alertID)) AS latestKey
                    FROM incident_logs_table
                    GROUP BY locationID
                ) latest ON latest.locationID = i.locationID
                    AND CONCAT(i.alertDateTime, '|', i.alertID) = latest.latestKey
                JOIN user_table u ON i.userID = u.userID
                JOIN location_table l ON i.locationID = l.locationID
            """.trimIndent()

            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()

            while (resultSet.next()) {
                val row = listOf(
                    resultSet.getString("alertID") ?: "",
                    resultSet.getString("userID") ?: "",
                    resultSet.getString("deviceID") ?: "",
                    resultSet.getString("fullName") ?: "",
                    resultSet.getString("coordinates") ?: "",
                    resultSet.getString("floorLevel") ?: "",
                    resultSet.getString("status") ?: "",
                    resultSet.getString("alertDateTime") ?: "",
                    resultSet.getString("resolvedOn") ?: "",
                    resultSet.getString("officerResponded") ?: "",
                    resultSet.getString("relocatedLocation") ?: "",
                    resultSet.getString("userType") ?: "",
                    resultSet.getString("department") ?: "",
                    resultSet.getString("actionFA") ?: "",
                    resultSet.getString("actionINFO") ?: ""
                )
                data.add(row)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }

        return data
    }

//    fun resolveEmergencyAlert(alertID: String): Boolean {
//        var connection: Connection? = null
//        var statement: PreparedStatement? = null
//        return try {
//            connection = getConnection()
//            if (connection == null) {
//                Log.e("MySQLHelper", "Database connection failed.")
//                return false
//            }
//
//            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
//                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//
//            val query = "UPDATE incident_logs_table SET resolvedOn = ? WHERE alertID = ?"
//            statement = connection.prepareStatement(query)
//            statement.setString(1, currentDateTime)
//            statement.setInt(2, alertID.toInt())
//
//            Log.d("MySQLHelper", "Updating alertID: $alertID")
//            val rowsAffected = statement.executeUpdate()
//            Log.d("MySQLHelper", "Query executed: $query with resolvedOn=$currentDateTime, alertID=$alertID")
//            rowsAffected > 0
//        } catch (e: Exception) {
//            Log.e("MySQLHelper", "Error updating resolvedOn", e)
//            false
//        } finally {
//            statement?.close()
//            connection?.close()
//        }
//    }

    fun getUserDataByEmail(email: String): UserData? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }
            val query = """
            SELECT userID, fullName, userType, email, contactNumber, createdOn, updatedOn, proofPicture, verified
            FROM user_table
            WHERE email = ?
        """
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                UserData(
                    resultSet.getString("userID") ?: "",
                    resultSet.getString("fullName") ?: "",
                    resultSet.getString("userType") ?: "",
                    resultSet.getString("email") ?: "",
                    resultSet.getString("contactNumber") ?: "",
                    resultSet.getString("createdOn") ?: "",
                    resultSet.getString("updatedOn") ?: "",
                    resultSet.getString("proofPicture") ?: "",
                    resultSet.getInt("verified") // Add this line
                )
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getUnverifiedUsers(): List<UserData> {
        val unverifiedUsers = mutableListOf<UserData>()
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return unverifiedUsers
            }
            val query = """
            SELECT userID, fullName, userType, email, contactNumber, createdOn, updatedOn, proofPicture, verified
            FROM user_table
            WHERE verified = 0
        """
            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()
            while (resultSet.next()) {
                unverifiedUsers.add(
                    UserData(
                        resultSet.getString("userID") ?: "",
                        resultSet.getString("fullName") ?: "",
                        resultSet.getString("userType") ?: "",
                        resultSet.getString("email") ?: "",
                        resultSet.getString("contactNumber") ?: "",
                        resultSet.getString("createdOn") ?: "",
                        resultSet.getString("updatedOn") ?: "",
                        resultSet.getString("proofPicture") ?: "",
                        resultSet.getInt("verified") // Add this line
                    )
                )
            }
            unverifiedUsers
        } catch (e: SQLException) {
            e.printStackTrace()
            unverifiedUsers
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    suspend fun updateUserVerificationStatus(userID: String, status: Int): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            try {
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }
                val query = "UPDATE user_table SET verified = ? WHERE userID = ?"
                statement = connection.prepareStatement(query)
                statement.setInt(1, status)
                statement.setString(2, userID)

                val rowsAffected = statement.executeUpdate()

                // Trigger fast polling if update was successful
                if (rowsAffected > 0) {
                    SmartPollingManager.getInstance().triggerFastUpdate()
                }

                rowsAffected > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }
    }

    suspend fun approveUserVerificationWithProfileAudit(
        userID: String,
        staffUserID: String,
        disabledVerificationMode: String? = null,
        temporaryValidUntil: String? = null,
        promoteToAdmin: Boolean = false
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var userStatement: PreparedStatement? = null
            var profileStatement: PreparedStatement? = null
            var checkStatement: PreparedStatement? = null
            try {
                val userIDInt = userID.toIntOrNull() ?: return@withContext false
                val staffUserIDInt = staffUserID.toIntOrNull() ?: return@withContext false
                connection = getConnection()
                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext false
                }

                connection.autoCommit = false

                val checkQuery = "SELECT COUNT(*) AS count FROM user_table WHERE userID = ?"
                checkStatement = connection.prepareStatement(checkQuery)
                checkStatement.setInt(1, staffUserIDInt)
                val checkResult = checkStatement.executeQuery()
                val staffExists = checkResult.next() && checkResult.getInt("count") > 0
                checkResult.close()
                if (!staffExists) {
                    Log.e("MySQLHelper", "Staff userID $staffUserIDInt does not exist for verification audit.")
                    connection.rollback()
                    return@withContext false
                }

                val now = ZonedDateTime.now(ZoneId.of("UTC+8"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                val normalizedMode = disabledVerificationMode?.trim()?.uppercase(Locale.US)
                val shouldApplyDisabledSettings = normalizedMode == "TEMPORARY" || normalizedMode == "PERMANENT"

                if (!normalizedMode.isNullOrBlank() && !shouldApplyDisabledSettings) {
                    connection.rollback()
                    return@withContext false
                }

                val resolvedExpiryDate = when (normalizedMode) {
                    "TEMPORARY" -> {
                        val trimmedDate = temporaryValidUntil?.trim()
                        if (trimmedDate.isNullOrBlank()) {
                            connection.rollback()
                            return@withContext false
                        }
                        try {
                            LocalDate.parse(trimmedDate, DateTimeFormatter.ISO_LOCAL_DATE)
                            trimmedDate
                        } catch (_: Exception) {
                            connection.rollback()
                            return@withContext false
                        }
                    }
                    "PERMANENT" -> null
                    else -> null
                }

                val resolvedDisabilityType = if (shouldApplyDisabledSettings) {
                    val selectedType = if (normalizedMode == "TEMPORARY") "Temporary" else "Permanent"
                    resolveDisabilityTypeForDatabase(connection ?: return@withContext false, selectedType) ?: selectedType
                } else {
                    null
                }

                val userQuery = "UPDATE user_table SET verified = 1 WHERE userID = ?"
                userStatement = connection.prepareStatement(userQuery)
                userStatement.setInt(1, userIDInt)
                val userUpdated = userStatement.executeUpdate() > 0
                if (!userUpdated) {
                    connection.rollback()
                    return@withContext false
                }

                val profileQuery = if (shouldApplyDisabledSettings) {
                    """
                        UPDATE pwd_profiles_table
                        SET verifiedBy = ?, verificationDate = ?, expiryDate = ?, disabilityType = ?
                        WHERE userID = ?
                    """.trimIndent()
                } else {
                    """
                        UPDATE pwd_profiles_table
                        SET verifiedBy = ?, verificationDate = ?
                        WHERE userID = ?
                    """.trimIndent()
                }
                profileStatement = connection.prepareStatement(profileQuery)
                profileStatement.setInt(1, staffUserIDInt)
                profileStatement.setString(2, now)
                if (shouldApplyDisabledSettings) {
                    profileStatement.setString(3, resolvedExpiryDate)
                    profileStatement.setString(4, resolvedDisabilityType)
                    profileStatement.setInt(5, userIDInt)
                } else {
                    profileStatement.setInt(3, userIDInt)
                }
                val profileUpdated = profileStatement.executeUpdate() > 0
                if (!profileUpdated) {
                    val hasSafetyProfileQuery = "SELECT COUNT(*) AS count FROM safety_officer_profiles_table WHERE userID = ?"
                    checkStatement = connection.prepareStatement(hasSafetyProfileQuery)
                    checkStatement.setInt(1, userIDInt)
                    val safetyResult = checkStatement.executeQuery()
                    val hasSafetyProfile = safetyResult.next() && safetyResult.getInt("count") > 0
                    safetyResult.close()
                    checkStatement?.close()
                    checkStatement = null

                    if (!hasSafetyProfile) {
                        connection.rollback()
                        return@withContext false
                    }
                }

                if (promoteToAdmin) {
                    val promoteQuery = """
                        UPDATE safety_officer_profiles_table
                        SET position = 'Admin'
                        WHERE userID = ?
                    """.trimIndent()
                    val promoteStatement = connection.prepareStatement(promoteQuery)
                    promoteStatement.setInt(1, userIDInt)
                    val promoted = promoteStatement.executeUpdate() > 0
                    promoteStatement.close()
                    if (!promoted) {
                        connection.rollback()
                        return@withContext false
                    }
                }

                connection.commit()
                true
            } catch (e: SQLException) {
                e.printStackTrace()
                try {
                    connection?.rollback()
                } catch (rollbackException: SQLException) {
                    rollbackException.printStackTrace()
                }
                false
            } finally {
                checkStatement?.close()
                userStatement?.close()
                profileStatement?.close()
                try {
                    connection?.autoCommit = true
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
                connection?.close()
            }
        }
    }

    fun getAllWheelchairs(): List<WheelchairDevice> {
        val wheelchairs = mutableListOf<WheelchairDevice>()
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return wheelchairs
            }

            val query = """
            SELECT d.deviceID, d.userID, d.status, d.latitude, d.longitude, 
                   d.floorLevel, d.connectedUntil, d.rssi, d.distance, u.fullName
            FROM devices_table d
            LEFT JOIN user_table u ON d.userID = u.userID
            ORDER BY d.deviceID
        """
            statement = connection?.prepareStatement(query)
            resultSet = statement?.executeQuery()

            while (resultSet?.next() == true) {
                wheelchairs.add(
                    WheelchairDevice(
                        deviceID = resultSet.getString("deviceID") ?: "",
                        userID = resultSet.getObject("userID") as? Int,
                        status = resultSet.getString("status") ?: "unknown",
                        latitude = resultSet.getObject("latitude") as? Double,
                        longitude = resultSet.getObject("longitude") as? Double,
                        floorLevel = resultSet.getString("floorLevel"),
                        connectedUntil = resultSet.getString("connectedUntil"),
                        rssi = resultSet.getObject("rssi") as? Int,
                        distance = resultSet.getObject("distance") as? Float,
                        userName = resultSet.getString("fullName"),
                        maintenanceReason = null // Column doesn't exist yet, default to null
                    )
                )
            }
            wheelchairs
        } catch (e: SQLException) {
            e.printStackTrace()
            wheelchairs
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getOfficerNameByLocationID(locationID: String?): String? {
        if (locationID == null) return null

        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = """
            SELECT officerResponded FROM incident_logs_table WHERE locationID = ? ORDER BY alertID DESC LIMIT 1
        """
            statement = connection?.prepareStatement(query)
            statement?.setString(1, locationID)

            resultSet = statement?.executeQuery()
            if (resultSet?.next() == true) {
                val officerName = resultSet.getString("officerResponded")
                if (officerName.isNullOrBlank()) null else officerName
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getActiveConnectionForUser(userID: String): ActiveConnectionInfo? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e("MySQLHelper", "getActiveConnectionForUser: Database connection failed.")
                return null
            }

            // Query devices_table for an active connection for this user
            // Assumes 'connectedUntil' is a DATETIME/TIMESTAMP and 'status' is 'in_use'
            val query =
                "SELECT deviceID FROM devices_table WHERE userID = ? AND status = 'in_use'"
            statement = connection?.prepareStatement(query)
            statement?.setString(1, userID)

            resultSet = statement?.executeQuery()
            if (resultSet?.next() == true) {
                val deviceID = resultSet.getString("deviceID")
                // Return with a dummy timestamp (10 years in the future) so timer logic doesn't trigger
                ActiveConnectionInfo(deviceID, System.currentTimeMillis() + (10L * 365 * 24 * 60 * 60 * 1000))
            } else {
                Log.d(
                    "MySQLHelper",
                    "No active connection found for user $userID in devices_table."
                )
                null
            }
        } catch (e: SQLException) {
            Log.e(
                "MySQLHelper",
                "SQLException in getActiveConnectionForUser for $userID: ${e.message}",
                e
            )
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    // In MySQLHelper.kt, update the getAssistanceDetails function
    fun updateUserFCMToken(userID: String, fcmToken: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "UPDATE user_table SET fcm_token = ? WHERE userID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, fcmToken)
            statement.setString(2, userID)

            val rowsAffected = statement.executeUpdate()
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    fun getAllSecurityOfficerFCMTokens(): List<String> {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        val tokens = mutableListOf<String>()

        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return emptyList()
            }

            val query = """
                SELECT DISTINCT u.fcm_token
                FROM user_table u
                LEFT JOIN safety_officer_profiles_table s ON u.userID = s.userID
                WHERE u.verified = 1
                AND u.fcm_token IS NOT NULL
                AND u.fcm_token != ''
                AND (
                    s.userID IS NOT NULL
                    OR LOWER(COALESCE(u.userType, '')) LIKE '%admin%'
                )
            """
            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()

            while (resultSet.next()) {
                val token = resultSet.getString("fcm_token")
                if (!token.isNullOrBlank()) {
                    tokens.add(token)
                }
            }
            tokens
        } catch (e: SQLException) {
            e.printStackTrace()
            emptyList()
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    fun getAssistanceDetails(locationId: String): LocationItem? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = """
            SELECT 
                l.locationID, 
                l.userID, 
                l.deviceID, 
                l.fullName, 
                l.floorLevel, 
                i.status, 
                l.latitude, 
                l.longitude, 
                l.dateTime, 
                i.officerResponded,
                i.relocatedLocation,
                p.emergencyContactPerson,
                p.emergencyContactNumber
            FROM location_table l 
            LEFT JOIN incident_logs_table i ON l.locationID = i.locationID
            LEFT JOIN pwd_profiles_table p ON l.userID = p.userID
            WHERE l.locationID = ?
            ORDER BY i.alertDateTime DESC
            LIMIT 1
        """
            statement = connection?.prepareStatement(query)
            statement?.setString(1, locationId)

            resultSet = statement?.executeQuery()
            if (resultSet?.next() == true) {
                val relocatedLocation = resultSet.getString("relocatedLocation") ?: ""
                val assistanceType =
                    if (relocatedLocation.contains("WHEELCHAIR FALL DETECTED", ignoreCase = true)) {
                        "FALL_DETECTION"
                    } else {
                        "MANUAL"
                    }

                LocationItem(
                    locationID = resultSet.getString("locationID"),
                    userID = resultSet.getString("userID"),
                    deviceID = resultSet.getString("deviceID") ?: "",
                    fullName = resultSet.getString("fullName"),
                    floorLevel = resultSet.getString("floorLevel"),
                    status = resultSet.getString("status") ?: "",
                    latitude = resultSet.getDouble("latitude"),
                    longitude = resultSet.getDouble("longitude"),
                    dateTime = resultSet.getString("dateTime"),
                    officerName = resultSet.getString("officerResponded") ?: "",
                    assistanceType = assistanceType,
                    emergencyContactPerson = resultSet.getString("emergencyContactPerson"),
                    emergencyContactNumber = resultSet.getString("emergencyContactNumber")
                )
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    suspend fun getDeviceCoordinatesByLocationID(locationID: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null

            return@withContext try {
                connection = getConnection()
                if (connection == null) return@withContext null

                val query = """
                    SELECT l.latitude, l.longitude
                    FROM location_table l
                    WHERE l.locationID = ?
                    LIMIT 1
                """.trimIndent()

                statement = connection.prepareStatement(query)
                statement.setString(1, locationID)
                resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val latitude = (resultSet.getObject("latitude") as? Number)?.toDouble()
                    val longitude = (resultSet.getObject("longitude") as? Number)?.toDouble()
                    if (latitude != null && longitude != null) {
                        Pair(latitude, longitude)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: SQLException) {
                Log.e("MySQLHelper", "Error fetching device coordinates for locationID=$locationID: ${e.message}")
                null
            } finally {
                resultSet?.close()
                statement?.close()
                connection?.close()
            }
        }

    fun getVerifiedLocomotorUsersFiltered(
        userType: String?,
        creationDateType: String?,
        selectedDate: Date?
    ): List<UserData> {
        val users = mutableListOf<UserData>()
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        try {
            connection = getConnection()
            if (connection == null) return users

            val query = StringBuilder(
                """
                SELECT userID, fullName, userType, email, contactNumber, createdOn, updatedOn, proofPicture, verified
                FROM user_table
                WHERE verified = 1
            """
            )
            if (userType != null && userType != "All") {
                query.append(" AND userType = ?")
            } else {
                query.append(" AND userType IN ('Temporarily Disabled', 'Permanently Disabled')")
            }

            statement = connection.prepareStatement(query.toString())
            var paramIndex = 1
            if (userType != null && userType != "All") {
                statement.setString(paramIndex++, userType)
            }

            resultSet = statement.executeQuery()
            while (resultSet.next()) {
                users.add(
                    UserData(
                        resultSet.getString("userID") ?: "",
                        resultSet.getString("fullName") ?: "",
                        resultSet.getString("userType") ?: "",
                        resultSet.getString("email") ?: "",
                        resultSet.getString("contactNumber") ?: "",
                        resultSet.getString("createdOn") ?: "",
                        resultSet.getString("updatedOn") ?: "",
                        resultSet.getString("proofPicture") ?: "",
                        resultSet.getInt("verified")
                    )
                )
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
        return users
    }

    fun getAllVerifiedUsers(): List<UserData> {
        val users = mutableListOf<UserData>()
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        try {
            connection = getConnection()
            if (connection == null) return users

            val query = """
                SELECT 
                u.userID, 
                u.fullName, 
                p.disabilityType AS userType, 
                u.email, 
                u.contactNumber, 
                u.createdOn, 
                u.updatedOn, 
                u.proofPicture, 
                u.verified
            FROM user_table u
            INNER JOIN pwd_profiles_table p ON u.userID = p.userID
            WHERE u.verified = 1
            ORDER BY u.createdOn DESC
            """

            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()

            while (resultSet.next()) {
                users.add(
                    UserData(
                        resultSet.getString("userID") ?: "",
                        resultSet.getString("fullName") ?: "",
                        resultSet.getString("userType") ?: "",
                        resultSet.getString("email") ?: "",
                        resultSet.getString("contactNumber") ?: "",
                        resultSet.getString("createdOn") ?: "",
                        resultSet.getString("updatedOn") ?: "",
                        resultSet.getString("proofPicture") ?: "",
                        resultSet.getInt("verified")
                    )
                )
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
        return users
    }

    // FCM Notification Integration Functions

    private fun sendAssistanceRequestNotification(locationID: String, userID: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url =
                    "https://cl67pknqo8.execute-api.ap-southeast-1.amazonaws.com/prod/fcm-notification"
                val client = OkHttpClient()

                val jsonBody = """
                    {
                        "notificationType": "assistance_request",
                        "locationID": "$locationID",
                        "userID": "$userID"
                    }
                """.trimIndent()

                val body = jsonBody.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("MySQLHelper", "Assistance request notification sent successfully")
                    } else {
                        Log.e(
                            "MySQLHelper",
                            "Failed to send assistance request notification: ${response.code}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MySQLHelper", "Error sending assistance request notification: ${e.message}")
            }
        }
    }

    private fun sendOfficerResponseNotification(locationID: String, officerName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get userID for the officer
                val officerUserID = getUserIDByFullName(officerName)
                // Get userID from the assistance request
                val assistanceUserID = getUserIDByLocationID(locationID)

                if (officerUserID != null && assistanceUserID != null) {
                    val url =
                        "https://cl67pknqo8.execute-api.ap-southeast-1.amazonaws.com/prod/fcm-notification"
                    val client = OkHttpClient()

                    val jsonBody = """
                        {
                            "notificationType": "officer_response",
                            "locationID": "$locationID",
                            "officerID": "$officerUserID",
                            "userID": "$assistanceUserID"
                        }
                    """.trimIndent()

                    val body = jsonBody.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("MySQLHelper", "Officer response notification sent successfully")
                        } else {
                            Log.e(
                                "MySQLHelper",
                                "Failed to send officer response notification: ${response.code}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MySQLHelper", "Error sending officer response notification: ${e.message}")
            }
        }
    }

    private fun sendAssistanceResolvedNotification(
        locationID: String,
        status: String,
        officerName: String,
        relocatedLocation: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assistanceUserID = getUserIDByLocationID(locationID)
                val officerUserID = getUserIDByFullName(officerName)

                if (assistanceUserID != null && officerUserID != null) {
                    val url =
                        "https://cl67pknqo8.execute-api.ap-southeast-1.amazonaws.com/prod/fcm-notification"
                    val client = OkHttpClient()

                    val jsonBody = if (status == "false alarm") {
                        // Send false alarm notification with relocated location
                        """
                            {
                                "notificationType": "false_alarm",
                                "locationID": "$locationID",
                                "userID": "$assistanceUserID",
                                "officerID": "$officerUserID",
                                "relocatedLocation": "${relocatedLocation ?: ""}"
                            }
                        """.trimIndent()
                    } else if (status == "resolved") {
                        // Send resolved notification with relocated location
                        """
                            {
                                "notificationType": "assistance_resolved",
                                "locationID": "$locationID",
                                "userID": "$assistanceUserID",
                                "officerID": "$officerUserID",
                                "relocatedLocation": "${relocatedLocation ?: ""}"
                            }
                        """.trimIndent()
                    } else {
                        // Unknown status, don't send notification
                        Log.d("MySQLHelper", "Unknown status '$status', not sending notification")
                        return@launch
                    }

                    val body = jsonBody.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d(
                                "MySQLHelper",
                                "Assistance $status notification sent successfully"
                            )
                        } else {
                            Log.e(
                                "MySQLHelper",
                                "Failed to send assistance $status notification: ${response.code}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MySQLHelper", "Error sending assistance $status notification: ${e.message}")
            }
        }
    }

    private fun getUserIDByFullName(fullName: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) return null

            val query = "SELECT userID FROM user_table WHERE fullName = ? LIMIT 1"
            statement = connection.prepareStatement(query)
            statement.setString(1, fullName)
            resultSet = statement.executeQuery()

            if (resultSet.next()) {
                resultSet.getString("userID")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    private fun getUserIDByLocationID(locationID: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) return null

            val query = "SELECT userID FROM location_table WHERE locationID = ? LIMIT 1"
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)
            resultSet = statement.executeQuery()

            if (resultSet.next()) {
                resultSet.getString("userID")
            } else {
                null
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    suspend fun getPwdProfileData(userID: String): PwdProfileData? {
        return withContext(Dispatchers.IO) {
            var profile: PwdProfileData? = null
            val query = "SELECT disabilityType, expiryDate, emergencyContactPerson, emergencyContactNumber " +
                    "FROM pwd_profiles_table WHERE userID = ?"

            try {
                // Using the connection manager logic you likely already have in MySQLHelper
                val connection = getConnection()
                val statement = connection?.prepareStatement(query)
                statement?.setString(1, userID)

                val resultSet = statement?.executeQuery()

                if (resultSet != null && resultSet.next()) {
                    profile = PwdProfileData(
                        disabilityType = resultSet.getString("disabilityType"),
                        expiryDate = resultSet.getString("expiryDate"),
                        emergencyContactPerson = resultSet.getString("emergencyContactPerson"),
                        emergencyContactNumber = resultSet.getString("emergencyContactNumber")
                    )
                }

                resultSet?.close()
                statement?.close()
                // connection?.close() // Don't close if you use a persistent connection pool

            } catch (e: Exception) {
                Log.e("MySQLHelper", "Error fetching PWD profile: ${e.message}")
            }

            profile
        }
    }

    // Check if a device is available for connection
    fun isDeviceAvailable(deviceID: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e(
                    "MySQLHelper",
                    "Database connection failed while checking device availability."
                )
                return false
            }

            val query = """
                SELECT deviceID, userID, connectedUntil, status 
                FROM devices_table 
                WHERE deviceID = ?
            """.trimIndent()

            statement = connection.prepareStatement(query)
            statement.setString(1, deviceID)
            resultSet = statement.executeQuery()

            if (resultSet.next()) {
                val currentUserID = resultSet.getString("userID")
                val currentConnectedUntil = resultSet.getString("connectedUntil")
                val currentStatus = resultSet.getString("status")

                Log.d(
                    "MySQLHelper",
                    "Device $deviceID availability check - UserID: $currentUserID, ConnectedUntil: $currentConnectedUntil, Status: $currentStatus"
                )

                // Device is available if:
                // 1. Status is 'available' AND no user is connected
                // 2. OR connection has expired (even if status is 'in_use')
                // Device is NOT available if status is 'maintenance'

                if (currentStatus == "maintenance") {
                    Log.d("MySQLHelper", "Device $deviceID is under maintenance - not available")
                    return false
                }

                if (currentStatus == "available" && (currentUserID == null || currentUserID.isEmpty())) {
                    Log.d(
                        "MySQLHelper",
                        "Device $deviceID is available - status: available, no user connected"
                    )
                    return true
                }

                // Check if connection has expired for in_use devices
                if (currentStatus == "in_use" && currentConnectedUntil != null) {
                    val checkExpiredQuery =
                        "SELECT STR_TO_DATE(?, '%Y-%m-%d %H:%i:%s') < NOW() as expired"
                    val expiredStmt = connection.prepareStatement(checkExpiredQuery)
                    expiredStmt.setString(1, currentConnectedUntil)
                    val expiredResult = expiredStmt.executeQuery()

                    if (expiredResult.next()) {
                        val isExpired = expiredResult.getBoolean("expired")
                        Log.d(
                            "MySQLHelper",
                            "Device $deviceID connection expired check: $isExpired"
                        )
                        expiredStmt.close()
                        return isExpired
                    }
                    expiredStmt.close()
                }

                Log.d(
                    "MySQLHelper",
                    "Device $deviceID is not available - status: $currentStatus, userID: $currentUserID"
                )
                return false
            } else {
                Log.e("MySQLHelper", "Device $deviceID not found in database")
                return false
            }
        } catch (e: SQLException) {
            Log.e(
                "MySQLHelper",
                "Error checking device availability for $deviceID: ${e.message}",
                e
            )
            e.printStackTrace()
            false
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    // Clean up expired device connections
    fun cleanupExpiredConnections(): Int {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e(
                    "MySQLHelper",
                    "Database connection failed while cleaning up expired connections."
                )
                return 0
            }

            val query = """
                UPDATE devices_table 
                SET userID = NULL, connectedUntil = NULL, status = 'available'
                WHERE connectedUntil IS NOT NULL 
                AND STR_TO_DATE(connectedUntil, '%Y-%m-%d %H:%i:%s') < NOW()
                AND status = 'in_use'
            """.trimIndent()

            statement = connection.prepareStatement(query)
            val rowsAffected = statement.executeUpdate()

            Log.d("MySQLHelper", "Cleaned up $rowsAffected expired device connections")
            return rowsAffected
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Error cleaning up expired connections: ${e.message}", e)
            e.printStackTrace()
            0
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    // Force reset a specific device connection (emergency use)
    fun forceResetDeviceConnection(deviceID: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e("MySQLHelper", "Database connection failed while force resetting device.")
                return false
            }

            val query = """
                UPDATE devices_table 
                SET userID = NULL, connectedUntil = NULL, status = 'available'
                WHERE deviceID = ?
            """.trimIndent()

            statement = connection.prepareStatement(query)
            statement.setString(1, deviceID)
            val rowsAffected = statement.executeUpdate()

            Log.d("MySQLHelper", "Force reset device $deviceID - rows affected: $rowsAffected")
            return rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Error force resetting device $deviceID: ${e.message}", e)
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }


    // Update a specific device's status (for testing/debugging)
    fun updateDeviceStatus(deviceID: String, newStatus: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e("MySQLHelper", "Database connection failed while updating device status.")
                return false
            }

            val query = "UPDATE devices_table SET status = ? WHERE deviceID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, newStatus)
            statement.setString(2, deviceID)

            val rowsAffected = statement.executeUpdate()
            Log.d(
                "MySQLHelper",
                "Updated device $deviceID status to '$newStatus' - rows affected: $rowsAffected"
            )
            return rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Error updating device status for $deviceID: ${e.message}", e)
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    // Monitor device status changes (for debugging)
    fun monitorDeviceStatusChanges(deviceID: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e("MySQLHelper", "Database connection failed while monitoring device status.")
                return null
            }

            val query =
                "SELECT deviceID, userID, connectedUntil, status, NOW() as current_timestamp FROM devices_table WHERE deviceID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, deviceID)
            resultSet = statement.executeQuery()

            if (resultSet.next()) {
                val currentUserID = resultSet.getString("userID")
                val currentConnectedUntil = resultSet.getString("connectedUntil")
                val currentStatus = resultSet.getString("status")
                val timestamp = resultSet.getLong("current_timestamp")

                val statusInfo =
                    "Device $deviceID at $timestamp: status=$currentStatus, userID=$currentUserID, connectedUntil=$currentConnectedUntil"
                Log.d("MySQLHelper", "DEVICE STATUS MONITOR: $statusInfo")

                // Alert if status is 'active' (should not happen)
                if (currentStatus == "active") {
                    Log.e(
                        "MySQLHelper",
                        "🚨 ALERT: Device $deviceID has 'active' status! This should not happen!"
                    )
                    Log.e("MySQLHelper", "🚨 Full details: $statusInfo")
                }

                return currentStatus
            } else {
                Log.e("MySQLHelper", "Device $deviceID not found in database during monitoring")
                return null
            }
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Error monitoring device status for $deviceID: ${e.message}", e)
            e.printStackTrace()
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    // Set device maintenance status
    fun updateDeviceMaintenanceStatus(
        deviceID: String,
        isMaintenanceMode: Boolean,
        reason: String? = null
    ): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                Log.e(
                    "MySQLHelper",
                    "Database connection failed while updating maintenance status."
                )
                return false
            }

            val newStatus = if (isMaintenanceMode) "maintenance" else "available"
            // Simplified query without maintenanceReason column for now
            val query = if (isMaintenanceMode) {
                // Set to maintenance mode - disconnect any user
                "UPDATE devices_table SET status = ?, userID = NULL, connectedUntil = NULL WHERE deviceID = ?"
            } else {
                // Remove from maintenance mode
                "UPDATE devices_table SET status = ? WHERE deviceID = ?"
            }

            statement = connection.prepareStatement(query)
            statement.setString(1, newStatus)
            statement.setString(2, deviceID)

            val rowsAffected = statement.executeUpdate()

            if (rowsAffected > 0) {
                Log.d("MySQLHelper", "Device $deviceID maintenance status updated to: $newStatus")
                if (reason != null) {
                    Log.d("MySQLHelper", "Maintenance reason (not stored in DB yet): $reason")
                }
                SmartPollingManager.getInstance().triggerFastUpdate()
            }

            rowsAffected > 0
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Error updating maintenance status for $deviceID: ${e.message}", e)
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    // Get device maintenance reason
    fun getDeviceMaintenanceReason(deviceID: String): String? {
        // Column doesn't exist yet, return null for now
        Log.d(
            "MySQLHelper",
            "getDeviceMaintenanceReason called for $deviceID - maintenanceReason column not implemented yet"
        )
        return null
    }

    suspend fun updateUserFCMToken(userID: String, newToken: String?): Boolean =
        withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            return@withContext try {
                connection = getConnection()
                if (connection == null) {
                    Log.e("MySQLHelper", "Database connection failed for updateUserFCMToken.")
                    return@withContext false
                }

                val query = "UPDATE user_table SET fcm_token = ? WHERE userID = ?"
                statement = connection.prepareStatement(query)
                statement.setString(1, newToken) // Set token (can be null)
                statement.setString(2, userID)

                val rowsAffected = statement.executeUpdate()
                Log.d(
                    "MySQLHelper",
                    "Updated FCM token for userID $userID: $rowsAffected rows affected."
                )
                rowsAffected > 0
            } catch (e: SQLException) {
                Log.e(
                    "MySQLHelper",
                    "SQL Error updating FCM token for userID $userID: ${e.message}",
                    e
                )
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }

    suspend fun clearUserFCMToken(userID: String): Boolean = withContext(Dispatchers.IO) {
        // Reusing updateUserFCMToken to set the token to null
        updateUserFCMToken(userID, null)
    }

    suspend fun clearFCMTokenFromOtherUsers(fcmToken: String, currentUserID: String): Boolean =
        withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            return@withContext try {
                connection = getConnection()
                if (connection == null) {
                    Log.e(
                        "MySQLHelper",
                        "Database connection failed for clearFCMTokenFromOtherUsers."
                    )
                    return@withContext false
                }

                // Find any other user with this FCM token and set their token to NULL
                val query =
                    "UPDATE user_table SET fcm_token = NULL WHERE fcm_token = ? AND userID != ?"
                statement = connection.prepareStatement(query)
                statement.setString(1, fcmToken)
                statement.setString(2, currentUserID)

                val rowsAffected = statement.executeUpdate()
                if (rowsAffected > 0) {
                    Log.d(
                        "MySQLHelper",
                        "Cleared FCM token for $rowsAffected other user(s) with token ending in ...${
                            fcmToken.takeLast(5)
                        }"
                    )
                }
                rowsAffected > 0
            } catch (e: SQLException) {
                Log.e(
                    "MySQLHelper",
                    "SQL Error clearing FCM token from other users: ${e.message}",
                    e
                )
                false
            } finally {
                statement?.close()
                connection?.close()
            }
        }

    // New: Function to get deviceName by deviceID
    suspend fun getDeviceNameById(deviceID: String): String? = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return@withContext try {
            connection = getConnection()
            if (connection == null) {
                Log.e("MySQLHelper", "Database connection failed for getDeviceNameById.")
                return@withContext null
            }

            val query = "SELECT deviceName FROM devices_table WHERE deviceID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, deviceID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val deviceName = resultSet.getString("deviceName")
                Log.d("MySQLHelper", "Found deviceName '$deviceName' for deviceID $deviceID")
                deviceName
            } else {
                Log.w("MySQLHelper", "No device found with deviceID: $deviceID")
                null
            }
        } catch (e: SQLException) {
            Log.e(
                "MySQLHelper",
                "SQL Error getting deviceName for deviceID $deviceID: ${e.message}",
                e
            )
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    // displays values in (pwd) account settings
    suspend fun getUserProfile(userID: String): Map<String, String>? {
        return withContext(Dispatchers.IO) {
            // Corrected columns based on your DB screenshot:
            // emergencyContactPerson and emergencyContactNumber
            val query = """
            SELECT u.fullName, u.email, u.contactNumber, u.userType, u.department, u.createdOn,
                   p.schoolID, p.emergencyContactPerson, p.emergencyContactNumber, p.disabilityType, p.verifiedBy, p.verificationDate
            FROM user_table u
            LEFT JOIN pwd_profiles_table p ON u.userID = p.userID
            WHERE u.userID = ?
        """.trimIndent()

            try {
                val connection = getConnection() ?: return@withContext null
                val statement = connection.prepareStatement(query)
                statement.setString(1, userID)
                val resultSet = statement.executeQuery()

                if (resultSet != null && resultSet.next()) {
                    val data = mutableMapOf<String, String>()
                    data["fullName"] = resultSet.getString("fullName") ?: ""
                    data["schoolID"] = resultSet.getString("schoolID") ?: ""
                    data["email"] = resultSet.getString("email") ?: ""
                    data["contactNumber"] = resultSet.getString("contactNumber") ?: ""
                    data["userType"] = resultSet.getString("userType") ?: ""
                    data["department"] = resultSet.getString("department") ?: ""
                    // Map DB names to the keys your Activity expects
                    data["emergencyName"] = resultSet.getString("emergencyContactPerson") ?: ""
                    data["emergencyNumber"] = resultSet.getString("emergencyContactNumber") ?: ""
                    data["disabilityType"] = resultSet.getString("disabilityType") ?: ""
                    data["verifiedBy"] = resultSet.getString("verifiedBy") ?: ""
                    data["verificationDate"] = resultSet.getString("verificationDate") ?: ""
                    data["createdOn"] = resultSet.getString("createdOn") ?: ""

                    resultSet.close()
                    statement.close()
                    return@withContext data
                }
            } catch (e: Exception) {
                Log.e("MySQLHelper", "Fetch Error: ${e.message}")
            }
            null
        }
    }

    // displays values in officer account settings
    suspend fun getOfficerProfile(userID: String): Map<String, String>? {
        return withContext(Dispatchers.IO) {
            val query = """
            SELECT u.fullName, u.email, u.contactNumber, u.userType, u.department, u.createdOn,
                   o.position AS systemRole
            FROM user_table u
            LEFT JOIN safety_officer_profiles_table o ON u.userID = o.userID
            WHERE u.userID = ?
        """.trimIndent()

            try {
                val connection = getConnection() ?: return@withContext null
                val statement = connection.prepareStatement(query)
                statement.setString(1, userID)
                val resultSet = statement.executeQuery()

                if (resultSet != null && resultSet.next()) {
                    val data = mutableMapOf<String, String>()
                    data["fullName"] = resultSet.getString("fullName") ?: ""
                    data["email"] = resultSet.getString("email") ?: ""
                    data["contactNumber"] = resultSet.getString("contactNumber") ?: ""
                    data["userType"] = resultSet.getString("userType") ?: ""
                    data["department"] = resultSet.getString("department") ?: ""
                    data["systemRole"] = resultSet.getString("systemRole") ?: ""
//                    data["verifiedBy"] = resultSet.getString("verifiedBy") ?: ""
//                    data["verificationDate"] = resultSet.getString("verificationDate") ?: ""
                    data["createdOn"] = resultSet.getString("createdOn") ?: ""

                    resultSet.close()
                    statement.close()
                    return@withContext data
                }
            } catch (e: Exception) {
                Log.e("MySQLHelper", "Fetch Error: ${e.message}")
            }
            null
        }
    }

    // In your MySQLHelper.kt file

    @JvmStatic
    fun clearDeviceConnection(deviceID: String): Boolean {
        // Use the column names that actually exist in your table
        val sql = "UPDATE devices_table SET userID = NULL, connectedUntil = NULL, status = 'available' WHERE deviceID = ?"
        var connection: Connection? = null
        var pstmt: PreparedStatement? = null
        var success = false

        try {
            connection = getConnection()
            if (connection != null) {
                pstmt = connection.prepareStatement(sql)
                pstmt.setString(1, deviceID)
                val rowsAffected = pstmt.executeUpdate()
                success = rowsAffected > 0
                Log.d("MySQLHelper", "Disconnect result: $success for Device: $deviceID")
            }
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "SQL Error in disconnect: ${e.message}")
        } finally {
            pstmt?.close()
            connection?.close()
        }
        return success
    }
    @JvmStatic
    fun getDeviceStatus(deviceID: String): DeviceStatus { // Return type is now the Enum
        val sql = "SELECT status FROM devices_table WHERE deviceID = ?" // Use correct table name
        var connection: Connection? = null
        var pstmt: PreparedStatement? = null
        var rs: ResultSet? = null
        var statusString: String? = null

        try {
            connection = getConnection()
            pstmt = connection?.prepareStatement(sql)
            pstmt?.setString(1, deviceID)
            rs = pstmt?.executeQuery()
            if (rs?.next() == true) {
                statusString = rs.getString("status")
            }
        } catch (e: Exception) {
            Log.e("MySQLHelper", "Error getting device status: ${e.message}", e)
        } finally {
            rs?.close()
            pstmt?.close()
            connection?.close()
        }

        return when (statusString?.lowercase()) {
            "available" -> DeviceStatus.AVAILABLE
            "in_use" -> DeviceStatus.IN_USE
            "maintenance" -> DeviceStatus.MAINTENANCE
            else -> DeviceStatus.UNKNOWN
        }
    }

    data class DeviceLocation(val name: String?, val latitude: Double?, val longitude: Double?, val floorLevel: String?)

    @JvmStatic
    fun getDeviceLastLocation(deviceID: String): DeviceLocation? {
        // UPDATED: Changed column names to match your other working functions
        val sql = "SELECT deviceName, latitude, longitude, floorLevel FROM devices_table WHERE deviceID = ?"
        var connection: Connection? = null
        var pstmt: PreparedStatement? = null
        var rs: ResultSet? = null

        try {
            connection = getConnection()
            if (connection == null) return null

            pstmt = connection.prepareStatement(sql)
            pstmt.setString(1, deviceID)
            rs = pstmt.executeQuery()

            if (rs.next()) {
                val name = rs.getString("deviceName")
                val lat = rs.getDouble("latitude")
                val lng = rs.getDouble("longitude")
                val floor = rs.getString("floorLevel")

                return DeviceLocation(
                    name = name,
                    latitude = if (rs.wasNull()) null else lat,
                    longitude = if (rs.wasNull()) null else lng,
                    floorLevel = floor
                )
            }
        } catch (e: Exception) {
            Log.e("MySQLHelper", "SQL Error in getDeviceLastLocation: ${e.message}")
        } finally {
            rs?.close()
            pstmt?.close()
            connection?.close()
        }
        return null
    }

    // --- Live GPS Tracking (phone GPS for officer dispatch) ---

    @Volatile
    private var liveGpsTableReady = false

    private fun ensureLiveGpsTableExists(conn: Connection) {
        if (liveGpsTableReady) return
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS live_gps_table (
                    userID VARCHAR(45) NOT NULL PRIMARY KEY,
                    latitude DOUBLE NOT NULL,
                    longitude DOUBLE NOT NULL,
                    accuracy FLOAT NOT NULL DEFAULT 0,
                    updatedAt VARCHAR(45) DEFAULT NULL
                )
            """.trimIndent())
            // Add accuracy column for existing tables that don't have it yet
            try {
                conn.createStatement().execute(
                    "ALTER TABLE live_gps_table ADD COLUMN accuracy FLOAT NOT NULL DEFAULT 0"
                )
            } catch (_: SQLException) { /* column already exists */ }
            liveGpsTableReady = true
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Failed to ensure live_gps_table: ${e.message}")
        }
    }

    fun upsertLiveGPS(userID: String, latitude: Double, longitude: Double, accuracy: Float = 0f): Boolean {
        var connection: Connection? = null
        return try {
            connection = getConnection() ?: return false
            ensureLiveGpsTableExists(connection)

            val now = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val query = """
                INSERT INTO live_gps_table (userID, latitude, longitude, accuracy, updatedAt)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE latitude = ?, longitude = ?, accuracy = ?, updatedAt = ?
            """.trimIndent()
            val stmt = connection.prepareStatement(query)
            stmt.setString(1, userID)
            stmt.setDouble(2, latitude)
            stmt.setDouble(3, longitude)
            stmt.setFloat(4, accuracy)
            stmt.setString(5, now)
            stmt.setDouble(6, latitude)
            stmt.setDouble(7, longitude)
            stmt.setFloat(8, accuracy)
            stmt.setString(9, now)
            stmt.executeUpdate() >= 0
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "upsertLiveGPS failed: ${e.message}")
            false
        } finally {
            connection?.close()
        }
    }

    /** Returns [latitude, longitude, accuracyMeters] or null */
    fun getLiveGPS(userID: String): DoubleArray? {
        var connection: Connection? = null
        var stmt: PreparedStatement? = null
        var rs: ResultSet? = null
        return try {
            connection = getConnection() ?: return null
            ensureLiveGpsTableExists(connection)
            stmt = connection.prepareStatement(
                "SELECT latitude, longitude, accuracy FROM live_gps_table WHERE userID = ?"
            )
            stmt.setString(1, userID)
            rs = stmt.executeQuery()
            if (rs.next()) doubleArrayOf(
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getDouble("accuracy")
            ) else null
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "getLiveGPS failed: ${e.message}")
            null
        } finally {
            rs?.close(); stmt?.close(); connection?.close()
        }
    }

    fun getActiveAssistanceLiveGps(): List<ActiveAssistanceGps> {
        val results = mutableListOf<ActiveAssistanceGps>()
        var connection: Connection? = null
        var stmt: PreparedStatement? = null
        var rs: ResultSet? = null

        return try {
            connection = getConnection() ?: return emptyList()
            ensureLiveGpsTableExists(connection)

            val query = """
                SELECT i.userID,
                       u.fullName,
                      COALESCE(g.latitude, l.latitude) AS latitude,
                      COALESCE(g.longitude, l.longitude) AS longitude,
                      COALESCE(g.accuracy, 0) AS accuracy,
                      i.status AS incidentStatus,
                      i.locationID
                FROM incident_logs_table i
                JOIN (
                    SELECT userID,
                           MAX(CONCAT(alertDateTime, '|', alertID)) AS latestKey
                    FROM incident_logs_table
                    WHERE status IN ('pending', 'ongoing')
                       OR (
                           status = 'resolved'
                           AND (
                               relocatedLocation IS NULL OR TRIM(relocatedLocation) = ''
                               OR actionFA IS NULL OR TRIM(actionFA) = ''
                               OR actionINFO IS NULL OR TRIM(actionINFO) = ''
                           )
                       )
                    GROUP BY userID
                ) latest ON latest.userID = i.userID
                       AND CONCAT(i.alertDateTime, '|', i.alertID) = latest.latestKey
                JOIN user_table u ON u.userID = i.userID
                  JOIN location_table l ON l.locationID = i.locationID
                  LEFT JOIN live_gps_table g ON g.userID = i.userID
                WHERE i.status IN ('pending', 'ongoing')
                   OR (
                       i.status = 'resolved'
                       AND (
                           i.relocatedLocation IS NULL OR TRIM(i.relocatedLocation) = ''
                           OR i.actionFA IS NULL OR TRIM(i.actionFA) = ''
                           OR i.actionINFO IS NULL OR TRIM(i.actionINFO) = ''
                       )
                   )
            """.trimIndent()

            stmt = connection.prepareStatement(query)
            rs = stmt.executeQuery()

            while (rs.next()) {
                val lat = rs.getDouble("latitude")
                val lng = rs.getDouble("longitude")
                if (lat == 0.0 && lng == 0.0) continue

                results.add(
                    ActiveAssistanceGps(
                        userID = rs.getString("userID") ?: continue,
                        fullName = rs.getString("fullName") ?: "User",
                        latitude = lat,
                        longitude = lng,
                        accuracy = rs.getDouble("accuracy"),
                        status = rs.getString("incidentStatus") ?: "pending",
                        locationID = rs.getString("locationID") ?: ""
                    )
                )
            }
            results
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "getActiveAssistanceLiveGps failed: ${e.message}")
            emptyList()
        } finally {
            rs?.close(); stmt?.close(); connection?.close()
        }
    }

    fun deleteLiveGPS(userID: String): Boolean {
        var connection: Connection? = null
        return try {
            connection = getConnection() ?: return false
            val stmt = connection.prepareStatement("DELETE FROM live_gps_table WHERE userID = ?")
            stmt.setString(1, userID)
            stmt.executeUpdate() >= 0
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "deleteLiveGPS failed: ${e.message}")
            false
        } finally {
            connection?.close()
        }
    }

    fun getOngoingIncidentForUser(userID: String): OngoingIncidentInfo? {
        var connection: Connection? = null
        var stmt: PreparedStatement? = null
        var rs: ResultSet? = null
        return try {
            connection = getConnection() ?: return null
            val query = """
                SELECT i.alertID, i.locationID, i.officerResponded, u.userID AS officerUserID
                FROM incident_logs_table i
                LEFT JOIN user_table u ON u.fullName = i.officerResponded
                WHERE i.userID = ? AND i.status = 'ongoing' AND i.officerResponded IS NOT NULL
                ORDER BY i.alertDateTime DESC LIMIT 1
            """.trimIndent()
            stmt = connection.prepareStatement(query)
            stmt.setString(1, userID)
            rs = stmt.executeQuery()
            if (rs.next()) {
                OngoingIncidentInfo(
                    alertID = rs.getString("alertID"),
                    locationID = rs.getString("locationID"),
                    officerName = rs.getString("officerResponded"),
                    officerUserID = rs.getString("officerUserID")
                )
            } else null
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "getOngoingIncidentForUser failed: ${e.message}")
            null
        } finally {
            rs?.close(); stmt?.close(); connection?.close()
        }
    }

    fun hasActiveIncidentForUser(userID: String): Boolean {
        var connection: Connection? = null
        var stmt: PreparedStatement? = null
        var rs: ResultSet? = null
        return try {
            connection = getConnection() ?: return false
            stmt = connection.prepareStatement(
                "SELECT 1 FROM incident_logs_table WHERE userID = ? AND status IN ('pending','ongoing') LIMIT 1"
            )
            stmt.setString(1, userID)
            rs = stmt.executeQuery()
            rs.next()
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "hasActiveIncidentForUser failed: ${e.message}")
            false
        } finally {
            rs?.close(); stmt?.close(); connection?.close()
        }
    }

    /**
     * 1. Count ONLY PWD users who are currently logged in
     * Note: I updated the query to match your disabilityType check logic.
     */
    @JvmStatic
    fun getTotalPwdUserCount(): Int {
        var connection: Connection? = null
        var pstmt: PreparedStatement? = null
        var rs: ResultSet? = null
        var count = 0
        // Adjust the string 'Locomotor Disabled' if your database uses a different role name
        val sql = "SELECT COUNT(*) FROM pwd_profiles_table"

        try {
            connection = getConnection()
            pstmt = connection?.prepareStatement(sql)
            rs = pstmt?.executeQuery()
            if (rs?.next() == true) count = rs.getInt(1)
        } catch (e: Exception) {
            Log.e("MySQLHelper", "Error counting total PWDs: ${e.message}")
        } finally {
            rs?.close(); pstmt?.close(); connection?.close()
        }
        return count
    }

    /**
     * 2. Count ONLY wheelchairs that are currently marked as 'in_use'
     */
    @JvmStatic
    fun getInUseWheelchairCount(): Int {
        var connection: Connection? = null
        var pstmt: PreparedStatement? = null
        var rs: ResultSet? = null
        var count = 0

        val sql = "SELECT COUNT(*) FROM devices_table WHERE status = 'in_use'"

        try {
            connection = getConnection()
            pstmt = connection?.prepareStatement(sql)
            rs = pstmt?.executeQuery()

            if (rs?.next() == true) {
                count = rs.getInt(1)
            }
        } catch (e: Exception) {
            Log.e("MySQLHelper", "Error counting in-use wheelchairs: ${e.message}")
        } finally {
            rs?.close()
            pstmt?.close()
            connection?.close()
        }
        return count
    }

    @JvmStatic
    fun getAvailableOfficersCount(): Int {
        var count = 0
        val sql = "SELECT COUNT(*) FROM safety_officer_profiles_table WHERE isOnDuty = 1 AND isDispatched = 0"
        var connection: Connection? = null
        try {
            connection = getConnection()
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery(sql)
            if (rs?.next() == true) {
                count = rs.getInt(1)
            }
            rs?.close()
            stmt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.close()
        }
        return count
    }
}
