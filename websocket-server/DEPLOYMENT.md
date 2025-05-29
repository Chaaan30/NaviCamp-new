# Quick Deployment Guide for NaviCamp WebSocket Server

## Option 1: Heroku (Recommended for Quick Setup)

### Prerequisites
- Install Heroku CLI: https://devcenter.heroku.com/articles/heroku-cli
- Git installed on your system

### Steps:
1. Navigate to the websocket-server directory:
   ```bash
   cd websocket-server
   ```

2. Initialize git repository (if not already done):
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   ```

3. Login to Heroku:
   ```bash
   heroku login
   ```

4. Create a new Heroku app:
   ```bash
   heroku create navicamp-websocket-server
   ```

5. Set environment variables:
   ```bash
   heroku config:set DB_HOST=campusnavigator.c10aiyo64bnv.ap-southeast-1.rds.amazonaws.com
   heroku config:set DB_USER=navicamp
   heroku config:set DB_PASSWORD=navicamp
   heroku config:set DB_NAME=campusnavigator
   heroku config:set DB_PORT=3306
   heroku config:set NODE_ENV=production
   ```

6. Deploy to Heroku:
   ```bash
   git push heroku main
   ```

7. Your WebSocket server will be available at: `https://navicamp-websocket-server.herokuapp.com`

### Update Android App
In `WebSocketManager.kt`, update the SERVER_URL:
```kotlin
private val SERVER_URL = "https://navicamp-websocket-server.herokuapp.com"
```

## Option 2: AWS EC2 (For Production)

### Prerequisites
- AWS account
- EC2 instance (t2.micro or higher)

### Steps:
1. Launch EC2 instance with Ubuntu 20.04 LTS
2. Connect via SSH
3. Install Node.js:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
   sudo apt-get install -y nodejs
   ```
4. Install PM2 for process management:
   ```bash
   sudo npm install -g pm2
   ```
5. Clone your server code and install dependencies:
   ```bash
   git clone <your-repo>
   cd websocket-server
   npm install
   ```
6. Set up environment variables in `.env` file
7. Start with PM2:
   ```bash
   pm2 start server.js --name navicamp-websocket
   pm2 startup
   pm2 save
   ```
8. Configure security group to allow inbound traffic on port 3000
9. Update Android app with your EC2 public IP or domain

## Option 3: Railway (Alternative to Heroku)

1. Visit https://railway.app and sign up
2. Connect your GitHub repository
3. Deploy the websocket-server folder
4. Set environment variables in Railway dashboard
5. Railway will provide a URL for your deployed service

## Testing Your Deployment

After deployment, test the health endpoint:
```bash
curl https://your-domain.com/health
```

You should see:
```json
{"status":"OK","timestamp":"...","connections":0}
```

## Next Steps

1. Deploy the server using one of the options above
2. Update the Android app's WebSocketManager.kt with the deployed URL
3. Build and test the Android app with real-time updates
4. Monitor server logs and performance
