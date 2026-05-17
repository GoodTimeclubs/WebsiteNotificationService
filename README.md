# Website Notification Service

A small Java service that periodically scans websites for content changes and
notifies subscribed users through pluggable channels (Mail, SMS, WhatsApp).

Change detection is done by comparing SHA-256 hashes of the page body between
consecutive scans. As soon as a hash changes, every user subscribed to that URL
is notified through the channel they chose.

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
- **Singleton scheduler** that auto-starts on the first registered task and
  multiplexes all monitor entries in a single polling loop.
- **Per-user subscriptions**: a user can subscribe to many URLs, and many users
  can share the same URL/channel/frequency triple.

---

## Architecture

```
              +-----------+        addSubscription        +----------------+
              |   User    | ----------------------------> |  TaskScheduler |
              +-----------+                               +----------------+
                                                                  |
                                                                  | per Frequency
                                                                  v
+----------------------+   hash diff   +------------------+   GET   +-----+
| INotificationChannel | <----- Notify <--- CheckDifference <--- GetWebsite |--> URL
+----------------------+                +------------------+         +-----+
        ^   ^   ^
        |   |   |
   Mail SMS WhatsApp
```

| Class                  | Responsibility                                                |
| ---------------------- | ------------------------------------------------------------- |
| `Main`                 | Entry point — wires up a sample subscription.                 |
| `User`                 | Holds contact data, manages subscriptions.                    |
| `MonitorEntry`         | One watched URL: settings, hashes, subscriber list.           |
| `Frequency`            | Scan-interval tiers (`low`, `mid`, `high`).                   |
| `TaskScheduler`        | Singleton polling loop; owns all monitor entries.             |
| `GetWebsite`           | Performs the HTTP GET and computes the new content hash.      |
| `CheckDifference`      | Compares old vs. new hash and triggers `Notify` on change.    |
| `Notify`               | Fans the change-event out to every subscriber.                |
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

The default `Main` registers a single high-frequency mail subscription:

```java
User test = new User("test@mail.com", "+123456789");
test.addSubscription("http://bengutzeit.de/", Frequency.high, new MailChannel());
```

Expected console output (first scan establishes the baseline, later scans
report whether the page changed):

```
Starting scan for url: http://bengutzeit.de/
Scan completed. Got code 200
First scan for http://bengutzeit.de/ — baseline stored, no notification.
...
Website http://bengutzeit.de/ has not changed!
```

When a change is detected, the chosen channel prints something like:

```
Empfänger: test@mail.com
Änderung an der Website: http://bengutzeit.de/
Benachrichtigung über Mail channel
```

---

## Adding a new notification channel

Implement `INotificationChannel` and pass an instance into `addSubscription`:

```java
public class SlackChannel implements INotificationChannel {
    @Override
    public void send(User user, String url) {
        // call your Slack API here
    }
}

user.addSubscription("https://example.com", Frequency.mid, new SlackChannel());
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
    ├── Notify.java
    ├── INotificationChannel.java
    ├── MailChannel.java
    ├── SmsChannel.java
    └── WhatsAppChannel.java
```

---

## Known limitations / roadmap

- Channel classes are stub implementations (console output only).
- `TaskScheduler.start()` runs on the calling thread and blocks — a real
  deployment should move the loop onto its own thread or a
  `ScheduledExecutorService`.
- `TaskScheduler.getInstance()` is not thread-safe.
- Subscriptions are kept in memory only; no persistence layer yet.
- Only successful (`HTTP 200`) responses are hashed; redirects and error pages
  are currently ignored.

---

## License

Coursework project for SWED — no license specified.
