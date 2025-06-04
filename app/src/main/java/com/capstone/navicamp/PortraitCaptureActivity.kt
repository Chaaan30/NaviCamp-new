package com.capstone.navicamp

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * This activity extends CaptureActivity to ensure the QR code scanner is locked to portrait mode.
 * It is referenced in ScanOptions by LocomotorDisabilityActivity.
 */
class PortraitCaptureActivity : CaptureActivity() {
    // The CaptureActivity from zxing-android-embedded handles screen orientation
    // based on the settings passed in ScanOptions and what's in the AndroidManifest.
    // By default, CaptureActivity is set to "fullSensor" which allows any orientation.
    // To lock it to portrait, we would typically set screenOrientation="portrait"
    // in the AndroidManifest.xml for this specific activity.
    // However, since we are setting options.setOrientationLocked(true) in ScanOptions,
    // the library should respect this. If it doesn't, manifest declaration is the fallback.
    // For now, this class primarily serves as a target for the captureActivity option.
}

