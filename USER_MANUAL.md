# NAVICAMP SYSTEM USER MANUAL
**Campus Assistance & Emergency Dispatch Mobile Application**  
*Integrated IoT Smart Wheelchair & Geofenced Security Management Platform*  
*Mapúa Malayan Colleges Laguna (MMCL)*

---

```
================================================================================
DOCUMENT CONTROL
================================================================================
Project Title      : NaviCamp Mobile Application & IoT Campus Dispatch System
Target Deployment  : Mapúa Malayan Colleges Laguna (MMCL)
Target Audience    : Locomotor Disabled Users (Students/Employees) & Campus Safety Officers
Document Version   : 2.0 (Comprehensive Academic Edition)
Date of Document   : 2026-08-20
Status             : Official User Guide & Academic Capstone Reference
================================================================================
```

---

## TABLE OF CONTENTS

1. [SYSTEM OVERVIEW & GETTING STARTED](#1-system-overview--getting-started)
   * 1.1 [Introduction to NaviCamp](#11-introduction-to-navicamp)
   * 1.2 [System Architecture & Core Technologies](#12-system-architecture--core-technologies)
   * 1.3 [System Prerequisites & Permissions](#13-system-prerequisites--permissions)
   * 1.4 [First-Time Device Setup Wizard](#14-first-time-device-setup-wizard)
2. [USER MANUAL: LOCOMOTOR DISABLED USERS (STUDENTS & EMPLOYEES)](#2-user-manual-locomotor-disabled-users)
   * 2.1 [Account Registration & Medical Verification](#21-account-registration--medical-verification)
   * 2.2 [Connecting to an IoT Wheelchair Device](#22-connecting-to-an-iot-wheelchair-device)
   * 2.3 [Requesting Emergency Assistance (SOS Flow)](#23-requesting-emergency-assistance-sos-flow)
   * 2.4 [Live Officer Dispatch & GPS Tracking Map](#24-live-officer-dispatch--gps-tracking-map)
   * 2.5 [Disconnection & Ending Wheelchair Session](#25-disconnection--ending-wheelchair-session)
   * 2.6 [Profile Management & Temporary Access Renewal](#26-profile-management--temporary-access-renewal)
3. [USER MANUAL: SAFETY OFFICERS & CAMPUS ADMINISTRATORS](#3-user-manual-safety-officers--campus-administrators)
   * 3.1 [Officer Registration & Role-Based Verification](#31-officer-registration--role-based-verification)
   * 3.2 [Automated Geofence-Based On-Duty / Off-Duty Monitoring](#32-automated-geofence-based-on-duty--off-duty-monitoring)
   * 3.3 [Real-Time Incident Dashboard & Live Presence Monitor](#33-real-time-incident-dashboard--live-presence-monitor)
   * 3.4 [Interactive Campus Map Operations & Dispatching](#34-interactive-campus-map-operations--dispatching)
   * 3.5 [Assistance Response, Resolution, and Incident Reporting](#35-assistance-response-resolution-and-incident-reporting)
   * 3.6 [Incident Logbook, Custom Filtering & Report Exporting](#36-incident-logbook-custom-filtering--report-exporting)
   * 3.7 [IoT Wheelchair Fleet Management & Maintenance](#37-iot-wheelchair-fleet-management--maintenance)
   * 3.8 [Admin Module: Verification QR Generator](#38-admin-module-verification-qr-generator)
   * 3.9 [Admin Module: Registered PWD Directory & Audit Trail](#39-admin-module-registered-pwd-directory--audit-trail)
4. [SYSTEM TROUBLESHOOTING & EMERGENCY FAQS](#4-system-troubleshooting--emergency-faqs)
   * 4.1 [Common Technical Issues & Solutions](#41-common-technical-issues--solutions)
   * 4.2 [Frequently Asked Questions (FAQs)](#42-frequently-asked-questions-faqs)
5. [SYSTEM AUDIT, DATA PRIVACY & GLOSSARY](#5-system-audit-data-privacy--glossary)

---

# 1. SYSTEM OVERVIEW & GETTING STARTED

---

### 1.1 Introduction to NaviCamp
**NaviCamp** is an integrated mobile and Internet-of-Things (IoT) assistive technology platform engineered specifically for **Mapúa Malayan Colleges Laguna (MMCL)**. The platform is designed to eliminate mobility barriers, enhance personal safety, and streamline emergency response for locomotor disabled individuals (students, faculty, and administrative personnel).

The system bridges two primary user groups:
1. **Locomotor Disabled Users (PWDs):** Individuals with permanent or temporary physical mobility impairments who utilize campus smart wheelchairs, require accessible navigation, and need rapid emergency response mechanisms (such as manual SOS and automated fall detection).
2. **Campus Safety Officers & Security Admins:** Personnel responsible for campus surveillance, emergency dispatching, incident investigation, PWD verification audits, and smart wheelchair fleet management.

---

### 1.2 System Architecture & Core Technologies

```
+-------------------------------------------------------------------------+
|                           NAVICAMP ECOSYSTEM                            |
+-------------------------------------------------------------------------+
|                                                                         |
|   +--------------------------+          +---------------------------+   |
|   |  Locomotor PWD Client    |          |   Safety Officer Client   |   |
|   |  - SOS Dispatch          |          |   - Interactive Live Map  |   |
|   |  - QR Device Binding     |          |   - Incident Resolution   |   |
|   |  - Live Officer Tracking |          |   - Geofence Duty Auto    |   |
|   +-------------+------------+          +-------------+-------------+   |
|                 |                                     |                 |
|                 +------------------+------------------+                 |
|                                    |                                    |
|                                    v                                    |
|   +-----------------------------------------------------------------+   |
|   |                    Central AWS Cloud Services                   |   |
|   |   * Amazon RDS (MySQL Database Cluster)                         |   |
|   |   * Firebase Cloud Messaging (FCM Push Notifications)           |   |
|   |   * Firebase Firestore (Real-Time Live Presence Tracking)       |   |
|   |   * Google Maps SDK (High-Precision Campus GIS Engine)          |   |
|   +--------------------------------+--------------------------------+   |
|                                    ^                                    |
|                                    | (BLE & Wi-Fi Telemetry)            |
|   +--------------------------------+--------------------------------+   |
|   |                  IoT Smart Wheelchair Hardware                  |   |
|   |   * ESP32 Microcontroller Node                                  |   |
|   |   * 6-Axis Accelerometer / Gyroscope (Fall Detection)           |   |
|   |   * Unique Hardware QR Identification Tag                       |   |
|   +-----------------------------------------------------------------+   |
+-------------------------------------------------------------------------+
```

---

### 1.3 System Prerequisites & Permissions

For optimal performance and safety compliance, ensure the mobile device meets the following system requirements:

| Component | Minimum Specification / Permission | Operational Purpose |
| :--- | :--- | :--- |
| **Operating System** | Android 8.0 (Oreo) or higher (Android 10+ recommended) | Support for background location services and BLE. |
| **GPS / Location** | `ACCESS_FINE_LOCATION` (High Accuracy Mode) | Pinpointing emergency locations and geofence duty status. |
| **Camera** | `CAMERA` permission | Scanning Wheelchair and Verification QR codes. |
| **Notifications** | `POST_NOTIFICATIONS` permission | Receiving emergency dispatch alerts and updates. |
| **Battery Optimization** | Battery Optimization Exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Prevents the OS from killing background GPS updates during an active SOS. |
| **Internet Access** | Active Wi-Fi (MMCL Campus Network) or Cellular Mobile Data | Real-time database sync, push notifications, and map updates. |

---

### 1.4 First-Time Device Setup Wizard

Upon the initial launch of NaviCamp, users are guided through an onboarding wizard (`SetupActivity`):

```
+---------------+     +---------------+     +---------------+     +---------------+     +---------------+
|    Step 1     | --> |    Step 2     | --> |    Step 3     | --> |    Step 4     | --> |    Step 5     |
| Welcome Intro |     | Internet Check|     |  GPS Location |     | Notifications |     | Battery Exempt|
+---------------+     +---------------+     +---------------+     +---------------+     +---------------+
```

1. **Welcome Screen:** Brief overview of NaviCamp's accessibility mission.
2. **Internet Connection Verification:** Verifies network handshake with the campus database.
3. **High-Accuracy GPS Calibration:** Prompts the user to grant "Precise Location" access (*Allow all the time* or *Allow while using app*).
4. **Notification Authorization:** Enables sound and high-priority visual banners for critical alerts.
5. **Battery Optimization Whitelist:** Prompts the user to disable battery restrictions so location polling continues if the screen turns off during an emergency.
6. **Setup Complete:** Directs the user to the Authentication Portal.

---

# 2. USER MANUAL: LOCOMOTOR DISABLED USERS (STUDENTS & EMPLOYEES)

---

### 2.1 Account Registration & Medical Verification

```
+---------------------+     +--------------------------+     +------------------------+
| 1. Register Account | --> | 2. Verification Required | --> | 3. Visit Clinic (CHSW) |
| (Fill PWD Form)     |     |    (Features Locked)     |     |    Scan Doctor QR      |
+---------------------+     +--------------------------+     +-----------+------------+
                                                                         |
                                                                         v
                                                             +------------------------+
                                                             | 4. Account Activated!  |
                                                             |    Full SOS Enabled    |
                                                             +------------------------+
```

#### Step 1: Account Creation
1. Open NaviCamp and tap **Sign Up / Register**.
2. Select your Campus Affiliation: **Student** or **Employee**.
3. Fill in the required fields:
   * **Full Name:** Official registered name.
   * **School ID / Employee ID:** e.g., `20250006`.
   * **Campus Department / Program:** (e.g., *CCIS*, *CEA*, *CAS*).
   * **Email Address:** Active email for security verification.
   * **Contact Number:** 11-digit mobile phone number (e.g., `09XXXXXXXXX`).
   * **Emergency Contact Person & Number:** Crucial for safety officers to notify family or guardians during emergencies.
   * **Password:** Minimum 8 characters.
4. Tap **Create Account**.

#### Step 2: Verification Protocol at CHSW (Clinic)
For safety compliance and to prevent system abuse, all PWD accounts are created in an unverified state:
1. Upon logging in, the **Verification Required Screen** appears.
2. Visit the **Center for Health and Student Wellness (CHSW / Campus Clinic)**.
3. Present your official Medical Certificate or PWD Identification Card to the attending medical staff.
4. The clinic staff will generate an encrypted, single-use **Clinic Verification QR Code** specifying your disability status:
   * **Permanent Disability:** Perpetual verification.
   * **Temporary Disability:** Verified with an assigned expiration date (e.g., fracture recovery period).
5. In your NaviCamp app, tap **Scan Clinic Verification QR**, point your camera at the clinic's screen, and scan the QR code.
6. The app instantly unlocks and redirects you to the **Locomotor Home Dashboard**.

---

### 2.2 Connecting to an IoT Wheelchair Device

To activate the SOS button, NaviCamp must be paired with the smart wheelchair you are currently occupying.

```
+------------------------------------------------------------------------------------+
| LOCOMOTOR DISABLED HOME DASHBOARD                                                  |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Welcome back, Nicca Quintillan!                                                  |
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  WHEELCHAIR STATUS: NOT CONNECTED                                          |   |
|   +----------------------------------------------------------------------------+   |
|                                                                                    |
|                                [   CONNECT TO   ]                                  |
|                                [  A WHEELCHAIR  ]  <-- Tap here or bottom tab      |
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  Available Safety Officers on Campus: 3 Online                             |   |
|   +----------------------------------------------------------------------------+   |
|                                                                                    |
|   [ Home ]                    [ Scan QR ]                     [ Settings ]         |
+------------------------------------------------------------------------------------+
```

#### Step-by-Step Connection Guide:
1. Locate the **NaviCamp QR Identification Tag** mounted on the smart wheelchair frame.
2. In NaviCamp, tap either the gray **"CONNECT TO A WHEELCHAIR"** circle on the Home screen or select the **Scan QR** icon on the bottom navigation bar.
3. If prompted, grant Camera Permissions.
4. Align the viewfinder with the wheelchair's QR code.
5. The application will validate the device in the campus database:
   * If the wheelchair is **Available**, the app immediately pairs with the device.
   * If the wheelchair is **In Use by another user** or **Under Maintenance**, an on-screen alert will notify you to select another wheelchair.
6. Once connected:
   * The status bar turns **Green**: `Connected to: [Device ID]`.
   * The bottom navigation tab changes from *"Scan QR"* to *"Disconnect"*.
   * The central emergency button activates and turns bright **Red (SOS)**.

---

### 2.3 Requesting Emergency Assistance (SOS Flow)

NaviCamp features a safety-engineered emergency trigger system with visual, auditory, and haptic feedback.

```
+------------------+     +--------------------------+     +--------------------------+
| 1. Tap SOS       | --> | 2. 5-Second Countdown    | --> | 3. Alert Dispatched!     |
| (Red Button)     |     |    * Tap again = Instant |     |    * Pulsating Animation |
|                  |     |    * Slide = Cancel      |     |    * Live GPS Stream     |
+------------------+     +--------------------------+     +--------------------------+
```

```
+------------------------------------------------------------------------------------+
| EMERGENCY ACTIVATION SCREEN (COUNTDOWN STATE)                                      |
+------------------------------------------------------------------------------------+
|                                                                                    |
|                            SOS pending...                                          |
|             Tap the button to send SOS now or slide to cancel                      |
|                                                                                    |
|                                  ( 5 )   <-- 5-Second Countdown Ring               |
|                                                                                    |
|               [========== SLIDE TO CANCEL >>>>>>>>>>>> [X] ]                       |
|                                                                                    |
+------------------------------------------------------------------------------------+
```

#### Triggering an SOS Manually:
1. Tap the large red **SOS** button on your Home screen.
2. A **5-second countdown timer** begins with a circular progress animation.
3. During the 5-second countdown:
   * **To send immediately:** Tap the countdown number once to bypass the remaining seconds.
   * **To cancel an accidental press:** Drag the slider at the bottom all the way to the right (**"SLIDE TO CANCEL"**). A haptic vibration confirms cancellation.
4. When the countdown completes:
   * A heavy confirmation vibration triggers.
   * The button transitions into a pulsating **"ALERT SENT"** state.
   * Your high-precision GPS coordinates, floor level, user profile, and emergency contacts are transmitted to all on-duty safety officers.

#### Automated IoT Fall Detection:
If your smart wheelchair is equipped with the NaviCamp Fall Detection Sensor Node and detects a sudden impact or tilt beyond safety thresholds, the device automatically transmits a **FALL ALERT** to safety officers even if you cannot reach your phone.

---

### 2.4 Live Officer Dispatch & GPS Tracking Map

```
+------------------------------------------------------------------------------------+
| LIVE EMERGENCY DISPATCH & TRACKING                                                 |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  DISPATCH STATUS: Officer Jay Delos Santos is dispatched!                  |   |
|   |  Tap here to open Live Tracking Map >>>                                    |   |
|   +----------------------------------------------------------------------------+   |
|                                                                                    |
|   ============================ TRACKING MAP ====================================   |
|   |                                                                            |   |
|   |         [Officer: Jay (Azure Marker)]                                      |   |
|   |                     \                                                      |   |
|   |                      \ (Estimated 1-2 min away)                            |   |
|   |                       v                                                    |   |
|   |                 [You (Red Marker)]                                         |   |
|   |                                                                            |   |
|   ==============================================================================   |
|                                                                                    |
|   * Status: Tracking live location... Phone GPS stream active                      |
+------------------------------------------------------------------------------------+
```

1. As soon as a safety officer accepts your call, the **Officer Dispatch Card** appears on your Home screen: `[Officer Name] is dispatched`.
2. Tap the dispatch card to launch the **Officer Tracking Map Activity**.
3. **Map Features:**
   * **Red Marker & Accuracy Circle:** Your real-time position.
   * **Azure/Blue Marker & Accuracy Circle:** The responding officer's real-time position.
   * **Auto-Framing Camera:** The map automatically scales and adjusts to keep both you and the officer in view as they approach.
4. **Resolution:** When the officer arrives and resolves the incident on their terminal, the tracking map automatically concludes, showing *"Assistance has been resolved"*, and your home screen resets to normal.

---

### 2.5 Disconnection & Ending Wheelchair Session

When you reach your destination and no longer require the smart wheelchair:
1. Tap the **Disconnect** tab on the bottom navigation bar.
2. A confirmation dialog will appear: *"Do you want to disconnect from the current wheelchair?"*
3. Tap **Disconnect**.
4. The wheelchair is released in the database and marked as **Available** for other campus users. The SOS button on your phone resets to a gray state until paired with a new wheelchair.

---

### 2.6 Profile Management & Temporary Access Renewal

#### Viewing Profile Details:
* Navigate to **Settings** > **Profile Information** to view your School ID, registered email, disability status, emergency contact person, and registration date.

#### Updating Contact and Emergency Info:
1. Tap **Edit Account Details**.
2. Update your mobile number, emergency contact person, or emergency contact number.
3. If changing your registered email, tap **Verify** to receive a 6-digit One-Time PIN (OTP) sent to your new email inbox. Enter the OTP to validate the change.
4. Tap **Save Changes**.

#### Temporary Disability Expiration:
* Temporary PWD profiles display an **Access Expiry Banner** on the dashboard.
* If your recovery period expires, the SOS features are temporarily locked with the message: *"Temporary Verification Expired. Please visit the CHSW (Clinic) to reactivate features."*
* Present an updated doctor's note at the clinic to receive a renewed verification QR code.

---

# 3. USER MANUAL: SAFETY OFFICERS & CAMPUS ADMINISTRATORS

---

### 3.1 Officer Registration & Role-Based Verification

```
+--------------------------+     +-------------------------------+     +---------------------------+
| 1. Officer Signs Up      | --> | 2. Account Pending            | --> | 3. Admin Scans Admin QR   |
| (Selects Department)     |     |    (Restricted Access)        |     |    (Full Security Unlocked|
+--------------------------+     +-------------------------------+     +---------------------------+
```

#### Officer Account Creation:
1. Tap **Sign Up / Register**.
2. Select your User Role: **Safety Officer**.
3. Select your Operational Department:
   * **Security Services / CDMO / IFO:** Standard or Admin Field Officer.
   * **CHSW (Clinic):** Medical & Health Administrator.
4. Complete your profile details and submit registration.

#### Security Admin Verification:
To prevent unauthorized access to campus security dispatch channels:
1. Newly registered officers must present their institutional credentials to a **Security Administrator**.
2. The Administrator navigates to **Account Verification** on their app and generates an **Admin / Safety Officer Verification QR**.
3. The new officer scans the QR code from the **Verification Required Screen** to immediately activate their operational dispatch privileges.

---

### 3.2 Automated Geofence-Based On-Duty / Off-Duty Monitoring

NaviCamp incorporates an **Automated Campus Geofencing Engine** (centered at MMCL Coordinates: `14.244221, 121.112341`, with a `220-meter operational radius`).

```
+------------------------------------------------------------------------------------+
| CAMPUS GEOFENCE LOGIC                                                              |
+------------------------------------------------------------------------------------+
|                                                                                    |
|                  [ OUTSIDE GEOFENCE (>220m) ]                                      |
|                  * Status: OFF DUTY (Orange Badge)                                 |
|                  * Alert dispatching disabled                                      |
|                  * Prevents off-campus officers from getting assigned              |
|                                                                                    |
|                                  |                                                 |
|                                  v (Enters Campus Boundary)                        |
|                                                                                    |
|                  [ INSIDE CAMPUS GEOFENCE (<=220m) ]                               |
|                  * Status: ON DUTY (Green Badge)                                   |
|                  * Live GPS tracking enabled                                       |
|                  * Real-time emergency call routing active                         |
|                                                                                    |
+------------------------------------------------------------------------------------+
```

* **No Manual Toggle Required:** Officers do not manually clock in or out on the app. The system continuously polls device GPS in the background.
* **On-Duty State (Green Badge):** Automatically activated when inside the campus perimeter. The officer is visible on the central dispatcher map and can receive emergency calls.
* **Off-Duty State (Orange Badge):** Automatically engaged when an officer leaves the campus premises. If an off-duty officer attempts to accept an emergency call, the app displays an advisory warning that they are outside the operational area.

---

### 3.3 Real-Time Incident Dashboard & Live Presence Monitor

The **Officer Home Dashboard** provides a centralized operational summary:

```
+------------------------------------------------------------------------------------+
| SAFETY OFFICER COMMAND DASHBOARD                                                   |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Officer Jay Delos Santos                                      [ ON DUTY (Green) ]|
|   * Within school area                                                             |
|                                                                                    |
|   +---------------------------------+   +--------------------------------------+   |
|   |  REGISTERED PWDs: 24 Users      |   |  IOT WHEELCHAIRS: 8 Deployed         |   |
|   +---------------------------------+   +--------------------------------------+   |
|                                                                                    |
|   * Live Monitor: [● 4 Locomotor Users Online Now]                                 |
|                                                                                    |
|   ----------------- PEOPLE CURRENTLY IN NEED OF ASSISTANCE (1) -----------------   |
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  Nicca Quintillan                         [ FALL ALERT ] [ STATUS: PENDING]|   |
|   |  Date: August 20, 2026 | Time: 01:15 PM                                    |   |
|   |  Location: Einstein Building Ground Floor                                  |   |
|   |  Officer: No officer responded yet                                         |   |
|   |                                                                            |   |
|   |                               [ RESPOND NOW ]                              |   |
|   +----------------------------------------------------------------------------+   |
|                                                                                    |
|   [ Map Home ]  [ Monitor ]  [ Verify (Admin) ]  [ Incidents ]  [ Settings ]       |
+------------------------------------------------------------------------------------+
```

#### Key Dashboard Elements:
1. **Live Presence Indicator:** Displays real-time counts of active PWD users currently on campus via Firebase Firestore presence channels (`● X online now`).
2. **Assistance Type Badges:**
   * `[ FALL ALERT ]` (Red Badge): Triggered automatically by hardware accelerometer telemetry.
   * `[ MANUAL ]` (Blue/Gray Badge): Triggered manually by the user via the in-app SOS button.
3. **Status Badges:**
   * `PENDING` (Orange): Awaiting officer response.
   * `ONGOING` (Blue): An officer is en route.
   * `RESOLVED (REPORT PENDING)` (Orange): The emergency is handled, but the formal report is awaiting submission.

---

### 3.4 Interactive Campus Map Operations & Dispatching

Selecting **Map Home** from the bottom navigation bar loads the interactive Google Maps GIS command interface.

```
+------------------------------------------------------------------------------------+
| INTERACTIVE CAMPUS MAP OPERATIONAL VIEW                                            |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   [ Floating Filter Button (FAB) ]            [ Assistance Quick-Action (FAB) ]    |
|                                                                                    |
|   Map Markers:                                                                     |
|   * Red Marker (Flashing) : Pending PWD Emergency Call (Tap to open modal)         |
|   * Orange Marker         : Ongoing Emergency Assistance                           |
|   * Green Marker          : Resolved Assistance                                    |
|   * Azure Blue Marker     : Your Location (Officer Self)                           |
|   * Cyan Marker           : Other On-Duty Safety Officers                          |
|   * Blue Circle Boundary  : MMCL Campus Geofence (220m radius)                     |
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  DYNAMIC MAP LEGEND                                                        |   |
|   |  ● You (Officer)  ● Officer Christian  ● Nicca Quintillan (PENDING)        |   |
|   +----------------------------------------------------------------------------+   |
+------------------------------------------------------------------------------------+
```

#### Map Filter Controls (Filter FAB):
Officers can customize map clutter by toggling display layers:
* `[X] Pending Calls`
* `[X] Ongoing Calls`
* `[X] Resolved Calls`
* `[X] Other Safety Officers`
* `[X] School Geofence Area`

---

### 3.5 Assistance Response, Resolution, and Incident Reporting

```
+----------------------+     +-----------------------+     +------------------------+
| 1. Tap "Respond"     | --> | 2. Assist PWD User    | --> | 3. Complete Report     |
|    Map routes to PWD |     |    Tap "Resolve"      |     |    (Action & Location) |
+----------------------+     +-----------------------+     +------------------------+
```

#### Step 1: Responding to a Call
1. Tap **Respond** on an Assistance Card or tap a **Red Marker** on the map to open the **Assistance Modal Dialog**.
2. Review critical incident data:
   * **User Name & School ID**
   * **Floor Level & GPS Coordinates**
   * **User Mobile Number** (tap to call)
   * **Emergency Contact Person & Phone Number**
3. Tap **"RESPOND"**.
4. The incident status changes to `ONGOING`. All other officers are notified that you are handling the incident. Your live GPS coordinates begin streaming directly to the PWD user's map.

```
+------------------------------------------------------------------------------------+
| ASSISTANCE MODAL DIALOG (OPERATIONAL MODAL)                                        |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Einstein Building Ground Floor                             [ ONGOING (Blue) ]    |
|   Student: Nicca Quintillan (ID: 20250006)                                         |
|   Contact: 09083356963 | Emergency: Agnes Quintillan (09171234567)                |
|                                                                                    |
|   [Card: You are responding to this assistance request]                            |
|                                                                                    |
|   [ RESOLVE INCIDENT ]                     [ FALSE ALARM (Dismiss) ]               |
|                                                                                    |
+------------------------------------------------------------------------------------+
```

#### Step 2: Resolving the Incident
* If the call was accidental or invalid: Tap **"FALSE ALARM"**. The incident is logged as a false alarm and dismissed.
* If assistance was provided: Tap **"RESOLVE INCIDENT"**.

#### Step 3: Mandatory Post-Resolution Incident Report
Upon resolving an incident, the modal immediately displays the **Incident Reporting Section**:

```
+------------------------------------------------------------------------------------+
| POST-RESOLUTION INCIDENT REPORTING FORM                                            |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Select Relocation / Destination Zone:                                            |
|   [ (GYM) ]  [ (FIELD) ]  [ (STUDENT LOUNGE) ]  [ (CLINIC) ]  [ (OTHER) ]          |
|                                                                                    |
|   First Aid / Immediate Action Taken (*Required):                                  |
|   [ Stabilized wheelchair, assisted student to clinic wheelchair bay             ] |
|                                                                                    |
|   Further Information / Follow-up Notes (*Required):                               |
|   [ Student suffered mild ankle strain; referred to Dr. Santos at CHSW.          ] |
|                                                                                    |
|                            [ SUBMIT INCIDENT REPORT ]                              |
+------------------------------------------------------------------------------------+
```

1. Select the relocation area where the user was escorted (*Gym, Field, Student Lounge, Clinic, or Other*). If *Other*, specify the exact room or area.
2. Enter the **First Aid Action Taken** (e.g., *Provided ice pack, uprighted wheelchair, assisted transfer*).
3. Enter **Further Information / Observations** (e.g., *Referred to attending nurse, no structural equipment damage*).
4. Tap **Submit Incident Report**. The incident log is officially completed and archived.

---

### 3.6 Incident Logbook, Custom Filtering & Report Exporting

The **Incidents Tab** provides an audit trail of all historical campus emergencies.

```
+------------------------------------------------------------------------------------+
| INCIDENT LOGBOOK & REPORT GENERATOR                                                |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Summary Counters:                                                                |
|   [ Total Pending: 0 ]       [ Ongoing: 1 ]       [ Total Resolved: 42 ]           |
|                                                                                    |
|   [ FILTER OPTIONS ]                                  [ EXPORT REPORTS ]           |
|                                                                                    |
|   Incident Records:                                                                |
|   * Alert #ALT006F6 | Nicca Quintillan | Fall Alert | Resolved by: Jay Delos Santos|
|   * Alert #ALT008H8 | Nicca Quintillan | Manual SOS | Marked: False Alarm          |
|   * Alert #ALT010J0 | dcristian        | Manual SOS | Resolved by: Jay Delos Santos|
|                                                                                    |
+------------------------------------------------------------------------------------+
```

#### Multi-Dimensional Filter Dialog:
Tap **Filter Options** to filter records by:
* **Date Presets:** *All Time, Today, This Week, This Month, This Year, or Custom Date Range (Start/End Calendar Pickers)*.
* **Incident Status:** *All Status, Pending, Ongoing, Resolved, False Alarm*.
* **Relocation Location:** *All Locations, Gym, Field, Student Lounge, Clinic, Other*.

#### Exporting Formal Incident Reports:
Tap **Export Reports** to generate documentation for university administrators:

| Export Option | File Format | Description & Usage |
| :--- | :--- | :--- |
| **Excel Spreadsheet** | `.xlsx` | Full raw tabular data export including timestamps, GPS coordinates, response duration, officer IDs, and action logs. |
| **PDF Document** | `.pdf` | Formatted official executive summary ready for printing or official academic submission. |
| **Email / Share** | `.xlsx` + `.pdf` | Bundles both Excel and PDF reports into an automated Android system share intent to email directly to campus department heads. |

---

### 3.7 IoT Wheelchair Fleet Management & Maintenance

NaviCamp provides full lifecycle oversight of smart wheelchair units deployed across campus.

```
+------------------------------------------------------------------------------------+
| SMART WHEELCHAIR FLEET MANAGEMENT                                                  |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Filter: [ All / Available / In Use / Maintenance ]   Search: [ Device ID / Name ]|
|   Total Wheelchairs Registered: 8 Units                                            |
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  WHEELCHAIR #202501 (Ground Floor - Einstein)     [ AVAILABLE (Green) ]    |   |
|   |  Current User: None Assigned                                               |   |
|   |  Battery/Telemetry: Online | Signal: Strong                                |   |
|   |                                                                            |   |
|   |  [ VIEW FULL DETAILS & CONTROLS ]                                          |   |
|   +----------------------------------------------------------------------------+   |
|                                                                                    |
|   [+] ADD NEW WHEELCHAIR (Admin Only Floating Button)                              |
+------------------------------------------------------------------------------------+
```

#### Wheelchair Management Actions:
* **View Details:** View current occupant ID, current floor level, connection expiry, and hardware telemetry.
* **Rename Device:** Assign clear location-based aliases (e.g., *"Library Bay Wheelchair 02"*).
* **Set Maintenance Mode:** If a wheelchair suffers mechanical or battery failure, officers can place the device into **Maintenance Mode** with a custom reason note (e.g., *"Left tire pressure low - under repair"*). This prevents PWD users from connecting to the damaged wheelchair.
* **Remove Maintenance Mode:** Restores the unit back to **Available** status once repaired.
* **[Admin Only] Generate QR Code:** Generates a high-resolution 512x512 QR code tag. Administrators can tap **Save to Gallery** to store the tag in the phone's `Pictures/NaviCamp` directory for printing and physical wheelchair labeling.
* **[Admin Only] Add New Device:** Registers a newly provisioned IoT microcontroller into the database.
* **[Admin Only] Delete Device:** Decommissions a retired wheelchair unit.

---

### 3.8 Admin Module: Verification QR Generator

Security Administrators and CHSW Clinic Staff have access to the **Account Verification Tab** to authenticate new accounts and issue role credentials.

```
+------------------------------------------------------------------------------------+
| ADMIN VERIFICATION QR GENERATOR                                                    |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Verification Type: [ Temporarily Disabled User Verification     v ]              |
|                                                                                    |
|   Valid Until Date: [ 2026-11-30 ]  (Date Picker)                                  |
|                                                                                    |
|                          [ GENERATE VERIFICATION QR ]                              |
|                                                                                    |
|   ============================ ONE-TIME SECURE QR ==============================   |
|   |                                                                            |   |
|   |                       [ 760x760 ENCRYPTED QR CODE ]                        |   |
|   |                                                                            |   |
|   ==============================================================================   |
|   * One-time QR ready. Expires in: 09:42                                           |
|   * Scan Status: [ Not yet scanned / Consumed ]                                    |
|                                                                                    |
+------------------------------------------------------------------------------------+
```

#### Security Architecture:
1. **Password Authorization:** Tapping *Generate Verification QR* triggers a secure password authentication prompt to confirm admin authorization.
2. **One-Time Nonce & 10-Minute Expiry:** Each generated QR contains a unique 32-character cryptographic nonce and automatically invalidates after **10 minutes** or immediately upon being scanned once.
3. **Live Countdown & Real-Time Scan Monitor:** The generator interface features an active countdown timer and dynamically polls the database every 3 seconds to update the on-screen badge from *"Not yet scanned"* to *"Scanned / Consumed"*.

#### Verification Types Supported:
* **Safety Officer Verification:** Authorizes standard security personnel.
* **Admin Verification:** Promotes security personnel to full Administrator status.
* **Permanently Disabled User Verification:** Authorizes permanent PWD student/employee accounts.
* **Temporarily Disabled User Verification:** Prompts the admin for a mandatory **Valid Until Date** picker (e.g., 3 months).

---

### 3.9 Admin Module: Registered PWD Directory & Audit Trail

Accessible via the **Registered Users** tile on the Dashboard, this module provides an administrative directory of all verified PWD users on campus.

```
+------------------------------------------------------------------------------------+
| VERIFIED PWD USER DIRECTORY                                                        |
+------------------------------------------------------------------------------------+
|                                                                                    |
|   Filter Type: [ All / Temporary / Permanent ]        Search: [ Name / ID / Dept ] |
|   Total Verified Users: 24                                                         |
|                                                                                    |
|   +----------------------------------------------------------------------------+   |
|   |  Nicca Quintillan                         [ PERMANENT (Green Badge) ]      |   |
|   |  School ID: 20250006 | Dept: CCIS                                          |   |
|   |  Email: nicca@gmail.com | Contact: 09083356963                             |   |
|   |  Emergency: Agnes Quintillan (09171234567)                                 |   |
|   |  Verified by: Jay Delos Santos | Verified on: 2025-06-08 01:12 PM          |   |
|   +----------------------------------------------------------------------------+   |
+------------------------------------------------------------------------------------+
```

* **Account Transparency & Audit Trail:** Every user card displays who verified the account (`Verified by: [Officer Name]`) and the exact timestamp of verification.
* **Emergency Lookup:** Safety officers can look up any student's emergency contacts and medical classifications during campus-wide drills or evacuations.

---

# 4. SYSTEM TROUBLESHOOTING & EMERGENCY FAQS

---

### 4.1 Common Technical Issues & Solutions

| Symptom / Error Message | Root Cause | Step-by-Step Resolution |
| :--- | :--- | :--- |
| **SOS Button is Gray / "CONNECT TO A WHEELCHAIR"** | App is not currently paired with an active IoT wheelchair device. | 1. Navigate to the **Scan QR** tab.<br>2. Scan the QR code mounted on your smart wheelchair.<br>3. Verify that the button turns red and reads "SOS". |
| **"Temporary Verification Expired" Banner** | Assigned medical validity date for temporary disability has passed. | 1. Visit the CHSW Campus Clinic.<br>2. Present an updated medical certificate.<br>3. Scan the renewed temporary verification QR code provided by medical staff. |
| **Officer Status Shows "OFF DUTY" While on Campus** | Device GPS accuracy is low or fine location permissions are disabled. | 1. Ensure phone GPS is set to **High Accuracy**.<br>2. Step out from dense concrete basements to acquire a satellite lock.<br>3. Check app settings: Ensure Location Permission is set to *"Allow all the time"*. |
| **"Device is currently In Use / Maintenance"** | The scanned wheelchair is occupied by another user or flagged for repairs. | 1. Check if another user forgot to disconnect.<br>2. If damaged, locate another available wheelchair on campus.<br>3. Safety officers can clear stale connections via the Wheelchair Management console. |
| **Camera Viewfinder is Black During QR Scan** | Camera permission was denied or interrupted. | 1. Open Android System Settings > Apps > NaviCamp > Permissions.<br>2. Enable **Camera** permission.<br>3. Restart the NaviCamp application. |
| **Live Officer Location is Not Updating on Map** | Responding officer's network dropped or battery saver throttled background sync. | 1. Check your Wi-Fi/data connection.<br>2. The app automatically retries database sync every 3 seconds.<br>3. If urgent, tap the phone icon on the dispatch card to call the officer directly. |

---

### 4.2 Frequently Asked Questions (FAQs)

#### Q1: What happens if I accidentally press the SOS button?
**Answer:** You have a **5-second safety buffer window**. Simply drag the bottom slider completely to the right (**"SLIDE TO CANCEL"**). The countdown will abort, your phone will give a short vibration, and no emergency dispatch will be sent.

#### Q2: What should I do if an emergency SOS is triggered as a False Alarm?
**Answer:** If the alert already went through, remain where you are. When the safety officer contacts you or arrives at your location, inform them that it was an accidental trigger. The officer will select **"False Alarm"** on their modal, which cleanly closes the log without penalty.

#### Q3: Does the smart wheelchair track my location when I am not in an emergency?
**Answer:** No. To protect user privacy and conserve battery life, high-frequency real-time GPS streaming to safety officers is only activated when an active emergency SOS or Fall Alert is in progress.

#### Q4: How long does a wheelchair connection last?
**Answer:** Once connected via QR code, your session remains active throughout your stay on campus until you manually tap **Disconnect**.

#### Q5: Can multiple officers respond to the same emergency call?
**Answer:** All on-duty officers receive the emergency alert simultaneously. The moment the first officer taps **"RESPOND"**, the call is assigned to that officer, and all other officers' dashboards update to show `Officer [Name] is responding`, preventing duplicate responses while keeping the team informed.

---

# 5. SYSTEM AUDIT, DATA PRIVACY & GLOSSARY

---

### 5.1 Data Privacy & Institutional Compliance (Republic Act 10173)
NaviCamp complies with the **Philippine Data Privacy Act of 2012 (RA 10173)**:
1. **Medical Confidentiality:** Specific diagnoses and medical history are not stored in public tables. Only functional mobility classifications (*Permanent* or *Temporary*) and access expiration dates are maintained.
2. **Audit Logging:** All administrative actions—including user verification, role elevation, device modifications, and incident report submissions—record the ID of the authorizing officer and an immutable timestamp.
3. **Encrypted Passwords:** Passwords are never stored in plaintext; all user credentials undergo SHA-256 cryptographic hashing prior to database transmission.

---

### 5.2 Glossary of Technical & Operational Terms

* **BLE (Bluetooth Low Energy):** Low-power wireless protocol used by IoT microcontroller nodes on the smart wheelchairs.
* **Geofence:** A virtual geographic boundary defined by GPS coordinates (`220m radius around MMCL`) used to automatically calculate safety officer on-duty availability.
* **Haptic Feedback:** Physical tactile vibrations produced by the smartphone to confirm emergency activations, slider cancellations, and alert deliveries without requiring the user to look at the screen.
* **Locomotor Disability:** Physical disability affecting movement, ambulation, and motor dexterity.
* **Nonce (Number used Once):** A unique, single-use security token embedded into verification QR codes to prevent unauthorized code copying or replay attacks.
* **Presence Channel:** A real-time WebSocket/Firestore channel monitoring active heartbeat connections to determine how many PWD users are currently online on campus.

---

```
================================================================================
END OF USER MANUAL
NaviCamp: Campus Assistance & Emergency Dispatch Platform
Developed for Mapúa Malayan Colleges Laguna (MMCL)
================================================================================
```
