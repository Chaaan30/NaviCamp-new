package com.capstone.navicamp

import android.util.Log
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import android.content.Context
import android.content.Intent
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



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
    private fun getConnection(): Connection? {
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

            val query =
                "SELECT COUNT(*) AS count FROM location_table WHERE floorLevel = ? AND status = 'pending'"
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
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return pendingItems
            }

            val query = "SELECT * FROM location_table WHERE status = 'pending' OR status LIKE '%ongoing%'"
            statement = connection.prepareStatement(query)
            resultSet = statement.executeQuery()
            while (resultSet.next()) {
                val locationItem = LocationItem(
                    resultSet.getString("locationID"),
                    resultSet.getString("userID"),
                    resultSet.getString("fullName"),
                    resultSet.getString("floorLevel"),
                    resultSet.getString("status"),
                    resultSet.getDouble("latitude"),
                    resultSet.getDouble("longitude"),
                    resultSet.getString("dateTime")
                )
                pendingItems.add(locationItem)
            }
            pendingItems
        } catch (e: SQLException) {
            e.printStackTrace()
            pendingItems
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

            val query =
                "SELECT userID, fullName, userType, email, contactNumber, createdOn, updatedOn FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                UserData(
                    resultSet.getString("userID") ?: return null,
                    resultSet.getString("fullName") ?: return null,
                    resultSet.getString("userType") ?: return null,
                    resultSet.getString("email") ?: return null,
                    resultSet.getString("contactNumber") ?: return null,
                    resultSet.getString("createdOn") ?: return null,
                    resultSet.getString("updatedOn") ?: return null
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

    fun insertLocationData(context: Context, userID: String, fullName: String): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            // Generate a random locationID
            val locationID =
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()

            // Get current datetime in UTC+8 and format it to 24-hour format
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            // Fixed latitude, longitude, and floorLevel values for testing
            val latitude = 14.243667
            val longitude = 121.111429
            val floorLevel = "Einstein Building Ground Floor"

            val query =
                "INSERT INTO location_table (locationID, userID, fullName, dateTime, status, latitude, longitude, floorLevel) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)
            statement.setString(2, userID)
            statement.setString(3, fullName)
            statement.setString(4, currentDateTime)
            statement.setString(5, "pending")
            statement.setDouble(6, latitude)
            statement.setDouble(7, longitude)
            statement.setString(8, floorLevel)

            val rowsAffected = statement.executeUpdate()
            Log.d("MySQLHelper", "Location data inserted: $rowsAffected rows affected.")

            if (rowsAffected > 0) {
                // Send broadcast
                val intent = Intent("com.capstone.navicamp.DATA_CHANGED")
                context.sendBroadcast(intent)
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

    fun getUserIDByUserName(userName: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT userID FROM user_table WHERE userName = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, userName)

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
    }

    // Insert user data into the database
    fun insertUser(
        userID: String,
        fullName: String,
        userType: String,
        email: String,
        contactNumber: String,
        password: String
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
            }

            // Get current datetime in UTC+8 and format it to 24-hour format
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val query =
                "INSERT INTO user_table (userID, fullName, userType, email, contactNumber, password, createdOn) VALUES (?, ?, ?, ?, ?, ?, ?)"
            statement = connection.prepareStatement(query)
            statement.setString(1, userID)
            statement.setString(2, fullName)
            statement.setString(3, userType)
            statement.setString(4, email)
            statement.setString(5, contactNumber)
            statement.setString(6, password)
            statement.setString(7, currentDateTime)

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
    suspend fun updateUserWithUserID(newFullName: String, newEmail: String, newContactNumber: String, userID: String, updatedOn: String): Boolean {
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

                val rowsAffected = statement.executeUpdate()
                Log.d("MySQLHelper", "User updated: $rowsAffected rows affected.")
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

                val query = "SELECT COUNT(*) AS count FROM user_table WHERE contactNumber = ? AND userID != ?"
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

                val query = "SELECT COUNT(*) AS count FROM user_table WHERE email = ? AND userID != ?"
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

            val query = "SELECT password, userType FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val storedPassword = resultSet.getString("password")
                val storedUserType = resultSet.getString("userType")
                val hashedPassword = hashPassword(password)
                val isValidUserType = storedUserType == userType
                hashedPassword == storedPassword && isValidUserType // Verify the password and userType
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

            val query = "SELECT password FROM user_table WHERE email = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, email)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val storedPassword = resultSet.getString("password")
                val hashedPassword = hashPassword(password)
                storedPassword == hashedPassword // Compare the hashed password with the stored password
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

            val query = "SELECT * FROM location_table WHERE locationID = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, locationID)

            resultSet = statement.executeQuery()
            if (resultSet.next()) {
                LocationItem(
                    resultSet.getString("locationID"),
                    resultSet.getString("userID"),
                    resultSet.getString("fullName"),
                    resultSet.getString("floorLevel"),
                    resultSet.getString("status"),
                    resultSet.getDouble("latitude"),
                    resultSet.getDouble("longitude"),
                    resultSet.getString("dateTime")
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

            val query =
                "SELECT userID, fullName, userType, email, contactNumber, createdOn, updatedOn, password FROM user_table WHERE email = ?"
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
                        resultSet.getString("updatedOn") ?: ""
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

            val query = "UPDATE location_table SET status = ? WHERE locationID = ?"
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

}