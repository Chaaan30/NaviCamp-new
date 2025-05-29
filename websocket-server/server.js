const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const mysql = require('mysql2/promise');
const cors = require('cors');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: process.env.ALLOWED_ORIGINS?.split(',') || "*",
    methods: ["GET", "POST"],
    credentials: true
  }
});

// Middleware
app.use(cors());
app.use(express.json());

// Database connection configuration
const dbConfig = {
  host: process.env.DB_HOST,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  port: process.env.DB_PORT || 3306,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
};

// Create connection pool
const pool = mysql.createPool(dbConfig);

// Store connected clients by room
const rooms = {
  security_officers: new Set(),
  general: new Set()
};

// Database query functions
async function getPendingAssistanceRequests() {
  try {
    const [rows] = await pool.execute(`
      SELECT l.locationID, l.userID, l.latitude, l.longitude, l.floorLevel, 
             l.dateTime, l.status, u.fullName, l.officerName
      FROM location_table l
      JOIN user_table u ON l.userID = u.userID
      WHERE l.status = 'pending'
      ORDER BY l.dateTime DESC
    `);
    return rows;
  } catch (error) {
    console.error('Error fetching assistance requests:', error);
    return [];
  }
}

async function getUnverifiedUsers() {
  try {
    const [rows] = await pool.execute(`
      SELECT userID, fullName, email, contactNumber, userType, createdOn, proofDisability
      FROM user_table 
      WHERE verified = 0 AND proofDisability IS NOT NULL
      ORDER BY createdOn DESC
    `);
    return rows;
  } catch (error) {
    console.error('Error fetching unverified users:', error);
    return [];
  }
}

async function getUserCount() {
  try {
    const [rows] = await pool.execute(`
      SELECT COUNT(*) as count 
      FROM user_table 
      WHERE userType = 'Locomotor Disabled' AND verified = 1
    `);
    return rows[0].count;
  } catch (error) {
    console.error('Error fetching user count:', error);
    return 0;
  }
}

async function getDeviceCount() {
  try {
    const [rows] = await pool.execute(`
      SELECT COUNT(*) as count 
      FROM device_table 
      WHERE status = 'active'
    `);
    return rows[0].count;
  } catch (error) {
    console.error('Error fetching device count:', error);
    return 0;
  }
}

// Function to broadcast updates to specific rooms
async function broadcastToSecurityOfficers(eventName, data) {
  io.to('security_officers').emit(eventName, data);
}

// Function to check for database changes and broadcast updates
async function checkForUpdates() {
  try {
    // Get all current data
    const assistanceRequests = await getPendingAssistanceRequests();
    const verificationRequests = await getUnverifiedUsers();
    const userCount = await getUserCount();
    const deviceCount = await getDeviceCount();

    // Broadcast updates to security officers
    await broadcastToSecurityOfficers('assistance_update', {
      assistance_requests: assistanceRequests
    });

    await broadcastToSecurityOfficers('verification_update', {
      verification_requests: verificationRequests
    });

    await broadcastToSecurityOfficers('user_count_update', {
      count: userCount
    });

    await broadcastToSecurityOfficers('device_count_update', {
      count: deviceCount
    });

  } catch (error) {
    console.error('Error checking for updates:', error);
  }
}

// Socket.IO connection handling
io.on('connection', (socket) => {
  console.log(`Client connected: ${socket.id}`);

  // Handle joining rooms
  socket.on('join_room', (room) => {
    socket.join(room);
    if (rooms[room]) {
      rooms[room].add(socket.id);
    }
    console.log(`Client ${socket.id} joined room: ${room}`);
    
    // Send initial data when client joins
    if (room === 'security_officers') {
      sendInitialData(socket);
    }
  });

  // Handle request for initial data
  socket.on('request_initial_data', () => {
    sendInitialData(socket);
  });

  // Handle assistance response updates
  socket.on('assistance_response', async (data) => {
    try {
      const { locationId, officerName } = data;
      
      // Update database
      await pool.execute(
        'UPDATE location_table SET officerName = ? WHERE locationID = ?',
        [officerName, locationId]
      );

      // Broadcast update to all security officers
      checkForUpdates();
      
    } catch (error) {
      console.error('Error updating assistance response:', error);
    }
  });

  // Handle verification decisions
  socket.on('verification_decision', async (data) => {
    try {
      const { userId, decision } = data;
      const status = decision === 'accepted' ? 1 : 2;
      
      // Update database
      await pool.execute(
        'UPDATE user_table SET verified = ? WHERE userID = ?',
        [status, userId]
      );

      // Broadcast update to all security officers
      checkForUpdates();
      
    } catch (error) {
      console.error('Error updating verification decision:', error);
    }
  });

  // Handle disconnection
  socket.on('disconnect', () => {
    console.log(`Client disconnected: ${socket.id}`);
    
    // Remove from all rooms
    Object.keys(rooms).forEach(room => {
      rooms[room].delete(socket.id);
    });
  });
});

// Function to send initial data to a specific socket
async function sendInitialData(socket) {
  try {
    const assistanceRequests = await getPendingAssistanceRequests();
    const verificationRequests = await getUnverifiedUsers();
    const userCount = await getUserCount();
    const deviceCount = await getDeviceCount();

    socket.emit('assistance_update', {
      assistance_requests: assistanceRequests
    });

    socket.emit('verification_update', {
      verification_requests: verificationRequests
    });

    socket.emit('user_count_update', {
      count: userCount
    });

    socket.emit('device_count_update', {
      count: deviceCount
    });

  } catch (error) {
    console.error('Error sending initial data:', error);
  }
}

// Periodic check for database changes (fallback mechanism)
// This polls the database every 5 seconds to catch any changes not triggered by WebSocket events
setInterval(checkForUpdates, 5000);

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    timestamp: new Date().toISOString(),
    connections: io.engine.clientsCount
  });
});

// Start server
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`WebSocket server running on port ${PORT}`);
  console.log(`Health check available at http://localhost:${PORT}/health`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('Received SIGTERM, shutting down gracefully');
  server.close(() => {
    pool.end();
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('Received SIGINT, shutting down gracefully');
  server.close(() => {
    pool.end();
    process.exit(0);
  });
});
