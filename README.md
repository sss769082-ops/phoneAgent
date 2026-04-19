# Phone Agent — Setup Guide

## What this does
Your Android phone runs a background agent. You control it from any laptop/PC in the world via a web dashboard.

Features:
- Read contacts
- Read call log
- Read SMS messages
- Take photos (camera)
- Capture screen (screenshot)
- List and launch installed apps
- Device info
- Works from ANYWHERE via ngrok tunnel

---

## Step 1 — Get a free ngrok account

1. Go to https://ngrok.com
2. Sign up for a free account
3. Go to Dashboard → "Your Authtoken"
4. Copy the long token string (looks like: 2abc123xyz_ABCDEF...)

---

## Step 2 — Build and install the Android app

### Requirements
- Android Studio (download from https://developer.android.com/studio)
- Android phone with USB cable

### Steps
1. Open Android Studio
2. Click "Open" and select the `PhoneAgent` folder
3. Wait for Gradle to sync (may take 2-3 minutes first time)
4. Connect your phone via USB
5. Enable "Developer options" on your phone:
   - Settings → About phone → tap "Build number" 7 times
   - Settings → Developer options → USB debugging ON
6. Click the green "Run" button (▶) in Android Studio
7. Select your phone from the device list
8. The app will install and open

---

## Step 3 — Start the agent

1. Open the "Phone Agent" app on your phone
2. Paste your ngrok authtoken into the text field
3. Tap "Start Agent"
4. Grant ALL permissions when asked (contacts, calls, SMS, camera, notifications)
5. Wait about 10-15 seconds for the tunnel to start
6. You will see a URL like: `0.tcp.ngrok.io:12345`
7. Copy that URL

---

## Step 4 — Open the dashboard

1. On your laptop/PC, open the file `web-dashboard/index.html` in Chrome or Firefox
2. Paste the URL from Step 3 into the input box (e.g. `0.tcp.ngrok.io:12345`)
3. Click "Connect"
4. You are now connected! Use the sidebar to access contacts, calls, SMS, camera, etc.

---

## Notes

- The agent starts automatically when your phone boots (after first setup)
- The ngrok URL changes every time the agent restarts (free tier)
   - Upgrade to ngrok paid plan for a fixed URL
- Screen capture requires accepting a permission popup on the phone the first time
- Keep the app in the battery optimization whitelist:
  - Settings → Battery → App battery usage → Phone Agent → Unrestricted

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Cannot connect" | Make sure agent is running and URL is correct |
| Contacts/SMS empty | Grant permissions in Settings → Apps → Phone Agent |
| ngrok not starting | Check authtoken is correct, check internet on phone |
| App killed in background | Disable battery optimization for the app |
| Screen capture not working | Accept the permission popup on the phone |
