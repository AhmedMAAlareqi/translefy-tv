# TV Web – open a website as an app on Fire TV

A small Android app that opens one website full-screen on an Amazon Fire TV Stick
(or any Android TV / Google TV box), controlled with the normal TV remote.

## Set your website (optional)

Open `app/src/main/java/com/ahmed/tvweb/MainActivity.java` and set:

```java
static final String DEFAULT_URL = "https://your-site.com";
```

If you leave it empty, the app asks for the address the first time it opens.
You can change it any time from the ≡ Menu button.

## Remote control

| Button        | Action |
|---------------|--------|
| D-pad         | Move the on-screen pointer (the page scrolls when it reaches an edge) |
| Select (OK)   | Click where the pointer is (hold for a long-press) |
| Back          | Previous page; on the first page, exit |
| ≡ Menu        | Change website, Reload, Home, Desktop site, Pointer on/off, Exit |
| Play/Pause    | Pointer on/off (off = normal D-pad focus navigation) |
| ⏪ / ⏩        | Scroll one page up / down |

## Build the APK

### Option A – GitHub, no software needed (recommended)
1. Create a new **public** repository on GitHub (e.g. `tv-web`).
2. Upload all files from this folder (keep the `.github` folder; it's hidden on some PCs).
3. Open the **Actions** tab and wait about 3–5 minutes for "Build APK" to finish (green tick).
4. The APK is now at:
   `https://github.com/<your-username>/<repo>/releases/download/latest/tv-web.apk`

### Option B – Android Studio on your PC
1. File ▸ Open ▸ select this folder, and let Gradle sync.
2. Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s).
3. The APK is in `app/build/outputs/apk/`.

## Install on the Fire TV Stick

1. **Settings ▸ My Fire TV ▸ About**: click the device name 7 times until it says
   "You are now a developer".
2. Install **Downloader** (orange icon, by AFTVnews) from the Fire TV app store.
3. **Settings ▸ My Fire TV ▸ Developer options ▸ Install unknown apps**: turn **Downloader** ON.
4. Open Downloader, type the APK link from Option A, then press **Install**.
   (Tip: shorten the long link first with a URL shortener so it's quicker to type.)
5. The app appears as **Translefy** under *Your Apps & Channels ▸ See all*.
   Move it to the front of the row so it's on the home screen.

Alternative: with **ADB debugging** turned on in Developer options:
```
adb connect <fire-tv-ip>:5555
adb install tv-web.apk
```

> Note: the newest budget Fire TV sticks (e.g. Fire TV Stick 4K Select / newest HD model)
> run Amazon's Vega OS and do not allow installing APKs. Older Fire OS models work.
