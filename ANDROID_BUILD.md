# BOFO Finder - Handoff and Android Build Notes

## Project
BOFO (Boarding House Finder): JavaFX + Maven app with Supabase and Gluon Maps, for students to find boarding houses. Package com.bofo.finder, main class com.bofo.finder.app.Main.

## Changes in v1.1.1
- Soft rounded redesign (purple theme, big rounded cards, floating icon nav): new Icons.java, BhCard.java, Shell.java, plus restyled Ui, HomeView, ExploreView, ProfileView, LoginView, ChatView, BoostView, ListingFormView, ListingsView and bofo.css.
- Fix: floating nav pill stretched over the whole screen. Shell.java now sets nav.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE).
- Chatbot panel (BofoPanel.java) closes when the user presses anywhere outside the chat area or the mascot (scene-level mouse-pressed filter).
- Replaced garbled symbols in BofoPanel with plain text.
- Version bumped to 1.1.1 (versionCode 2).

## Run on desktop
    cd C:\Users\user\bofo-finder
    mvn clean javafx:run

## Turning BOFO into an Android app (GluonFX)
GluonFX builds Android apps only on Linux, so on Windows use WSL2 (Ubuntu). The pom.xml already has <target>android</target> in gluonfx-maven-plugin 1.0.23. There is NO android Maven profile, so do not use -Pandroid.

### One-time setup (inside Ubuntu)
1. Open Ubuntu from PowerShell: wsl -d Ubuntu (install with wsl --install -d Ubuntu if missing). uname -r should contain WSL2.
2. Install tools:

    sudo apt update
    sudo apt install -y build-essential libz-dev zlib1g-dev unzip zip curl git rsync wget

3. Install Gluon's GraalVM (the Linux build is a .tar.gz, not a zip):

    cd ~
    wget https://github.com/gluonhq/graal/releases/download/gluon-22.1.0.1-Final/graalvm-svm-java17-linux-gluon-22.1.0.1-Final.tar.gz
    tar -xzf graalvm-svm-java17-linux-gluon-22.1.0.1-Final.tar.gz
    echo 'export GRAALVM_HOME=$HOME/graalvm-svm-java17-linux-gluon-22.1.0.1-Final' >> ~/.bashrc
    echo 'export JAVA_HOME=$GRAALVM_HOME' >> ~/.bashrc
    echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
    source ~/.bashrc
    java -version

4. Install Maven 3.8.8 (the GluonFX plugin rejects Maven 3.9.x):

    cd ~
    wget https://archive.apache.org/dist/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.tar.gz
    tar -xzf apache-maven-3.8.8-bin.tar.gz
    echo 'export PATH=$HOME/apache-maven-3.8.8/bin:$PATH' >> ~/.bashrc
    source ~/.bashrc
    mvn -version

### Get the project into Linux
Build in the Linux home folder, not under /mnt/c (much slower and can fail):

    rsync -a --exclude target --exclude _backup_before_inside --exclude _backup_full_ui /mnt/c/Users/user/bofo-finder/ ~/bofo-finder/

Cloning a private repo over HTTPS needs a GitHub personal access token (classic, repo scope) as the password, not the account password.

### Build

    cd ~/bofo-finder
    mvn gluonfx:build gluonfx:package 2>&1 | tee build.log

The first run takes 20-40 minutes (downloads the Android SDK/NDK, native compile). Output is under target/gluonfx/aarch64-android/.

    find ~/bofo-finder/target -name "*.apk" -o -name "*.aab"
    cp $(find ~/bofo-finder/target -name "*.apk" | head -1) /mnt/c/Users/user/Downloads/BOFO.apk

Then send the APK to the phone and install it (allow unknown sources). packageType is aab (Play Store format); if only an .aab is produced, adjust the config so an APK is built too.

### Status
Build environment is set up. A finished APK is NOT yet confirmed. Native builds often fail on first runs with reflection or missing-resource errors (Gluon Maps, org.json, Supabase HTTP calls). Fix by adding entries to reflectionList / resourcesList in the pom.xml and rebuilding. Keep build.log to diagnose.

### Mistakes already hit
- Using -Pandroid: that profile does not exist, so the build ran for plain Linux.
- Maven 3.9.12: rejected by GluonFX, needs 3.8.8.
- GraalVM download URL ends in .tar.gz; .zip returns 404.
- Pasting Java code into PowerShell, or <url> placeholders into bash.
- cd in PowerShell does not change the .NET working directory: use full paths with [IO.File].

## Before releasing to real users
- Remove the "Developer: force login" buttons (LoginView, AuthService.devForce).
- Android Google sign-in needs an Android OAuth client in Google Cloud (package name + signing SHA-1).
- Release builds need a signed keystore (releaseConfiguration in pom.xml).
- Never commit GitHub tokens or Supabase secrets.
