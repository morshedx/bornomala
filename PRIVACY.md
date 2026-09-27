# Privacy Policy — Bornomala Keyboard

**Last updated:** 27 September 2026

Bornomala ("the app", "the keyboard") is a privacy-first Android keyboard developed by
morshedx (contact: https://pocketware.vercel.app/bornomala/). This policy explains exactly what the app does and
does not do with your information.

## The short version

Bornomala collects **nothing**. Everything you type stays on your device — the keyboard itself
never connects to the internet. The app goes online for only two things, both started by you:
checking for app updates, and the optional backup to your own Google Drive. There is no account
with us, no analytics, no advertising, and no tracking.

## No data collection

The developer does **not** collect, receive, sell, or share any personal or sensitive data,
including:

- The text you type, the words you enter, or any content of any input field.
- Contacts, location, photos, files, identifiers, or usage analytics.

The only data that ever leaves your device is the optional backup you start yourself, which goes
to your own Google Drive — never to the developer.

A keyboard necessarily processes the keystrokes you make so it can show the right characters,
transliterate Bangla, and offer suggestions. **All of this happens locally, on your device,
in memory.** None of it is sent anywhere.

## Network access

The keyboard — typing, transliteration, suggestions, learning — works entirely offline and
never uses the network. The app requests the `INTERNET` permission for two features only:

- **App updates.** When you open *Settings → Updates*, the app downloads a small version file
  and, if you choose to update, the new app package from the developer's release server
  (`app-releases.morshed.im`). These requests contain nothing you typed and no personal
  identifiers; as with any web request, the server can see your IP address.
- **Google Drive backup (optional).** If you sign in with Google and back up, the app uploads
  your settings, learned words and word picks, and clipboard history to **your own** Google
  Drive, in its private app-data area (not shown in your Drive file list). The developer never
  receives it. By default the backup is protected by your Google account — like WhatsApp's
  default backup — so anyone who can sign in to your Google account, and Google itself, could
  read it; note that clipboard history can contain things you copied, such as passwords or
  codes. For stronger protection, turn on **Encrypt with passphrase**: the backup is then
  encrypted on your device (AES-256-GCM, with a key derived from your passphrase) before upload,
  so neither Google nor the developer can read it — and it cannot be restored without the
  passphrase. Automatic daily backup runs only if you turn it on. You can restore or delete the
  backup from the app at any time.

## On-device data

Some features store data on your device, in the app's private storage. It never leaves the
device unless you turn on Google Drive backup (see above), and it is removed when you uninstall
the app:

- **User dictionary / learning** — words you type frequently, and the Bangla word you pick for
  a given spelling, to improve suggestions (can be disabled in Settings). Nothing is learned in
  password fields or in fields that ask for no personalized learning (such as incognito tabs).
- **Clipboard history** — recent copied text, so you can paste it again (can be disabled;
  cleared on uninstall).
- **Emoji usage** — which emoji you use, to surface recents/frequents.
- **Preferences** — your theme, layout, and feature toggles.

You can clear this data at any time by clearing the app's storage or uninstalling the app.

## Permissions used

- **Vibrate** — optional haptic feedback on key press (off by default; enable in Settings).
- **Internet** and **network state** — only for update checks and the optional Drive backup
  (see *Network access*).
- **Install packages** — to install an update you chose to download.

No other permissions are requested.

## Children's privacy

The app collects no data from anyone, including children. It is safe for all ages.

## Third-party services

The app contains **no** advertising networks, analytics, or crash-reporting services. The only
third-party SDK is Google Play Services sign-in, used solely to access your own Google Drive when
you use backup; Google's own privacy policy applies to that sign-in.

## Open-source components

Bornomala bundles open-source language data and assets, used under their respective licenses.
See `THIRD_PARTY_LICENSES.md` in the project. These components run entirely offline and do not
change the guarantees above.

## Changes to this policy

If this policy changes, the updated version will be posted at the same URL with a new "Last
updated" date.

## Contact

Questions about privacy: **https://pocketware.vercel.app/bornomala/**
