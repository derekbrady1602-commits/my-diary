# Snezzy's Diary

An affirmation, gratitude, diary, lists and appointments app for Android tablets and phones, made for writing with a pen.

## Install on the tablet from VS Code

1. On the tablet turn on Developer options and USB debugging, then plug it into the PC with a USB cable and accept the "Allow USB debugging" prompt.
2. Open this folder in VS Code.
3. In the VS Code terminal check the tablet shows up

   ```
   adb devices
   ```

4. Build and install the app

   ```
   .\gradlew installDebug
   ```

   On Mac or Linux use `./gradlew installDebug`

5. Snezzy's Diary appears in the tablet's app drawer.

If Gradle says it can't find the Android SDK, create a file called `local.properties` in this folder with one line pointing at your SDK, for example

```
sdk.dir=C\:\\Users\\Derek\\AppData\\Local\\Android\\Sdk
```

Java 17 or newer is needed for the build.

## Where things are

| File | What it does |
|---|---|
| `app/src/main/assets/index.html` | The whole diary (calendar, templates, pen drawing, saving) |
| `app/src/main/java/ie/derek/snezzysdiary/MainActivity.kt` | Shows the diary full screen and handles backups and the back button |
| `app/src/main/res/values/strings.xml` | The app name |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The app icon |

To change the daily affirmations or prompts, edit the `PROMPTS` lists near the top of the script in `index.html`. To add or change a template, edit the `TEMPLATES` list just below it. Each template is a list of sections (lines, numbered, checklist, pick, mood, space, habit, twocol) and `add: true` gives a section a + button. Run `.\gradlew installDebug` again afterwards. Your diary pages are kept when you reinstall over the top.

## Making a list from Excel

In the Lists tab of any day, or under My lists › New list, tap **Make a list from Excel** and pick an `.xlsx`, `.xls`, `.ods` or `.csv` file. Each row becomes a tick box. If the sheet has several columns you can choose whether each row is one item or each column is its own section. If the file has several sheets you can pick one or make a list from every sheet.

## Appointment alarms

On any Appointments page tap **⏰ Set an alarm**, choose the date and time and when to be reminded (at the time, 10 or 30 minutes before, an hour before or the day before). Android rings the alarm even if the app is closed, and sets them again after the phone restarts. The first time, Android asks to allow notifications, so say yes. Every linked device with the app rings, so turn it off on the tablet in Settings › Notifications if you only want the phone.

## Pen page tools (tablet)

- **T (Text)** lets you type with the keyboard. Tap a line to type on it (Enter jumps to the next line), or tap an empty spot to drop a text box there.
- **Select** lets you draw a loop around anything on the page (handwriting, typed text on the lines or text boxes), then drag it somewhere else. The little bar lets you delete or copy what you picked.
- **Tap a tick box** with the pen or your finger to tick or untick it.
- **Swipe across a ticked item** (a quick sideways line with the pen, or a finger swipe) and it asks whether to delete that row or keep it. Deleting moves everything under it up.
- Scrolling down a page hides the menus so the page fills the screen. Scroll up a little, or tap **Menus**, to bring them back.

## Make your own templates

Open any tab on a day and tap **+ Make your own** at the start of the template list. Give it a name, choose its tab, then add sections like questions, lines, numbered lists, tick boxes, two columns, tables, choice buttons, mood faces, habit trackers, free or dotted space, daily prompts and notes. Use the arrows to reorder them. A live preview shows how it looks. Your templates appear first in the list with an **Edit** button and sync to your other devices.

## Look

At the bottom of the calendar under **Look** you can switch dark mode on or off, pick a page colour (Cream, White, Pink, Peach, Lemon, Mint, Sky, Lavender, Grey, Night or Black) and pick a header colour.

## Keeping your pages safe

Pages are saved inside the app on the tablet. Uninstalling the app deletes them, so use "Save a backup" at the bottom of the calendar now and then. It puts a file in the tablet's Downloads folder, and "Restore a backup" brings it back.

## Linking your phone and tablet

The same app installs on your phone and your tablet. To keep them in sync you need a free Firebase project. This is a one-off setup that takes about ten minutes.

1. Go to https://console.firebase.google.com and sign in with your Google account.
2. Click **Create a project**, call it `snezzys-diary` and turn off Google Analytics (you don't need it).
3. In the left menu open **Build › Authentication**, click **Get started**, choose **Email/Password**, switch it on and save.
4. Still in Authentication open the **Settings** tab, then **Authorised domains**, click **Add domain** and add `appassets.androidplatform.net`
5. In the left menu open **Build › Firestore Database**, click **Create database**, pick the `eur3 (europe-west)` location and start in **production mode**.
6. In Firestore open the **Rules** tab, replace everything with the rules below and press **Publish**

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```

   This means only you, signed in, can ever read or write your diary.
7. Click the gear icon beside **Project Overview**, choose **Project settings**, scroll to **Your apps** and click the **</>** (web) icon. Call it `Snezzy's Diary` and register it. Don't tick Hosting.
8. Firebase shows a `firebaseConfig` block. Copy the part between the curly brackets into `app/src/main/assets/firebase-config.js` so it looks like this

   ```
   window.SNEZZY_FIREBASE = {
     apiKey: "AIza...",
     authDomain: "snezzys-diary.firebaseapp.com",
     projectId: "snezzys-diary",
     storageBucket: "snezzys-diary.appspot.com",
     messagingSenderId: "1234567890",
     appId: "1:1234567890:web:abc123"
   };
   ```

9. Run `.\gradlew installDebug` with the tablet plugged in, then again with the phone plugged in.
10. On the tablet scroll to the bottom of the calendar, type your email and a password and press **Create account**. On the phone type the same email and password and press **Sign in**.

From then on every page you write is saved on the device and sent to your account, and changes from the other device appear by themselves. If you're offline it keeps a note and syncs when the internet comes back. The newest version of a page always wins, so try not to write on the same page on both devices at the exact same time.

The free Firebase plan is far more than a diary needs.
