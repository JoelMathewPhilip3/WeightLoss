# Da Thadiya — GitHub APK Build + Firebase Sync

This repository builds the Android APK using GitHub Actions. You do not need Android Studio.

## Folder purpose

- `website/index.html`
  - Upload this file to GitHub Pages.
  - This is the version your laptop opens.
  - It includes Firebase auto-sync.
  - The repeated motivation line was removed from the calendar tab.

- Android app files in the root/app folders
  - GitHub Actions builds the APK.
  - The app is a native Android WebView wrapper.
  - It opens your GitHub Pages website inside the app.

## Before building APK

Open:

`app/src/main/java/com/dathadiya/app/MainActivity.kt`

Replace:

`https://YOUR-GITHUB-USERNAME.github.io/YOUR-REPOSITORY-NAME/`

with your actual GitHub Pages website URL.

Example:

`https://joelmathewphilip.github.io/da-thadiya/`

## How to build APK using GitHub only

1. Create a new GitHub repository.
2. Upload all files from this package to the repository.
3. Go to the repository's `Actions` tab.
4. Choose `Build Da Thadiya APK`.
5. Click `Run workflow`.
6. After it finishes, open the completed run.
7. Download the artifact named `DaThadiya-debug-apk`.
8. Inside it is `app-debug.apk`.

## Important Android install note

This creates a debug APK. Android may warn that it is from an unknown source. That is normal for a private APK not uploaded to the Play Store.

## Firebase sync

GitHub Pages cannot sync saved data by itself. Firebase Firestore is used so your laptop website and Android app share the same data.

The sync is automatic. There is no Master/Load button.

When you edit a mission, mark complete, add tomorrow mission, or add weight:
- it saves locally first
- then automatically saves to Firebase
- the other device updates automatically when it receives the Firestore update

## Firebase config

In `website/index.html`, replace this block:

```js
const firebaseConfig = {
  apiKey: "PASTE_YOUR_FIREBASE_API_KEY_HERE",
  authDomain: "PASTE_YOUR_PROJECT_ID.firebaseapp.com",
  projectId: "PASTE_YOUR_PROJECT_ID",
  storageBucket: "PASTE_YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "PASTE_YOUR_MESSAGING_SENDER_ID",
  appId: "PASTE_YOUR_APP_ID"
};
```

with your Firebase Web App config.

## Firestore document path

The app stores everything here:

`daThadiyaUsers / joel`

## Suggested Firestore rules while building

Use these only for personal/testing use:

```txt
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /daThadiyaUsers/joel {
      allow read, write: if true;
    }
  }
}
```

For a public app, do not keep open write rules like this.
