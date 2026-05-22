# Website Notification Service

A small Java service that periodically scans websites for content changes and
notifies subscribed users through pluggable channels (Mail, SMS, WhatsApp).

Change detection is done by comparing SHA-256 hashes of the page body between
consecutive scans. As soon as a hash changes, the monitor entry notifies its
subscribers via the **observer pattern** — each user is updated and delivers the
message through the channel they chose.

---

## Features

- **Multiple notification channels** — Mail, SMS and WhatsApp out of the box,
  unified behind the `INotificationChannel` interface so new channels can be
  added with a single class.
- **Configurable scan frequency** per subscription:
  - `high` — every minute
  - `mid`  — every hour
  - `low`  — every 5 hours
- **Hash-based change detection** using SHA-256 over the HTTP response body.
- **Observer-based notifications**: a `MonitorEntry` notifies its subscribed
  `User`s on change, and each user delivers via their own channel.
- **Thread-safe singleton scheduler** that auto-starts on the first registered
  task, runs its polling loop on a dedicated background thread, and multiplexes
  all monitor entries in that single loop.
- **Per-user subscriptions**: a user can subscribe to many URLs, and many users
  can share the same URL/frequency pair.

---

## Architecture

```
        +-----------+      addSubscription       +----------------+
        |   User    | -------------------------> |  TaskScheduler |
        +-----------+                            +----------------+
              ^                                          |
              | update() (observer)                      | per Frequency
              |                                          v
       +-------------+    notifyObservers   +--------------+   GET   +-----+
       | MonitorEntry | <----- on change ---| scanAndcheck |<------- | URL |
       +-------------+                      +--------------+         +-----+
              |                                    |
              | send()                             | hash diff
              v                                     v
   +----------------------+              +------------------+
   | INotificationChannel |              |  CheckDifference |
   +----------------------+              +------------------+
        ^   ^   ^
        |   |   |
   Mail SMS WhatsApp
```

| Class                  | Responsibility                                                |
| ---------------------- | ------------------------------------------------------------- |
| `Main`                 | Entry point — wires up sample subscriptions.                  |
| `User`                 | Holds contact data and channel; observer that delivers on `update()`. |
| `MonitorEntry`         | One watched URL: settings, hashes, subscriber list, `notifyObservers()`. |
| `Frequency`            | Scan-interval tiers (`low`, `mid`, `high`).                   |
| `TaskScheduler`        | Thread-safe singleton polling loop; owns all monitor entries. |
| `GetWebsite`           | Performs the HTTP GET and computes the new content hash.      |
| `CheckDifference`      | Compares old vs. new hash; returns whether the page changed.  |
| `INotificationChannel` | Common contract for delivery channels.                        |
| `MailChannel`          | Sends notification via e-mail (stub).                         |
| `SmsChannel`           | Sends notification via SMS (stub).                            |
| `WhatsAppChannel`      | Sends notification via WhatsApp (stub).                       |

> The three channel implementations currently print to `stdout` instead of
> talking to a real provider — they are designed to be swapped for real
> integrations without touching the rest of the codebase.

---

## Requirements

- **JDK 21+** — the code uses Java 21 features such as instance `main` methods
  (`void main()` in `Main.java`) and `java.net.http.HttpClient`.
- No external build tool is required; the project ships as a plain IntelliJ
  module (`.iml`).

---

## Getting started

### Run from IntelliJ

1. Open the project folder in IntelliJ IDEA.
2. Right-click `src/Main.java` and choose **Run 'Main'**.

### Run from the command line

```bash
# Compile
javac -d out src/*.java

# Run
java -cp out Main
```

### Sample run

The default `Main` registers a few high-frequency subscriptions, each user
carrying their own channel:

```java
User test1 = new User("test@mail.com",  "+123456789",  new MailChannel());
TaskScheduler scheduler = TaskScheduler.getInstance();
scheduler.addSubscription("http://bengutzeit.de", Frequency.high, test1);
```

Expected console output (first scan establishes the baseline, later scans
report whether the page changed):

```
Starting scan for url: http://bengutzeit.de
Scan completed. Got code 200
First scan for http://bengutzeit.de — baseline stored, no notification.
...
Website http://bengutzeit.de has not changed!
```

When a change is detected, the chosen channel prints something like:

```
Empfänger: test@mail.com
Änderung an der Website: http://bengutzeit.de/
Benachrichtigung über Mail channel
```

---

## Adding a new notification channel

Implement `INotificationChannel` and pass an instance into the `User`
constructor, then subscribe that user through the scheduler:

```java
public class SlackChannel implements INotificationChannel {
    @Override
    public void send(User user, String url) {
        // call your Slack API here
    }
}

User user = new User("me@example.com", "+49123", new SlackChannel());
TaskScheduler.getInstance().addSubscription("https://example.com", Frequency.mid, user);
```

---

## Project layout

```
WebsiteNotificationService/
├── README.md
├── WebsiteNotificationService.iml
├── out/                            # compiled classes (generated)
└── src/
    ├── Main.java
    ├── User.java
    ├── MonitorEntry.java
    ├── Frequency.java
    ├── TaskScheduler.java
    ├── GetWebsite.java
    ├── CheckDifference.java
    ├── INotificationChannel.java
    ├── MailChannel.java
    ├── SmsChannel.java
    └── WhatsAppChannel.java
```

---

## Known limitations / roadmap

- Channel classes are stub implementations (console output only).
- The scan loop runs on a single background thread; for many entries a
  `ScheduledExecutorService` (one task per entry) would scale better.
- Subscriptions are kept in memory only; no persistence layer yet.
- Only successful (`HTTP 200`) responses are hashed; redirects and error pages
  are currently ignored.
- There is no built-in shutdown hook; the loop is stopped by setting
  `scheduler.stop = true`.

---

## Changelog

### [Unreleased] — 2026-05-23

#### Added
- Observer-based notification flow: `MonitorEntry.notifyObservers()` →
  `User.update()` → the user's `INotificationChannel`.
- Background execution: the scheduler's polling loop now runs on its own thread,
  so `main` keeps control after registering subscriptions.
- Doc comments across the scheduler, observer and change-detection classes.

#### Changed
- `TaskScheduler.getInstance()` is now thread-safe (`synchronized`); shared state
  is guarded by a dedicated lock object and `volatile` flags.
- Each `User` now carries its own notification channel instead of attaching it
  to the `MonitorEntry`.

#### Fixed
- `ArrayIndexOutOfBoundsException` when subscribing the first task, caused by
  Java's left-to-right evaluation of `registeredTasks[addTask(...)]`.
- Off-by-one out-of-bounds error while shifting arrays in `removeTask` and
  `MonitorEntry.removeUser`.
- `removeSubscription` now removes the last subscriber correctly.
- Double / dead notification path: notifications are no longer sent twice.

#### Removed
- The `Notify` class — superseded by the observer pattern.

---

## License

Coursework project for SWED — no license specified.
