# Elonara Home Android AR Proof

This project is a separate Android proof client for Elonara Home's two-layer model.

## What it demonstrates

- `room layer`: world/environment-anchored placeholders using ARCore anchors
- `carry layer`: viewer-attached foreground panel rendered as normal Android UI

It does **not** connect to the PHP backend and does **not** implement real object functionality.

## Build in Android Studio

1. Open Android Studio.
2. Choose **Open** and select this folder: `elonara-home-android`.
3. Let Android Studio sync Gradle and install any missing SDK components.
4. Connect an ARCore-supported Android device with developer mode enabled.
5. Select the `app` run configuration.
6. Click **Run**.

## CLI build

If you have Gradle installed locally:

```bash
gradle assembleDebug
```

To install to a connected device:

```bash
gradle installDebug
```

## Notes

- The app is AR Required.
- The first tracked camera pose is used to place the room-layer anchors in front of the user.
- Browser and Social are rendered in a fixed overlay panel above the AR scene.
