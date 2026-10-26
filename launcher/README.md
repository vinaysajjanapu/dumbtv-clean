# Dumb TV Clean

Thanks to [Muzzammil](https://github.com/humblemuzzu) for the original [dumbtv](https://github.com/humblemuzzu/dumbtv).

A Fire TV home screen by Vinay Sajjanapu. It speaks the same language as the television: Amazon Ember, the type already on the device, dark tiles, and the three-pane settings Fire TV uses. A focused tile grows slightly and keeps a white outline. A focused row becomes a light bar with dark text. The screen is your apps, a clock, a greeting and a wallpaper, drawn as a clean, polished launcher.

Plain Java and Android views. No AndroidX, Kotlin, Compose or Play Services. Application id `tv.vinay.dumbtv`. Fetching a wallpaper you asked for is the only time it uses the network. On Fire OS 7 and 8 that internet permission is the only one. There are no ads, no recommended rows, no account and no analytics.

## Home

<p align="center">
  <img src="../docs/assets/screens/home.jpg" alt="Home: a dusk road, the time, Good Evening Vinay, and a row of apps" width="880">
</p>

The time sits on the left. A short greeting follows the time of day, and your name is added when you set one, so it reads "Good Evening, Vinay". The wallpaper fills the screen behind it. Your apps are one row along the bottom. YouTube and Netflix use their own tile art. Every other app keeps the banner it shipped with.

All Apps, System Settings and Home Settings sit at the top right. The name shows under the icon that has focus.

| Remote | Does |
|---|---|
| OK | open the app |
| Hold OK on a tile, or ☰ Menu | Move, Hide, App Info |
| Up from the top row | All Apps, System Settings, Home Settings |
| Back on the home screen | close All Apps, or jump to the first tile |
| Hold HOME | Fire OS quick menu (Amazon's, unchanged) |

## All Apps

<p align="center">
  <img src="../docs/assets/screens/all-apps.jpg" alt="All Apps: a panel listing every installed app over the home screen" width="880">
</p>

All Apps opens from the grid icon. Every installed app is in the panel on the right, including apps kept off the home row. OK opens the one in focus. Hold OK and a small menu sits beside that name: App Info, or add it to the home row and take it off again. Back closes the menu, then the list.

## On a tile

<p align="center">
  <img src="../docs/assets/screens/options.jpg" alt="Hold OK on a tile: Move, Hide and App Info" width="880">
</p>

Hold OK on a home tile, or press ☰ Menu. Move, Hide and App Info. In Move, left and right slide the tile. Any other key drops it where it is.

## Home Settings

<p align="center">
  <img src="../docs/assets/screens/settings.png" alt="Home Settings: Your Name, Wallpaper, Apps, Inputs, Greeting, and Exit to Amazon Home" width="880">
</p>

Home Settings opens like Fire TV settings: a dark tile grid, then a section in three panes. The focused tile grows a little and keeps a white outline. The grid uses Amazon Ember.

| Tile | What it does |
|---|---|
| Your Name | The name in the greeting. The tile shows the name once you set one. OK opens the editor. Blank leaves the greeting as "Good Evening" |
| Wallpaper | Search query, darkness, picture rotation, and a new picture now |
| Apps | Sort order, whether new apps join the home row, and which apps are on that row |
| Inputs | Show or hide the inputs row, which inputs appear, and where each one opens |
| Greeting | Time of day, or your own words |
| Exit to Amazon Home | Leaves Dumb TV Clean and opens Amazon's launcher |

## Wallpaper

<p align="center">
  <img src="../docs/assets/screens/wallpaper.png" alt="Wallpaper settings: search query, darkness, and a 16:9 preview" width="880">
</p>

Search Query, Darkness, Picture Rotation, and Get Another Picture. The right pane is a 16:9 preview of the picture on the home screen, with a short note beside the row in focus. Left and right, or OK, change the row in focus. OK on Search Query opens the editor.

The picture comes from [Wallhaven](https://wallhaven.cc), general and safe, at least 1920 wide. A blank search looks for abstract dark. A search already saved on the TV is left as it is. Darkness applies on the preview and on the home screen immediately. Picture Rotation asks for another when its turn comes: off, every return home, every hour, every day, or as the time of day changes. Get Another Picture asks now. The chosen picture's address is saved, so a restart shows the same one. Nothing else in the app opens a connection.

## Apps

<p align="center">
  <img src="../docs/assets/screens/apps.png" alt="Apps settings: sort order, add new apps automatically, and the home row" width="880">
</p>

Two preferences, then the home row.

Sort Order is Custom, Alphabetical, or Recently Used. Custom keeps the order you set with Move. Add New Apps Automatically is On or Off, on the same kind of row: left and right, or OK, change it. On puts a newly installed app on the home row. Apps already installed stay as they are.

Home Row lists every installed app. The switch on a row puts that app on the home screen or takes it off. An app that is off here is still in All Apps. The note on the right follows the row in focus.

## Inputs

<p align="center">
  <img src="../docs/assets/screens/inputs.png" alt="Inputs settings: the inputs row, where each input opens, and ARC" width="880">
</p>

The inputs row stays off until you show it. Inputs Row is Shown or Hidden. Open Each Input In is Automatic, The TV's App, or Dumb TV Clean. Each input on the television is listed under that, Shown or Hidden. Left and right, or OK, change the row in focus.

## Greeting

<p align="center">
  <img src="../docs/assets/screens/greeting.png" alt="Greeting settings: time of day, with a preview that reads Good Evening, Vinay" width="880">
</p>

Greeting is Time of Day or Custom. Time of Day follows the clock and then your name. Custom Greeting replaces that whole line, including the name. Clear it and the time-of-day greeting comes back. The right pane is a preview of the line the home screen will show.

## Install

You need `adb` and a TV with ADB debugging on.

```sh
adb connect <tv-ip>:5555         # accept "Allow USB debugging?" on the TV
./launcher/install.sh            # build from source, install, take over HOME
```

This tree builds `tv.vinay.dumbtv` and installs that. The original
[dumbtv home release](https://github.com/humblemuzzu/dumbtv/releases/latest) is a
different app, `tv.dumbtv.home`. If that package is already on the TV, `install.sh`
disables it so HOME stays here, and leaves its settings on the device.
`adb shell pm enable --user 0 tv.dumbtv.home` turns it back on. Installing an APK
by hand works, but HOME only switches over once `install.sh` (or
`scripts/launcher.sh`) has set up the HOME hook.

Undo: `./launcher/install.sh --undo` gives HOME back to Amazon's launcher.

## Build it yourself

Needs JDK 17 and the Android SDK (`platforms;android-25`, `build-tools`). There is
no Gradle: `build.sh` runs aapt2, javac, d8, zipalign and apksigner directly, and
compiles against Android 7.1 so one APK runs on Fire OS 6, 7 and 8.

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-25" "build-tools;34.0.0"
./launcher/build.sh              # launcher/build/dumbtv-home.apk
./launcher/install.sh --build    # build, install, take over HOME
```

On Linux, set `ANDROID_HOME`; `build.sh` finds a JDK 17 in
`/usr/lib/jvm/java-17-openjdk` (Arch: `pacman -S jdk17-openjdk`). It prefers JDK 17
because build-tools 34's `d8` has crashed on classes from newer JDKs.

Your build is signed with a key `build.sh` creates on first run. A later build
has to use that same key. `install.sh` says so when the signatures differ; back
up `home.conf`, then `adb uninstall tv.vinay.dumbtv`.
