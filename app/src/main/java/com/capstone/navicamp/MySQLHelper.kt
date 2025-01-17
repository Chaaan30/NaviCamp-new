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

object MySQLHelper {

    // Database credentials
    private const val JDBC_URL = "jdbc:mariadb://campusnavigator.c10aiyo64bnv.ap-southeast-1.rds.amazonaws.com:3306/campusnavigator"
    private const val USERNAME = "navicamp"
    private const val PASSWORD = "navicamp"

    // Get the connection to the database
    private fun getConnection(): Connection? {
        return try {
            DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }

    // Validate access code and fetch user type
    fun getUserTypeByAccessCode(accessCode: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT userType FROM user_table WHERE accessCode = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, accessCode)

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

    fun getFullNameByAccessCode(accessCode: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT fullName FROM user_table WHERE accessCode = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, accessCode)

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

    fun getUserTypeIfFullNameExists(accessCode: String): String? {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return null
            }

            val query = "SELECT userType FROM user_table WHERE accessCode = ? AND fullName IS NOT NULL AND fullName != ''"
            statement = connection.prepareStatement(query)
            statement.setString(1, accessCode)

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

    fun updateUser(accessCode: String, newFullName: String?): Boolean {
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        return try {
            connection = getConnection()
            if (connection == null) {
                println("Database connection failed.")
                return false
            }

            // Get current datetime in UTC+8 and format it to 24-hour format
            val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC+8"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val query = "UPDATE user_table SET fullName = ?, createdOn = ? WHERE accessCode = ?"
            statement = connection.prepareStatement(query)
            statement.setString(1, newFullName)
            statement.setString(2, currentDateTime)
            statement.setString(3, accessCode)

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