# NaviCamp WebSocket Server

This WebSocket server provides real-time updates for the NaviCamp Android application, replacing the polling mechanism with efficient push notifications.

## Features

- **Real-time Updates**: Instant notifications for assistance requests, verification requests, user counts, and device counts
- **Room-based Broadcasting**: Targeted updates to security officers
- **Database Integration**: Direct connection to AWS RDS MySQL database
- **Health Monitoring**: Built-in health check endpoint
- **Graceful Shutdown**: Proper cleanup on server termination

## Setup Instructions

### 1. Install Dependencies

```bash
cd websocket-server
npm install
```

### 2. Configure Environment

Copy the example environment file and fill in your AWS RDS details:

```bash
cp .env.example .env
```

Edit `.env` with your actual database credentials:

```env
# Database Configuration (AWS RDS)
DB_HOST=your-rds-endpoint.amazonaws.com
DB_USER=your-db-username
DB_PASSWORD=your-db-password
DB_NAME=your-database-name
DB_PORT=3306

# Server Configuration
PORT=3000
NODE_ENV=production
```

### 3. Update Android App

In your Android app's `WebSocketManager.kt`, update the server URL:

```kotlin
private val SERVER_URL = "ws://your-server-domain:3000"  // or wss:// for HTTPS
```

### 4. Run the Server

For development:
```bash
npm run dev
```

For production:
```bash
npm start
```

## Deployment Options

### Option 1: AWS EC2
1. Launch an EC2 instance
2. Install Node.js and npm
3. Clone your server code
4. Configure environment variables
5. Use PM2 for process management: `npm install -g pm2 && pm2 start server.js`

### Option 2: Heroku
1. Create a Heroku app
2. Add database environment variables in Heroku dashboard
3. Deploy: `git push heroku main`

### Option 3: AWS Lambda + API Gateway
Use the serverless framework for a fully managed solution.

## API Endpoints

- `GET /health` - Health check endpoint
- WebSocket connection at `/` for real-time communication

## WebSocket Events

### Client to Server:
- `join_room` - Join a specific room (e.g., "security_officers")
- `request_initial_data` - Request current data
- `assistance_response` - Update assistance response
- `verification_decision` - Update verification decision

### Server to Client:
- `assistance_update` - New assistance requests
- `verification_update` - New verification requests  
- `user_count_update` - Updated user count
- `device_count_update` - Updated device count

## Benefits Over Polling

- **Reduced Server Load**: No constant database queries
- **Faster Updates**: Instant notifications instead of 3-second delays
- **Better Battery Life**: No constant HTTP requests on mobile
- **Scalable**: Can handle many concurrent connections efficiently
- **Reliable**: Automatic reconnection and offline handling

## Monitoring

Check server health:
```bash
curl http://your-server:3000/health
```

Monitor WebSocket connections and ensure real-time data flow is working properly.
