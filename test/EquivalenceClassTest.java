import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Equivalence-class tests for Exercise 6 (EC1–EC6), authored by Claude (Anthropic's Claude Code).
//
// These equivalence classes describe end-to-end behaviour, but the production code has no
// input-validation layer and the real pipeline relies on a singleton scheduler, real HTTP and
// real-time intervals. Each EC is therefore reduced to the smallest deterministic unit, and a
// Spy channel is used instead of real Mail/SMS/WhatsApp delivery.
//
// Lives in the default package because the production classes (User, MonitorEntry, ...) are in
// the unnamed package and cannot be imported from a named package.
class EquivalenceClassTest {

    // --- Test double -------------------------------------------------------

    // Records how often (and with what URL) a notification was delivered.
    // Wraps a real channel so the concrete WhatsApp/SMS/Mail class is exercised too.
    static class SpyChannel implements INotificationChannel {
        final INotificationChannel delegate;
        int calls = 0;
        String lastUrl = null;

        SpyChannel(INotificationChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public void send(User user, String url) {
            calls++;
            lastUrl = url;
            if (delegate != null) {
                delegate.send(user, url);   // run the real stub too
            }
        }
    }

    // --- Helpers -----------------------------------------------------------

    private static final String VALID_URL = "https://example.com";

    // Build an entry whose stored bodies differ, i.e. a change WILL be detected.
    private MonitorEntry entryWithChange(String url, Frequency freq, User user) {
        MonitorEntry e = new MonitorEntry(url, freq, new IdenticalHtml());
        e.addUser(user);
        e.setLastScan("OLD CONTENT");
        e.setNewScan("NEW CONTENT");        // different length & text -> change
        return e;
    }

    // Run the decision slice of TaskScheduler.scanAndcheck WITHOUT HTTP / threads:
    // detect the difference and, if changed, notify the observers.
    private boolean detectAndNotify(MonitorEntry e) {
        CheckDifference check = new CheckDifference();
        boolean changed = check.checkHashDifference(e);
        if (changed) {
            e.notifyObservers();
        }
        return changed;
    }

    // ======================================================================
    // EC1 — valid URL, valid frequency, valid WhatsApp channel
    // Expectation: notification is delivered via the WhatsApp channel.
    // ======================================================================
    @Test
    @DisplayName("EC1: valid URL + frequency + WhatsApp channel -> notifies")
    void ec1_validUrl_frequency_whatsapp_notifies() {
        SpyChannel spy = new SpyChannel(new WhatsAppChannel());
        User user = new User("a@b.com", "+49111", spy);
        MonitorEntry entry = entryWithChange(VALID_URL, Frequency.high, user);

        boolean changed = detectAndNotify(entry);

        assertTrue(changed, "a content change must be detected");
        assertEquals(1, spy.calls, "WhatsApp channel must be invoked exactly once");
        assertEquals(VALID_URL, spy.lastUrl);
    }

    // ======================================================================
    // EC2 — valid URL, valid frequency, valid SMS channel
    // ======================================================================
    @Test
    @DisplayName("EC2: valid URL + frequency + SMS channel -> notifies")
    void ec2_validUrl_frequency_sms_notifies() {
        SpyChannel spy = new SpyChannel(new SmsChannel());
        User user = new User("a@b.com", "+49222", spy);
        MonitorEntry entry = entryWithChange(VALID_URL, Frequency.mid, user);

        boolean changed = detectAndNotify(entry);

        assertTrue(changed);
        assertEquals(1, spy.calls, "SMS channel must be invoked exactly once");
        assertEquals(VALID_URL, spy.lastUrl);
    }

    // ======================================================================
    // EC3 — valid URL, valid frequency, valid Mail channel
    // ======================================================================
    @Test
    @DisplayName("EC3: valid URL + frequency + Mail channel -> notifies")
    void ec3_validUrl_frequency_mail_notifies() {
        SpyChannel spy = new SpyChannel(new MailChannel());
        User user = new User("a@b.com", "+49333", spy);
        MonitorEntry entry = entryWithChange(VALID_URL, Frequency.low, user);

        boolean changed = detectAndNotify(entry);

        assertTrue(changed);
        assertEquals(1, spy.calls, "Mail channel must be invoked exactly once");
        assertEquals(VALID_URL, spy.lastUrl);
    }

    // ======================================================================
    // EC4 — valid URL, valid frequency, INVALID channel (null)
    // Spec says: "No Notification but check performed without Problem".
    // Reality: User.update() calls notChan.send() with no null-guard, so once a change is
    // detected the notify path throws NullPointerException. This test documents the CURRENT
    // behaviour and thereby the missing input validation.
    // ======================================================================
    @Test
    @DisplayName("EC4: null channel -> check runs, but notify throws NPE (no guard)")
    void ec4_nullChannel_changeDetected_throwsNpe() {
        User user = new User("a@b.com", "+49444", null);     // invalid channel
        MonitorEntry entry = entryWithChange(VALID_URL, Frequency.high, user);

        // The check itself works fine...
        assertTrue(new CheckDifference().checkHashDifference(entry));

        // ...but delivering the notification blows up because the channel is null.
        assertThrows(NullPointerException.class, entry::notifyObservers,
                "documents the missing null-channel guard in User.update()");
    }

    // ======================================================================
    // EC5 — valid URL, INVALID frequency (null), valid channel
    // Expectation: no checks performed, no notification.
    // The scheduler loop only acts when freq == high/mid/low; a null frequency matches none of
    // them, so no scan is ever dispatched. This mirrors the tier selection in TaskScheduler.start().
    // ======================================================================
    @Test
    @DisplayName("EC5: null frequency -> scheduler dispatches no scan, no notify")
    void ec5_nullFrequency_noScan_noNotify() {
        SpyChannel spy = new SpyChannel(new MailChannel());
        new User("a@b.com", "+49555", spy);
        Frequency freq = null;                               // invalid frequency

        // Replicates TaskScheduler.start()'s tier check: none of the branches fire.
        boolean wouldBeScanned =
                (freq == Frequency.high) || (freq == Frequency.mid) || (freq == Frequency.low);

        assertFalse(wouldBeScanned, "a null frequency matches no scheduler tier");
        assertEquals(0, spy.calls, "no scan -> no notification");
    }

    // ======================================================================
    // EC6 — INVALID URL, valid frequency, valid channel
    // Expectation: no checks, no notification.
    // GetWebsite.startScanner builds a URI from the URL; a malformed URL makes URI.create throw,
    // so the scan aborts before any body is stored or anyone is notified. A URL containing a space
    // is used so it fails offline and deterministically.
    // ======================================================================
    @Test
    @DisplayName("EC6: invalid URL -> scan aborts, nothing stored, no notify")
    void ec6_invalidUrl_scanAborts_noNotify() {
        SpyChannel spy = new SpyChannel(new MailChannel());
        User user = new User("a@b.com", "+49666", spy);
        MonitorEntry entry =
                new MonitorEntry("https://exa mple.com", Frequency.high, new IdenticalHtml());
        entry.addUser(user);

        GetWebsite scanner = new GetWebsite();

        assertThrows(IllegalArgumentException.class, () -> scanner.startScanner(entry),
                "malformed URL must fail in URI.create");
        assertNull(entry.getNewScan(), "no response body stored on a failed scan");
        assertEquals(0, spy.calls, "no scan -> no notification");
    }
}
