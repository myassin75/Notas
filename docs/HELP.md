# Notas Help Guide

## Getting started

### Notes, Tasks, and Events

Notas classifies your entries into three clear categories:
- **Notes**: Free-form writing, checklists, and rich markdown text with no scheduled dates.
- **Tasks**: Actionable to-dos with a scheduled date or reminder time, but without an end date.
- **Events**: Time-bounded entries that have an explicit end date or start-to-end time range.

The classification is automatic: add a start date or reminder to turn a note into a Task; add an end date to turn it into an Event.

### Creating and organizing entries

- Tap the **+ Add** button on the Home screen to create a new note or checklist.
- Set an optional start date or reminder to schedule it as a **Task**.
- Set an optional end date to schedule it as an **Event**.
- Use launcher shortcuts, the persistent Quick capture notification, or Home screen widgets for rapid capture from anywhere on your phone.

### Marking something done

Marking a note or task done moves it out of your active workspace into the bottom **Done** section.
- For one-time tasks and reminders, marking done clears the reminder notification.
- For recurring schedules, marking done automatically rolls the reminder forward to the next scheduled occurrence. If no future occurrence exists, the item completes and enters Done.
- You can toggle task completion directly using the circular checkboxes on the Home screen, Calendar Agenda, or Day Timeline.

---

## Calendar and Views

### Calendar views: Month, Week, Day, and Year

Notas includes a complete, dedicated Calendar suite accessible from the bottom navigation bar:
- **Month view**: A full-month calendar grid. Selected dates show their agenda below the grid. Tap the **Today** capsule button to instantly return to today. Use `<` and `>` to navigate between months.
- **Week view**: A focused 7-day horizontal horizon highlighting the active day and displaying all weekly tasks and events in sequential order.
- **Day view**: A detailed 24-hour vertical timeline showing scheduled entries. A live purple indicator line shows the exact current time in real-time.
- **Year view**: A 12-month bird's-eye overview with weekend highlights and a quick return bar to jump straight back to Today.

### Calendar tasks and checkboxes

- Any entry with a scheduled date or reminder automatically appears in the Calendar on that day.
- Completed tasks display a checked badge; tap the circular checkbox directly to toggle completion without opening item details.
- Tap the **+ New event** button from Week or Day view to quickly schedule an entry for the selected date.

---

## Notes and checklists

### What you can set on a note or list

Each note and list supports: an icon with optional custom color, a shoulder tag, reminder schedule, notification action, importance level, visibility setting, cover picture, and file attachments. Pictures appear as card headers and in notification previews. Attachments remain securely linked with their parent note.

### Note formatting

Notes support full Markdown writing: headings, bold, italic, underline, strikethrough, bulleted and numbered lists, blockquotes, code blocks, hyperlinks, and horizontal dividers. Reminder notifications use a clean plain-text version so alerts stay readable.

### Checklists

Checklists contain checkable items that can be reordered and structured with one level of nesting:
- Indent an item under a parent item to group related subtasks.
- Checking a parent item automatically checks all of its nested children.
- If every child item is checked off, the parent completes itself automatically.

---

## Organization and Design

### Card design and colors

Notas uses a unified design system crafted for visual clarity and ease on the eyes:
- **Unified neutral gray cards**: All cards across Home, Calendar, and Search use a unified neutral gray background by default.
- **Shoulders**: The left vertical indicator stripe retains its unique user-chosen shoulder color, giving you instant visual recognition.
- **Icon customization**: Icons use neutral gray by default, but if you choose a custom icon color, your personalized color is proudly displayed.
- **Card shape**: Cards feature smooth 28dp rounded corners for a modern, tactile feel.

### Shoulders, pins, archive, and trash

- **Shoulders** group related notes, tasks, and events with quick filter chips along the top of the screen.
- **Star** marks favorites so they surface easily.
- **Pin** keeps essential items at the very top of your Home list.
- **Archive** removes items from the active list without deleting them.
- **Trash** holds items you plan to remove before final deletion.

### Pinning

Pinned items collect in a dedicated **Pinned** section at the top of the Home screen:
- Pinning is an ordering priority that holds items at the top regardless of the active sort or grouping.
- Pin or unpin from the note bottom bar, by swiping right on a card, or via multi-select actions.
- Completed pinned items move to the bottom Done section, and restore their pinned status if unmarked.

### Search

Instant full-text search powered by SQLite FTS4:
- Checks titles, body text, tags, checklist items, and notification action details.
- Use quick category filter chips (**All**, **Notes**, **Tasks**, **Events**) to narrow down results instantly.
- Searches both active notes and archived or trashed items.

### Finding archived or trashed notes

Archived and trashed items are safely tucked away from your daily view:
- Open **Settings → History** to review, unarchive, restore, or empty trash.
- Full-text search also finds matches in archive and trash.

---

## Reminders and Alerts

### How reminders work

Add a reminder date and time to any note or list. When the time arrives, Notas delivers a notification. Tapping opens the item directly.
- The reminder sheet offers quick natural presets: **Soon**, **Late tonight**, **Tomorrow morning**, or specific custom dates and times.
- Settings let you switch preset styles between **Timing** (named times) and **Duration** (30 mins, 6 hours, 24 hours).
- Reminder notifications feature the title, body preview, snooze actions, a **Done** button, and your custom notification action.

### Importance: Low, Normal, High, and Critical

Importance controls how loudly and persistently a reminder alerts:
- **Low**: Silent notification in your shade with no sound or vibration.
- **Normal**: Standard sound and vibration alert.
- **High**: Heads-up popup alert treated with high priority.
- **Critical**: Re-alerts every 15 minutes until snoozed or marked done. Rings like a full alarm with your alarm volume and tone even when the phone is on silent or vibrate. Overrides Do Not Disturb when alarms are permitted.

### Recurring reminders

Recurring reminders can repeat daily, weekly, monthly, or yearly:
- Set an optional end date or limit by number of occurrences.
- Marking a recurring reminder done advances it to the next scheduled occurrence automatically.

### Restore notifications

**Restore notifications** prevents accidental dismissal. When enabled, a dismissed reminder notification reappears immediately if the note or task is still not marked done.

### Reminder summary

The **reminder summary notification** provides a quiet, persistent overview of overdue and upcoming reminders in your notification shade.

### Notification permission and reliability

Android requires notification and exact alarm permissions for timely delivery. Ensure background activity and alarm permissions are granted so reminders alert reliably without battery optimization delays.

### Snooze

Snooze postpones a reminder directly from the notification or within the app:
- **Timing mode**: Soon, Later today, Tomorrow morning, Next week.
- **Duration mode**: Snooze by minutes, hours, or days.
- **Pick a specific time**: Custom date and time picker is always available.

### Troubleshooting reminders

- **Not getting reminders**: Confirm Android notification and alarm permissions are allowed for Notas, and verify the note has an active reminder set.
- **Delayed reminders**: Disable battery optimization restrictions for Notas in Android System Settings.
- **Dismissed alerts missing**: Turn on **Restore notifications** in Settings to keep uncompleted reminders sticky.

---

## Notification actions

### How they work

Notification actions are one-tap buttons attached directly to reminder notifications:
- **Call**, **Message**, or **Email** a contact directly.
- **Get directions** to an address in your navigation app.
- **Open a link**, app, or system shortcut.
- **Copy text** to clipboard or **Share** content.
Notifications always include **Snooze** and **Mark done** alongside your chosen action.

### Troubleshooting notification actions

- Confirm action data is formatted correctly (valid phone number, email address, URL, or address).
- Ensure the required application is installed on your device (dialer, browser, maps, email client).
- Secret visibility protects private notes by suppressing sensitive data from notifications.

---

## Widgets and quick capture

### Available widgets

- **Agenda widget**: Displays upcoming tasks and events for the next 7 days.
- **Selected notes widget**: Displays notes from all, starred, pinned, or specific shoulder tags.
- **Quick capture widget**: One-tap buttons to start a new note or checklist directly from your home screen.

### Quick capture notification

The **quick capture notification** is a persistent shortcut in your notification shade for creating a new note without leaving your current app.

### Troubleshooting widgets

- Secret notes are hidden from widgets for privacy.
- Private notes hide their body preview text.
- Widgets only display active notes (archived and trashed items do not appear).

---

## Cloud Sync, Backup, and Restore

### Setting up Google Drive sync

The GitHub build of Notas supports seamless cloud synchronization via Google Drive:
- Connect your Google account in Settings to sync your notes, tasks, events, and shoulders across devices.
- Uses Hybrid Logical Clocks (HLC) and end-to-end cryptographic verification to keep data consistent.

### Multi-device sync and conflict resolution

Notas uses automated field-level conflict resolution. If concurrent edits happen on different devices while offline, changes merge deterministically without overwriting unrelated fields.

### Importing from Google Tasks or Tasks.org

Bring your tasks into Notas with read-only safety:
- **Connect Google**: Sign in to import tasks directly.
- **Manual import**: Pick a `Tasks.json` from Google Takeout or a Tasks.org backup file. No account sign-in required.

### What a backup includes

Backups include all notes, tasks, events, checklists, shoulders, tags, reminder schedules, visibility, importance, icons, actions, and app preferences.
- Turn on **Include media in backup** to embed pictures and attachments inside the backup archive.

### Import vs. restore

- **Import backup**: Adds backup items into your current collection. Use this when merging data.
- **Restore backup**: Replaces all current app data with the contents of the backup. Use this when moving to a new device.

---

## Privacy and security

### Where notes are stored

Notas is an offline-first app. Your data is stored locally on your device in a structured SQLite database protected by Android's application sandbox and cryptographic key partitioning.

### Visibility: Normal, Private, and Secret

Visibility controls how notes appear in notifications and widgets:
- **Normal**: Full title and content visible.
- **Private**: Title visible, content preview hidden in notifications and widgets.
- **Secret**: Completely hidden from notifications, widgets, and lock screen previews.

### App lock

App lock secures Notas behind your device biometric authentication (fingerprint, face unlock) or device lock PIN/pattern:
- Prompts for authentication whenever opening or returning to Notas.
- Protects your personal thoughts and tasks from unauthorized access.

---

## Customization and Appearance

### Appearance

- **Appearance mode**: System default, Light, or Dark.
- **Pure black OLED theme**: In Dark mode, enable true black surfaces for battery savings and high contrast on OLED screens.
- **Accent colors**: Curated palettes including Amethyst, Maroon, Royal Blue, Forest Green, Dark Brown, and Ocean Teal.
- **Adaptive styling**: Seamless light and dark mode transitions with accessible contrast ratios.

### Defaults

Configure starting defaults in Settings so new entries match your workflow:
- Default visibility (Normal, Private, Secret).
- Default importance (Low, Normal, High, Critical).
- Default reminder time of day.
- Start of week (Monday, Sunday, Saturday).

### Swipe actions

Configure swipe gestures on Home screen cards:
- **Reveal mode** (default): Swipe left or right to reveal action buttons (Pin, Star, Duplicate, Done, Archive, Trash).
- **Direct mode**: Swipe immediately triggers the first action.
- Configurable haptic feedback.
