import android.util.Log
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt

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

    // Update user data in the database
    fun updateUser(fullName: String, accessCode: String): Boolean {
        val connection = getConnection() ?: return false
        return try {
            // Check if fullName is already set for the given accessCode
            val checkQuery = "SELECT fullName FROM user_table WHERE accessCode = ?"
            val checkStatement: PreparedStatement = connection.prepareStatement(checkQuery)
            checkStatement.setString(1, accessCode)
            val resultSet = checkStatement.executeQuery()
            if (resultSet.next() && resultSet.getString("fullName").isNotEmpty()) {
                return false // Full name already exists
            }

            val sql = "UPDATE user_table SET fullName = ?, CreatedOn = NOW() WHERE accessCode = ?"
            val statement: PreparedStatement = connection.prepareStatement(sql)
            statement.setString(1, fullName)
            statement.setString(2, accessCode)

            val rowsAffected = statement.executeUpdate()
            rowsAffected > 0
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        } finally {
            connection.close()
        }
    }
}