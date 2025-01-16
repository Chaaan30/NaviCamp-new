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

    // Validate user credentials and fetch user type
    suspend fun validateUser(username: String, password: String): String? {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null
            try {
                connection = getConnection()

                if (connection == null) {
                    println("Database connection failed.")
                    return@withContext null
                }

                // SQL query to fetch the hashed password and user type with case-sensitive username comparison
                val checkCredentialsQuery =
                    "SELECT password, userType FROM user_table WHERE BINARY userName = ?"
                statement = connection.prepareStatement(checkCredentialsQuery)
                statement.setString(1, username)

                resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val hashedPassword = resultSet.getString("password")
                    val userType = resultSet.getString("userType")

                    // Verify the password
                    if (BCrypt.checkpw(password, hashedPassword)) {
                        userType
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
    }

    // Insert user data into the database
    fun insertUser(
        fullName: String,
        userName: String,
        password: String,
        userType: String,
        email: String,
        contactNumber: String
    ): Boolean {
        val connection = getConnection() ?: return false // Return false if the connection is null
        return try {
            val sql = "INSERT INTO user_table (fullName, userName, password, userType, email, contactNumber, createdOn, updatedOn) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())"
            val statement: PreparedStatement = connection.prepareStatement(sql)
            statement.setString(1, fullName)
            statement.setString(2, userName)
            statement.setString(3, password)
            statement.setString(4, userType)
            statement.setString(5, email)
            statement.setString(6, contactNumber)

            // Execute the statement and check the result
            val rowsAffected = statement.executeUpdate()
            rowsAffected > 0 // Return true if one or more rows were affected
        } catch (e: SQLException) {
            e.printStackTrace()
            false // Return false in case of error
        } finally {
            connection.close() // Use safe call to close the connection
        }
    }
}