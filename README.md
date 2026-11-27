# Hush

Android app that removes advertising text messages (SMS) automatically, while your normal messaging app
(Google Messages, Samsung Messages …) stays the default app for reading and sending texts.

## How it works

Android lets only the default messaging app delete texts. The permission behind it is the hidden
`WRITE_SMS` app-op, which can be given to Hush once from a computer:

```
adb shell appops set io.github.samaliyan.hush WRITE_SMS allow
```

It stays after restarts and app updates. The app shows step-by-step instructions (Permission to remove).

A text is removed when it has an unsubscribe code (لغو۱۱, لغو 11 …), comes from an advertising line
(numbers starting with 1000, 2000, 3000, 5000; can be switched off), or matches your Block lists.
Login codes, bank texts, texts from contacts and your Allow lists are always kept.
Removed texts are copied into the app for 7 days and can be restored.

## Build

GitHub Actions builds, tests and publishes every push to `main` (see `.github/workflows/build.yml`).
`tests/run_core.sh` runs the filter tests without Android. The full emulator test
(`.github/workflows/ui-test.yml`) is run by hand or by pushing to the branch `ui-test`.
