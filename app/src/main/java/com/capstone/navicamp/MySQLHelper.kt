package com.capstone.navicamp

import android.util.Log
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.ResultSet
import java.util.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class to hold active connection information
data class ActiveConnectionInfo(
    val deviceID: String,
    val connectedUntilMillis: Long
)



object MySQLHelper {

    // Database credentials
    private const val JDBC_URL =
        "jdbc:mariadb://campusnavigator.c10aiyo64bnv.ap-southeast-1.rds.amazonaws.com:3306/campusnavigator"
    private const val USERNAME = "navicamp"
    private const val PASSWORD = "navicamp"

    init {
        try {
            Class.forName("org.mariadb.jdbc.Driver")
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
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

            val query ="""
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

    fun getPendingItems(): List<LocationItem> {
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
                    i.officerResponded
                FROM incident_logs_table i
                JOIN location_table l ON i.locationID = l.locationID
                WHERE i.status = 'pending' OR i.status LIKE '%ongoing%'
            """.trimIndent()
            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()

            while (resultSet.next()) {
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
                    resultSet.getString("deviceID") ?: ""
                )
                pendingItems.add(locationItem)
            }
            return pendingItems

        } catch (e: SQLException) {
            e.printStackTrace()
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

    fun insertLocationData(context: Context, userID: String, deviceID: String, fullName: String): Boolean {
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

            val incidentQuery = """
                INSERT INTO incident_logs_table (userID, deviceID, locationID, status, alertDateTime)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            incidentStmt = connection.prepareStatement(incidentQuery)
            incidentStmt.setString(1, userID)
            incidentStmt.setString(2, deviceID)
            incidentStmt.setString(3, locationID)
            incidentStmt.setString(4, "pending")
            incidentStmt.setString(5, currentDateTime)

            val incidentRows = incidentStmt.executeUpdate()
            Log.d("MySQLHelper", "Incident data inserted: $incidentRows rows affected.")

            if (locationRows > 0 && incidentRows > 0) {
                val intent = Intent("com.capstone.navicamp.DATA_CHANGED")
                intent.setClassName("com.capstone.navicamp", "com.capstone.navicamp.DataChangeReceiver")
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
    
    suspend fun resolveIncident(locationID: String, status: String, officerName: String, alertDescription: String? = null): Boolean = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        try {
            connection = getConnection()
            if (connection == null) return@withContext false

            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val query = if (alertDescription != null) {
                "UPDATE incident_logs_table SET status = ?, officerResponded = ?, alertDescription = ?, resolvedOn = ? WHERE locationID = ?"
            } else {
                "UPDATE incident_logs_table SET status = ?, officerResponded = ?, resolvedOn = ? WHERE locationID = ?"
            }
            statement = connection.prepareStatement(query)
            statement.setString(1, status)
            statement.setString(2, officerName)
            if (alertDescription != null) {
                statement.setString(3, alertDescription)
                statement.setString(4, currentDateTime)
                statement.setString(5, locationID)
            } else {
                statement.setString(3, currentDateTime)
                statement.setString(4, locationID)
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

    fun getDeviceLastLocation(deviceID: String): Triple<Double, Double, String>? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            // Assuming devices_table has latitude, longitude, floorLevel
            val query =
                "SELECT latitude, longitude, floorLevel FROM devices_table WHERE deviceID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, deviceID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                Triple(
                    resultSet.getDouble("latitude"),
                    resultSet.getDouble("longitude"),
                    resultSet.getString("floorLevel")
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
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val locationID =
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            // Assuming location_table also stores deviceID for assistance requests tied to a device
            val query =
                "INSERT INTO location_table (locationID, userID, fullName, deviceID, dateTime, status, latitude, longitude, floorLevel) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)
            statement.setString(2, userID)
            statement.setString(3, fullName)
            statement.setString(4, deviceID) // Store the deviceID
            statement.setString(5, currentDateTime)
            statement.setString(6, "pending") // Default status
            statement.setDouble(7, latitude)
            statement.setDouble(8, longitude)
            statement.setString(9, floorLevel)

            val rowsAffected = statement.executeUpdate()
            Log.d("MySQLHelper", "Assistance request inserted: $rowsAffected rows affected.")

            if (rowsAffected > 0) {
                val intent = Intent("com.capstone.navicamp.DATA_CHANGED")
                intent.setClassName(
                    "com.capstone.navicamp",
                    "com.capstone.navicamp.DataChangeReceiver"
                )
                context.sendBroadcast(intent)
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

    fun updateDeviceConnectionStatus(
        deviceID: String,
        newUserID: String?, // Renamed for clarity
        newConnectedUntil: String? // Renamed for clarity
    ): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val newDeviceStatus = if (newUserID != null) "in_use" else "available"

            val query: String
            if (newUserID != null) {
                // Trying to connect a user
                // Ensure NOW() compares correctly with your stored connectedUntil format/timezone.
                // It's best if connectedUntil is stored as UTC DATETIME/TIMESTAMP.
                query = """
                    UPDATE devices_table
                    SET userID = ?, connectedUntil = ?, status = ?
                    WHERE deviceID = ?
                    AND (userID IS NULL OR connectedUntil IS NULL OR connectedUntil < NOW())
                """.trimIndent()
                statement = connection.prepareStatement(query)
                statement.setString(1, newUserID)
                if (newConnectedUntil != null) {
                    statement.setString(2, newConnectedUntil)
                } else {
                    // This case should ideally not happen if newUserID is not null,
                    // but as a safeguard, set connectedUntil to NULL or a very past time.
                    // Forcing a valid connection to have an expiry.
                    statement.setNull(2, java.sql.Types.TIMESTAMP)
                }
                statement.setString(3, newDeviceStatus)
                statement.setString(4, deviceID)
            } else {
                // Disconnecting a user (newUserID is null)
                query = "UPDATE devices_table SET userID = NULL, connectedUntil = NULL, status = ? WHERE deviceID = ?"
                statement = connection.prepareStatement(query)
                statement.setString(1, newDeviceStatus) // "available"
                statement.setString(2, deviceID)
            }

            val rowsAffected = statement.executeUpdate()
            Log.d(
                "MySQLHelper",
                "Device $deviceID connection status update attempt. Rows affected: $rowsAffected. User: $newUserID, Until: $newConnectedUntil, Status: $newDeviceStatus."
            )
            
            // If we were trying to connect a user (newUserID != null) and no rows were affected,
            // it means the device was busy with a still-valid connection or deviceID didn't exist.
            if (newUserID != null && rowsAffected == 0) {
                Log.w("MySQLHelper", "Failed to connect device $deviceID. It might be in use with a valid session or deviceID is invalid.")
                return false // Explicitly return false as connection was not established
            }
            // If disconnecting, 0 rows affected is fine if already disconnected.
            // If connecting, rowsAffected > 0 means success.
            return rowsAffected > 0 || (newUserID == null)
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "Error updating device connection status for $deviceID: ${e.message}", e)
            e.printStackTrace()
            false
        } finally {
            statement?.close()
            connection?.close()
        }
    }

    // Generate a new userID
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

            val year = Calendar.getInstance().get(Calendar.YEAR).toString()
            val query = "SELECT MAX(userID) AS maxUserID FROM user_table WHERE userID LIKE ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, "$year%")

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val maxUserID = resultSet.getString("maxUserID")
                val nextID = if (maxUserID != null) {
                    val currentNumber = maxUserID.substring(4).toInt()
                    currentNumber + 1
                } else {
                    1
                }
                String.format("%s%04d", year, nextID)
            } else {
                String.format("%s0001", year)
            }
        } catch (e: SQLException) {
            e.printStackTrace()
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

    // Update user with userID
    suspend fun updateUserWithUserID(
        newFullName: String,
        newEmail: String,
        newContactNumber: String,
        userID: String,
        updatedOn: String
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
                statement.setString(index, userID)

                Log.d("MySQLHelper", "Executing query: $query")
                Log.d(
                    "MySQLHelper",
                    "Parameters: updatedOn=$updatedOn, newFullName=$newFullName, newEmail=$newEmail, newContactNumber=$newContactNumber, userID=$userID"
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
                "SELECT COUNT(*) AS count FROM user_table WHERE userType = 'Student' AND userID IS NOT NULL AND userID != ''"
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
                    l.fullName,
                    l.floorLevel,
                    i.status,
                    l.latitude,
                    l.longitude,
                    l.dateTime,
                    i.officerResponded
                FROM incident_logs_table i
                JOIN location_table l ON i.locationID = l.locationID
                WHERE l.locationID = ?
                ORDER BY i.alertDateTime DESC
                LIMIT 1
            """.trimIndent()
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
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
                    officerName = resultSet.getString("officerResponded") ?: ""
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
    }    fun loginUser(email: String, password: String): UserData? {
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
            if (resultSet.next()) {                val storedPassword = resultSet.getString("password")
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
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
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

            val selectQuery =  """
                SELECT u.userID, d.deviceID, l.locationID, l.dateTime
                FROM location_table l
                JOIN user_table u ON u.userID = l.userID
                JOIN devices_table d ON d.userID = u.userID
                WHERE l.locationID NOT IN (
                    SELECT locationID FROM incident_logs_table
                )
            """.trimIndent()

            selectStmt = connection.prepareStatement(selectQuery)
            resultSet = selectStmt.executeQuery()

            val insertQuery = """
                INSERT INTO incident_logs_table
                (userID, deviceID, locationID, alertDateTime, status, alertDescription, officerResponded, resolvedOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            insertStmt = connection.prepareStatement(insertQuery)

            while (resultSet.next()) {
                val userID = resultSet.getString("userID")
                val deviceID = resultSet.getString("deviceID")
                val locationID = resultSet.getString("locationID")
                val alertDateTime = resultSet.getTimestamp("dateTime")

                insertStmt.setString(1, userID)
                insertStmt.setString(2, deviceID)
                insertStmt.setString(3, locationID)
                insertStmt.setTimestamp(4, alertDateTime)
                insertStmt.setString(5, "pending") // default status
                insertStmt.setNull(6, java.sql.Types.VARCHAR) // alertDescription NULL
                insertStmt.setNull(7, java.sql.Types.VARCHAR) // officerResponded NULL
                insertStmt.setNull(8, java.sql.Types.TIMESTAMP) // resolvedOn NULL

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
                    i.alertDescription
                FROM incident_logs_table i
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
                    resultSet.getString("alertDescription") ?: ""
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
        return try {            connection = getConnection()
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
        return try {            connection = getConnection()
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
                        resultSet.getString("contactNumber") ?: "",                        resultSet.getString("createdOn") ?: "",
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
    }    fun updateProofPicture(userID: Int, proofPicture: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            val query = "UPDATE user_table SET proofPicture = ? WHERE userID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, proofPicture)
            statement.setInt(2, userID) // Ensure userID is set as an integer

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
            SELECT officerResponded FROM location_table WHERE locationID = ?
        """
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
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
            val query = "SELECT deviceID, connectedUntil FROM devices_table WHERE userID = ? AND status = 'in_use'"
            statement = connection.prepareStatement(query)
            statement.setString(1, userID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val deviceID = resultSet.getString("deviceID")
                val connectedUntilStr = resultSet.getString("connectedUntil")

                if (deviceID != null && connectedUntilStr != null) {
                    try {
                        // Parse the datetime string to milliseconds
                        // Ensure this format matches how it's stored by updateDeviceConnectionStatus
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Manila") // Consistent timezone
                        val date = dateFormat.parse(connectedUntilStr)
                        if (date != null) {
                            val connectedUntilMillis = date.time
                            // Optional: Check if current time is already past connectedUntilMillis
                            // if (System.currentTimeMillis() > connectedUntilMillis) {
                            // Log.d("MySQLHelper", "Found expired connection for user $userID, device $deviceID in DB.")
                            // return null // Or trigger a cleanup
                            // }
                            Log.d("MySQLHelper", "Found active connection for user $userID: device $deviceID, until $connectedUntilStr ($connectedUntilMillis ms)")
                            ActiveConnectionInfo(deviceID, connectedUntilMillis)
                        } else {
                            Log.e("MySQLHelper", "Failed to parse connectedUntil date string: $connectedUntilStr for user $userID")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("MySQLHelper", "Error parsing date for getActiveConnectionForUser: ${e.message}", e)
                        null
                    }
                } else {
                    Log.d("MySQLHelper", "No valid deviceID or connectedUntil found for user $userID with status 'in_use'.")
                    null
                }
            } else {
                Log.d("MySQLHelper", "No active connection found for user $userID in devices_table.")
                null
            }
        } catch (e: SQLException) {
            Log.e("MySQLHelper", "SQLException in getActiveConnectionForUser for $userID: ${e.message}", e)
            null
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    // In MySQLHelper.kt, update the getAssistanceDetails function
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
            SELECT * FROM location_table WHERE locationID = ?
        """
            statement = connection.prepareStatement(query)
            statement.setString(1, locationId)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                LocationItem(
                    locationID = resultSet.getString("locationID"),
                    userID = resultSet.getString("userID"),
                    fullName = resultSet.getString("fullName"),
                    floorLevel = resultSet.getString("floorLevel"),
                    status = resultSet.getString("status"),
                    latitude = resultSet.getDouble("latitude"),
                    longitude = resultSet.getDouble("longitude"),
                    dateTime = resultSet.getString("dateTime"),
                    officerName = resultSet.getString("officerResponded")  // Changed parameter name
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
}
//    fun getAssistanceDetails(locationId: String): LocationItem? {
//        var connection: Connection? = null
//        var statement: PreparedStatement? = null
//        var resultSet: ResultSet? = null
//        return try {
//            connection = getConnection()
//            if (connection == null) {
//                println("Database connection failed.")
//                return null
//            }
//
//            val query = """
//            SELECT locationID, userID, deviceID, fullName, floorLevel, latitude, longitude, dateTime
//            FROM location_table WHERE locationID = ?
//        """
//            statement = connection.prepareStatement(query)
//            statement.setString(1, locationId)
//
//            resultSet = statement.executeQuery()
//            if (resultSet.next()) {
//                LocationItem(
//                    locationID = resultSet.getString("locationID"),
//                    userID = resultSet.getString("userID"),
//                    deviceID = resultSet.getString("deviceID"),
//                    fullName = resultSet.getString("fullName"),
//                    floorLevel = resultSet.getString("floorLevel"),
//                    latitude = resultSet.getDouble("latitude"),
//                    longitude = resultSet.getDouble("longitude"),
//                    dateTime = resultSet.getString("dateTime")
//                )
//            } else {
//                null
//            }
//        } catch (e: SQLException) {
//            e.printStackTrace()
//            null
//        } finally {
//            resultSet?.close()
//            statement?.close()
//            connection?.close()
//        }
//    }
}
