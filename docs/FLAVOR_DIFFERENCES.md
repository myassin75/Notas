# Remember Flavor Differences

Remember is built using three product flavors under the `distribution` dimension: **`github`**, **`playstore`**, and **`offline`**.

| Feature / Characteristic | GitHub Flavor (`github`) | Play Store Flavor (`playstore`) | Offline Flavor (`offline`) |
| :--- | :--- | :--- | :--- |
| **Application ID** | `three.haddad.smartnotas.gh` | `three.haddad.smartnotas` | `three.haddad.smartnotas.offline` |
| **Side-by-Side Installation** | Can run alongside Play Store & Offline. | Can run alongside all other flavors | Can run alongside all other flavors |
| **Internet permission** | Used for GitHub update checks, APK download, changelog fetch, and Google Tasks connect. | Used for Play in-app updates, changelog fetch, and Google Tasks connect. | No internet permission |
| **Update check source** | GitHub Release API for `bikram-agarwal/Remember` | Google Play Core In-App Updates | You share it to ObtainX, and track upates there |
| **Update Action** | Downloads the release APK and launches the system package installer | Starts the Play Core in-app update flow. | Track updates via ObtainX, or install a newer offline APK from GitHub Releases manually. |
| **Tasks import** | Connect to Google account, plus Takeout / Tasks.org local file import. | Same as GitHub | Google Takeout / Tasks.org local file import only (no Google account connect). |
| **Manifest Permissions** | Requests `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` | Requests `SCHEDULE_EXACT_ALARM` | Same as GitHub |
| **Save Update APK to Downloads** | Yes | No | No |
| **In-App Rating / Review** | Does not prompt for Play ratings | Uses the Google Play In-App Review API with automated prompt | Does not prompt for Play ratings |
| **Cross-Promo Cards** | Shows FilePipe and ObtainX cards. Tapping opens each app's webpage. | Shows FilePipe only. Tapping opens the FilePipe Play Store listing. | Same as GitHub |

---

## Backup Portability FAQ

### Are backups portable between the GitHub, Play Store, and Offline flavors?

**Yes, the backup files are fully portable between all flavors.**

All flavors share the same data domain representation, Room database schema, and JSON serialization. Backup import (`org.json`) only reads the keys it knows, so flavor-specific preference fields are safely ignored by a flavor that does not use them.

> [!WARNING]
> **Package-scoped grants do not transfer across application IDs.**
>
> The GitHub flavor uses `three.haddad.smartnotas.gh`. The Play Store flavor uses `three.haddad.smartnotas`. The Offline flavor uses `three.haddad.smartnotas.offline`. Switching to Offline (or from Offline to another package ID) means restoring a backup into a different app install; re-grant any permissions the new package needs after restore.
