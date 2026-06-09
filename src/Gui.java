import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Swing front-end for the Website Notification Service.
//
// Responsibilities (kept in this file so the backend barely changes):
//   * manage Users (create / remove, pick their notification channel),
//   * create monitoring entries (url + frequency + change-detection strategy) for those users,
//   * show every registered entry as its own rounded card in a scrollable overview,
//   * mirror the live System.out / System.err stream into a console panel on the right.
//
// The entry overview reads the live task list straight from the scheduler via
// TaskScheduler.getRegisteredTasks() (a thread-safe snapshot), so the GUI never keeps a second,
// drift-prone copy of the entries. The GUI does keep its own List<User>, because a user with no
// subscription does not exist anywhere in the backend yet still needs to be listed and selectable.
//
// To make the scheduler's "same task" grouping line up with the combo selections, every
// subscription is handed a single shared instance per strategy (existingTask compares strategies
// by object identity), see HTML / TEXT / SIZE.
public class Gui extends JFrame {

    // ----- palette: white surfaces, restrained colourful accents -----------------------------
    private static final Color APP_BG      = new Color(0xF3F4FB); // window canvas (very light lavender-grey)
    private static final Color SURFACE      = Color.WHITE;         // section + card background
    private static final Color BORDER_SOFT  = new Color(0xE6E8F0); // hairline borders
    private static final Color TEXT_DARK    = new Color(0x1F2430);
    private static final Color TEXT_MUTED   = new Color(0x7A8194);
    private static final Color ACCENT       = new Color(0x4F46E5); // primary indigo
    private static final Color ACCENT_SOFT  = new Color(0xEEF0FF);
    private static final Color DANGER       = new Color(0xE11D48); // rose, for destructive actions

    private static final Color CONSOLE_BG   = new Color(0x14161F); // dark slate console surface
    private static final Color CONSOLE_OUT  = new Color(0xDDE1EC);
    private static final Color CONSOLE_ERR  = new Color(0xFF7A85);

    // ----- fonts -------------------------------------------------------------------------------
    private static final String UI_FAMILY = "Segoe UI";
    private static final Font FONT_H1     = new Font(UI_FAMILY, Font.BOLD, 19);
    private static final Font FONT_H2     = new Font(UI_FAMILY, Font.BOLD, 15);
    private static final Font FONT_BASE   = new Font(UI_FAMILY, Font.PLAIN, 13);
    private static final Font FONT_BOLD   = new Font(UI_FAMILY, Font.BOLD, 13);
    private static final Font FONT_SMALL  = new Font(UI_FAMILY, Font.PLAIN, 12);
    private static final Font FONT_PILL   = new Font(UI_FAMILY, Font.BOLD, 11);
    private static final Font FONT_MONO   = new Font("Consolas", Font.PLAIN, 13);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // ----- shared backend handles --------------------------------------------------------------
    private final TaskScheduler scheduler = TaskScheduler.getInstance();

    // One instance per strategy: reused for every subscription so TaskScheduler.existingTask
    // (which compares strategies by reference) groups identical url+freq+strategy tasks together.
    private final IContentType HTML = new IdenticalHtml();
    private final IContentType TEXT = new IdenticalText();
    private final IContentType SIZE = new IdenticalSize();

    // The only GUI-owned model: the set of known users (entries are read live from the scheduler).
    private final List<User> users = new ArrayList<>();

    // Per-card "last checked" updaters, re-collected on every refresh and run by a Swing timer so
    // the timestamps stay live while background scans run.
    private final List<Runnable> timestampUpdaters = new ArrayList<>();

    // ----- live UI references rebuilt on refresh -----------------------------------------------
    private JPanel usersListPanel;     // vertical stack of user rows
    private JPanel entriesListPanel;   // vertical stack of entry cards
    private JTextPane consolePane;     // mirrored System.out / System.err
    private JLabel usersTitle;
    private JLabel entriesTitle;
    private JComboBox<User> entryUserCombo;

    // ----- new-user form fields ----------------------------------------------------------------
    private JTextField userMailField;
    private JTextField userPhoneField;
    private JComboBox<String> userChannelCombo;

    // ----- new-entry form fields ---------------------------------------------------------------
    private JTextField entryUrlField;
    private JComboBox<Frequency> entryFreqCombo;
    private JComboBox<IContentType> entryStrategyCombo;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel; the custom painting carries the design anyway
        }
        SwingUtilities.invokeLater(() -> new Gui().setVisible(true));
    }

    public Gui() {
        super("Website Notification Service");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 640));
        setSize(1240, 760);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(APP_BG);
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);

        // Redirect console streams once the text pane exists, then greet the user.
        installConsoleRedirect();

        // Stop the scheduler's polling loop cleanly when the window closes.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                scheduler.stop = true;
            }
        });

        seedDemoData();
        refreshUsers();
        refreshEntries();

        // tick once a second so each card's "last checked" / "next scan" countdown stays live
        new javax.swing.Timer(1000, e -> {
            for (Runnable r : timestampUpdaters) r.run();
        }).start();

        System.out.println("Website Notification Service ready.");
        System.out.println("Add users on the left and create website entries in the middle.");
    }

    // ============================================================================================
    //  Layout
    // ============================================================================================

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SOFT),
                new EmptyBorderInsets(16, 22, 16, 22)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        // accent "logo" mark with a simple bell glyph
        JComponent mark = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(Color.WHITE);
                int cx = getWidth() / 2;
                g2.fillRoundRect(cx - 7, 11, 14, 12, 8, 8);
                g2.fillRect(cx - 9, 19, 18, 4);
                g2.fillOval(cx - 2, 8, 4, 4);
                g2.fillOval(cx - 3, 24, 6, 4);
                g2.dispose();
            }
        };
        mark.setPreferredSize(new Dimension(34, 34));
        left.add(mark);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Website Notification Service");
        title.setFont(FONT_H1);
        title.setForeground(TEXT_DARK);
        JLabel subtitle = new JLabel("Manage users and monitor websites for changes");
        subtitle.setFont(FONT_SMALL);
        subtitle.setForeground(TEXT_MUTED);
        titles.add(title);
        titles.add(subtitle);
        left.add(titles);

        header.add(left, BorderLayout.WEST);
        return header;
    }

    private JComponent buildBody() {
        // Outer split: [users | entries]  |  [console]
        JSplitPane workSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildUsersSection(), buildEntriesSection());
        styleSplit(workSplit);
        workSplit.setResizeWeight(0.32);
        workSplit.setDividerLocation(330);

        JSplitPane outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workSplit, buildConsoleSection());
        styleSplit(outerSplit);
        outerSplit.setResizeWeight(0.68);
        outerSplit.setDividerLocation(860);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(APP_BG);
        wrap.setBorder(new EmptyBorderInsets(16));
        wrap.add(outerSplit, BorderLayout.CENTER);
        return wrap;
    }

    private void styleSplit(JSplitPane split) {
        split.setOpaque(false);
        split.setBackground(APP_BG);
        split.setDividerSize(16);
        split.setContinuousLayout(true);
        // make the divider blend into the canvas
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(APP_BG);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        split.setBorder(null);
    }

    // ---- left column: user management ---------------------------------------------------------

    private JComponent buildUsersSection() {
        RoundedPanel section = new RoundedPanel(20, SURFACE, BORDER_SOFT);
        section.setLayout(new BorderLayout());
        section.setBorder(new EmptyBorderInsets(18));

        usersTitle = sectionTitle("Users");
        section.add(usersTitle, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.setBorder(new EmptyBorderInsets(14, 0, 0, 0));

        center.add(buildUserForm(), BorderLayout.NORTH);

        usersListPanel = new JPanel();
        usersListPanel.setOpaque(false);
        usersListPanel.setLayout(new BoxLayout(usersListPanel, BoxLayout.Y_AXIS));

        center.add(quietScroll(usersListPanel), BorderLayout.CENTER);

        section.add(center, BorderLayout.CENTER);
        return section;
    }

    private JComponent buildUserForm() {
        RoundedPanel form = new RoundedPanel(14, ACCENT_SOFT, null);
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorderInsets(14));
        GridBagConstraints c = formConstraints();

        userMailField = new JTextField();
        userPhoneField = new JTextField();
        userChannelCombo = new JComboBox<>(new String[]{"Mail", "SMS", "WhatsApp"});
        styleCombo(userChannelCombo);

        c.gridy = 0; form.add(fieldLabel("Email"), c);
        c.gridy = 1; form.add(inputShell(userMailField), c);
        c.gridy = 2; form.add(fieldLabel("Phone"), c);
        c.gridy = 3; form.add(inputShell(userPhoneField), c);
        c.gridy = 4; form.add(fieldLabel("Channel"), c);
        c.gridy = 5; form.add(inputShell(userChannelCombo), c);

        RoundedButton add = new RoundedButton("+  Add user", ACCENT, Color.WHITE);
        add.addActionListener(e -> addUser());
        userPhoneField.addActionListener(e -> addUser());
        c.gridy = 6; c.insets = new Insets(12, 0, 0, 0);
        form.add(add, c);

        return form;
    }

    // ---- middle column: entry creation + overview ---------------------------------------------

    private JComponent buildEntriesSection() {
        RoundedPanel section = new RoundedPanel(20, SURFACE, BORDER_SOFT);
        section.setLayout(new BorderLayout());
        section.setBorder(new EmptyBorderInsets(18));

        entriesTitle = sectionTitle("Website Entries");
        section.add(entriesTitle, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.setBorder(new EmptyBorderInsets(14, 0, 0, 0));

        center.add(buildEntryForm(), BorderLayout.NORTH);

        entriesListPanel = new JPanel();
        entriesListPanel.setOpaque(false);
        entriesListPanel.setLayout(new BoxLayout(entriesListPanel, BoxLayout.Y_AXIS));

        center.add(quietScroll(entriesListPanel), BorderLayout.CENTER);

        section.add(center, BorderLayout.CENTER);
        return section;
    }

    private JComponent buildEntryForm() {
        RoundedPanel form = new RoundedPanel(14, ACCENT_SOFT, null);
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorderInsets(14));
        GridBagConstraints c = formConstraints();

        entryUserCombo = new JComboBox<>();
        entryUserCombo.setRenderer(new UserRenderer());
        styleCombo(entryUserCombo);

        entryUrlField = new JTextField();

        entryFreqCombo = new JComboBox<>(new Frequency[]{Frequency.high, Frequency.mid, Frequency.low});
        entryFreqCombo.setRenderer(new LabelRenderer(v -> freqLabel((Frequency) v)));
        styleCombo(entryFreqCombo);

        entryStrategyCombo = new JComboBox<>(new IContentType[]{HTML, TEXT, SIZE});
        entryStrategyCombo.setRenderer(new LabelRenderer(v -> strategyLabel((IContentType) v)));
        styleCombo(entryStrategyCombo);

        c.gridy = 0; form.add(fieldLabel("User"), c);
        c.gridy = 1; form.add(inputShell(entryUserCombo), c);
        c.gridy = 2; form.add(fieldLabel("Website URL"), c);
        c.gridy = 3; form.add(inputShell(entryUrlField), c);

        // frequency + strategy side by side
        JPanel dual = new JPanel(new GridLayout(1, 2, 12, 0));
        dual.setOpaque(false);
        dual.add(labeled("Interval", inputShell(entryFreqCombo)));
        dual.add(labeled("Strategy", inputShell(entryStrategyCombo)));
        c.gridy = 4; c.insets = new Insets(2, 0, 0, 0);
        form.add(dual, c);

        RoundedButton add = new RoundedButton("+  Add entry", ACCENT, Color.WHITE);
        add.addActionListener(e -> addEntry());
        entryUrlField.addActionListener(e -> addEntry());
        c.gridy = 5; c.insets = new Insets(12, 0, 0, 0);
        form.add(add, c);

        return form;
    }

    // ---- right column: console ----------------------------------------------------------------

    private JComponent buildConsoleSection() {
        RoundedPanel section = new RoundedPanel(20, CONSOLE_BG, null);
        section.setLayout(new BorderLayout());

        // header bar with title + clear button
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorderInsets(14, 18, 12, 14));

        JPanel headLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headLeft.setOpaque(false);
        JComponent dot = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x3DDC84));
                g2.fillOval(0, getHeight() / 2 - 4, 8, 8);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(10, 16));
        headLeft.add(dot);
        JLabel ctitle = new JLabel("Console");
        ctitle.setFont(FONT_H2);
        ctitle.setForeground(Color.WHITE);
        headLeft.add(ctitle);
        head.add(headLeft, BorderLayout.WEST);

        RoundedButton clear = new RoundedButton("Clear", new Color(0x2A2E3C), CONSOLE_OUT);
        clear.setFont(FONT_SMALL);
        clear.addActionListener(e -> consolePane.setText(""));
        JPanel headRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        headRight.setOpaque(false);
        headRight.add(clear);
        head.add(headRight, BorderLayout.EAST);

        section.add(head, BorderLayout.NORTH);

        consolePane = new JTextPane();
        consolePane.setEditable(false);
        consolePane.setBackground(CONSOLE_BG);
        consolePane.setForeground(CONSOLE_OUT);
        consolePane.setFont(FONT_MONO);
        consolePane.setBorder(new EmptyBorderInsets(4, 18, 16, 14));
        consolePane.setCaretColor(CONSOLE_OUT);

        JScrollPane scroll = new JScrollPane(consolePane);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(CONSOLE_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        section.add(scroll, BorderLayout.CENTER);

        return section;
    }

    // ============================================================================================
    //  Actions
    // ============================================================================================

    private void addUser() {
        String mail = userMailField.getText().trim();
        String phone = userPhoneField.getText().trim();
        String channel = (String) userChannelCombo.getSelectedItem();

        if (mail.isEmpty() || !mail.contains("@")) {
            warn("Please enter a valid email address.");
            return;
        }
        if (phone.isEmpty()) {
            warn("Please enter a phone number.");
            return;
        }
        for (User u : users) {
            if (u.getMailAddress().equalsIgnoreCase(mail)) {
                warn("A user with this email already exists.");
                return;
            }
        }

        INotificationChannel chan = switch (channel) {
            case "SMS" -> new SmsChannel();
            case "WhatsApp" -> new WhatsAppChannel();
            default -> new MailChannel();
        };

        users.add(new User(mail, phone, chan));
        userMailField.setText("");
        userPhoneField.setText("");
        userChannelCombo.setSelectedIndex(0);

        refreshUsers();
        System.out.println("User added: " + mail + " (" + channel + ")");
    }

    private void removeUser(User user) {
        // Drop this user from every entry it is subscribed to; the scheduler removes a task once
        // its last subscriber is gone.
        for (MonitorEntry m : scheduler.getRegisteredTasks()) {
            if (containsUser(m.subscribedUsers, user)) {
                try {
                    scheduler.removeSubscription(m.getUrl(), m.getFreq(), user, m.getCheckType());
                } catch (Exception ex) {
                    System.err.println("Could not remove subscription: " + ex.getMessage());
                }
            }
        }
        users.remove(user);
        refreshUsers();
        refreshEntries();
        System.out.println("User removed: " + user.getMailAddress());
    }

    private void addEntry() {
        User user = (User) entryUserCombo.getSelectedItem();
        if (user == null) {
            warn("Please create a user on the left and select it here first.");
            return;
        }
        String url = entryUrlField.getText().trim();
        if (url.isEmpty()) {
            warn("Please enter a website URL.");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        Frequency freq = (Frequency) entryFreqCombo.getSelectedItem();
        IContentType strategy = (IContentType) entryStrategyCombo.getSelectedItem();

        MonitorEntry existing = findLiveEntry(url, freq, strategy);
        if (existing != null && containsUser(existing.subscribedUsers, user)) {
            warn("This user is already subscribed to this exact entry.");
            return;
        }

        // Register with the scheduler (creates or reuses the matching MonitorEntry).
        scheduler.addSubscription(url, freq, user, strategy);

        entryUrlField.setText("");
        refreshEntries();
        System.out.println("Entry created: " + url
                + "  [" + freqLabel(freq) + ", " + strategyLabel(strategy) + "]"
                + "  for " + user.getMailAddress());
    }

    // Remove a single subscriber from one monitored entry (the scheduler drops the whole task
    // once its last subscriber is gone).
    private void unsubscribe(MonitorEntry entry, User user) {
        try {
            scheduler.removeSubscription(entry.getUrl(), entry.getFreq(), user, entry.getCheckType());
        } catch (Exception ex) {
            System.err.println("Could not remove subscription: " + ex.getMessage());
        }
        refreshEntries();
        System.out.println("Subscription removed: " + user.getMailAddress() + " -> " + entry.getUrl());
    }

    private void removeEntry(MonitorEntry entry) {
        for (User u : entry.subscribedUsers.clone()) {   // iterate a snapshot; the live array shrinks
            try {
                scheduler.removeSubscription(entry.getUrl(), entry.getFreq(), u, entry.getCheckType());
            } catch (Exception ex) {
                System.err.println("Could not remove subscription: " + ex.getMessage());
            }
        }
        refreshEntries();
        System.out.println("Entry removed: " + entry.getUrl());
    }

    // ============================================================================================
    //  Refresh / rendering of the dynamic lists
    // ============================================================================================

    private void refreshUsers() {
        usersListPanel.removeAll();
        if (users.isEmpty()) {
            usersListPanel.add(emptyState("No users yet."));
        } else {
            for (User u : users) {
                usersListPanel.add(buildUserRow(u));
                usersListPanel.add(Box.createVerticalStrut(10));
            }
        }
        usersListPanel.revalidate();
        usersListPanel.repaint();

        usersTitle.setText("Users  (" + users.size() + ")");
        refreshUserCombo();
    }

    private void refreshUserCombo() {
        User selected = (User) entryUserCombo.getSelectedItem();
        entryUserCombo.removeAllItems();
        for (User u : users) {
            entryUserCombo.addItem(u);
        }
        if (selected != null && users.contains(selected)) {
            entryUserCombo.setSelectedItem(selected);
        }
    }

    private void refreshEntries() {
        timestampUpdaters.clear();
        entriesListPanel.removeAll();

        MonitorEntry[] tasks = scheduler.getRegisteredTasks();
        if (tasks.length == 0) {
            entriesListPanel.add(emptyState("No entries yet. Create your first website entry above."));
        } else {
            for (MonitorEntry m : tasks) {
                entriesListPanel.add(buildEntryCard(m));
                entriesListPanel.add(Box.createVerticalStrut(14));
            }
        }
        entriesListPanel.revalidate();
        entriesListPanel.repaint();

        entriesTitle.setText("Website Entries  (" + tasks.length + ")");
    }

    // A single user row in the left column: mail, phone, channel pill and a remove button.
    private JComponent buildUserRow(User user) {
        // size to content height: never stretch inside the BoxLayout, never clip the channel pill
        RoundedPanel row = new RoundedPanel(14, SURFACE, BORDER_SOFT) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setLayout(new BorderLayout(10, 0));
        row.setBorder(new EmptyBorderInsets(12, 14, 12, 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel mail = new JLabel(user.getMailAddress());
        mail.setFont(FONT_BOLD);
        mail.setForeground(TEXT_DARK);
        JLabel phone = new JLabel(user.getPhone());
        phone.setFont(FONT_SMALL);
        phone.setForeground(TEXT_MUTED);

        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        pillRow.setOpaque(false);
        pillRow.add(channelPill(user.notChan));

        info.add(mail);
        info.add(Box.createVerticalStrut(2));
        info.add(phone);
        info.add(pillRow);

        row.add(info, BorderLayout.CENTER);

        RoundedButton remove = circleButton("×", DANGER);
        remove.setToolTipText("Remove user");
        remove.addActionListener(e -> removeUser(user));
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        east.setOpaque(false);
        east.add(remove);
        row.add(east, BorderLayout.EAST);

        return row;
    }

    // A single entry as its own rounded card: title (url), badges, subscriber chips, live
    // "last checked" line and a remove button. Reads straight off the live MonitorEntry.
    private JComponent buildEntryCard(MonitorEntry m) {
        IContentType strategy = m.getCheckType();
        Frequency freq = m.getFreq();
        User[] subs = m.subscribedUsers;

        CardPanel card = new CardPanel(18);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorderInsets(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- top row: coloured dot + url, and a remove button -------------------------------
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);

        JPanel titleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleWrap.setOpaque(false);
        titleWrap.add(new Dot(strategyColor(strategy)));
        JLabel url = new JLabel(m.getUrl());
        url.setFont(FONT_H2);
        url.setForeground(TEXT_DARK);
        url.setToolTipText(m.getUrl());
        titleWrap.add(url);
        top.add(titleWrap, BorderLayout.CENTER);

        RoundedButton remove = circleButton("×", DANGER);
        remove.setToolTipText("Remove entry");
        remove.addActionListener(e -> removeEntry(m));
        JPanel rm = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rm.setOpaque(false);
        rm.add(remove);
        top.add(rm, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);

        // --- badges + subscriber chips + live timestamp -------------------------------------
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badges.setOpaque(false);
        badges.setAlignmentX(Component.LEFT_ALIGNMENT);
        badges.add(new PillLabel(freqLabel(freq), freqColor(freq)));
        badges.add(new PillLabel(strategyLabel(strategy), strategyColor(strategy)));
        badges.add(new PillLabel(subs.length + (subs.length == 1 ? " subscriber" : " subscribers"), TEXT_MUTED));
        body.add(badges);
        body.add(Box.createVerticalStrut(10));

        JPanel chips = new WrapPanel(8, 8);
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (User u : subs) {
            chips.add(buildSubscriberChip(m, u));
        }
        body.add(chips);

        body.add(Box.createVerticalStrut(10));
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel lastChecked = new JLabel(lastCheckedText(m.lastChecked));
        lastChecked.setFont(FONT_SMALL);
        lastChecked.setForeground(TEXT_MUTED);
        footer.add(lastChecked, BorderLayout.WEST);

        JLabel countdown = new JLabel(nextScanText(m));
        countdown.setFont(FONT_BOLD);
        countdown.setForeground(ACCENT);
        countdown.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.add(countdown, BorderLayout.EAST);

        body.add(footer);
        // refreshed every second by the Swing timer in the constructor
        timestampUpdaters.add(() -> {
            lastChecked.setText(lastCheckedText(m.lastChecked));
            countdown.setText(nextScanText(m));
        });

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // A subscriber chip inside an entry card: mail + channel colour + unsubscribe "x".
    private JComponent buildSubscriberChip(MonitorEntry entry, User user) {
        RoundedPanel chip = new RoundedPanel(12, new Color(0xF5F6FB), BORDER_SOFT);
        chip.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));

        chip.add(new Dot(channelColor(user.notChan)));
        JLabel label = new JLabel(user.getMailAddress());
        label.setFont(FONT_SMALL);
        label.setForeground(TEXT_DARK);
        chip.add(label);

        RoundedButton x = circleButton("×", TEXT_MUTED);
        x.setPreferredSize(new Dimension(20, 20));
        x.setToolTipText("Remove subscription");
        x.addActionListener(e -> unsubscribe(entry, user));
        chip.add(x);

        return chip;
    }

    // ============================================================================================
    //  Console redirect
    // ============================================================================================

    private void installConsoleRedirect() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(new ConsoleStream(originalOut, CONSOLE_OUT), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ConsoleStream(originalErr, CONSOLE_ERR), true, StandardCharsets.UTF_8));
    }

    // Appends text to the console pane on the EDT, colour-coded per source stream, and keeps the
    // view scrolled to the bottom. Also tees to the original stream so the IDE console still works.
    private void appendConsole(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = consolePane.getStyledDocument();
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, color);
            try {
                // cap the buffer so a long-running session does not grow unbounded
                if (doc.getLength() > 200_000) {
                    doc.remove(0, 60_000);
                }
                doc.insertString(doc.getLength(), text, attr);
            } catch (BadLocationException ignored) {
            }
            consolePane.setCaretPosition(doc.getLength());
        });
    }

    private final class ConsoleStream extends OutputStream {
        private final PrintStream mirror;
        private final Color color;

        ConsoleStream(PrintStream mirror, Color color) {
            this.mirror = mirror;
            this.color = color;
        }

        @Override public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override public void write(byte[] b, int off, int len) {
            String s = new String(b, off, len, StandardCharsets.UTF_8);
            if (mirror != null) mirror.print(s);
            appendConsole(s, color);
        }
    }

    // ============================================================================================
    //  Demo seed (mirrors Main.java so the overview is populated on first launch)
    // ============================================================================================

    private void seedDemoData() {
        User a = new User("anna@mail.com", "+49 170 1111111", new MailChannel());
        User b = new User("ben@mail.com", "+49 170 2222222", new SmsChannel());
        User c = new User("carla@mail.com", "+49 170 3333333", new WhatsAppChannel());
        users.add(a);
        users.add(b);
        users.add(c);

        scheduler.addSubscription("https://bengutzeit.de", Frequency.high, a, HTML);
        scheduler.addSubscription("https://www.tagesschau.de/", Frequency.mid, b, TEXT);
        scheduler.addSubscription("https://de.wikipedia.org/wiki/Tagesschau_(ARD)", Frequency.low, c, SIZE);
        scheduler.addSubscription("https://bengutzeit.de", Frequency.high, c, HTML); // second subscriber, same entry
    }

    // ============================================================================================
    //  Small helpers
    // ============================================================================================

    // Find the live scheduler task for this url+freq+strategy (strategy matched by identity), or null.
    private MonitorEntry findLiveEntry(String url, Frequency freq, IContentType strategy) {
        for (MonitorEntry m : scheduler.getRegisteredTasks()) {
            if (m.getUrl().equals(url) && m.getFreq() == freq && m.getCheckType() == strategy) {
                return m;
            }
        }
        return null;
    }

    private static boolean containsUser(User[] arr, User u) {
        for (User x : arr) {
            if (x.getMailAddress().equalsIgnoreCase(u.getMailAddress())) {
                return true;
            }
        }
        return false;
    }

    private static String lastCheckedText(Instant t) {
        if (t == null || t.equals(Instant.MIN)) {
            return "Last checked: never";
        }
        return "Last checked: " + TIME_FMT.format(t);
    }

    // Seconds between scans for a frequency, mirroring TaskScheduler.start()'s thresholds: high fires
    // once elapsed passes 1 whole minute (=> ~2 min), mid past 1 whole hour (=> ~2 h), low past 5 (=> ~6 h).
    private static long scanIntervalSeconds(Frequency f) {
        return switch (f) {
            case high -> 120L;
            case mid -> 2L * 3600L;
            case low -> 6L * 3600L;
        };
    }

    // Live countdown text until the entry's next scan, derived from its lastChecked timestamp.
    private static String nextScanText(MonitorEntry m) {
        if (m.lastChecked == null || m.lastChecked.equals(Instant.MIN)) {
            return "Next scan: imminent";
        }
        long remaining = scanIntervalSeconds(m.getFreq())
                - Duration.between(m.lastChecked, Instant.now()).getSeconds();
        if (remaining <= 0) {
            return "Next scan: imminent";
        }
        return "Next scan in " + formatDuration(remaining);
    }

    private static String formatDuration(long s) {
        if (s >= 3600) return String.format("%dh %02dm", s / 3600, (s % 3600) / 60);
        if (s >= 60) return String.format("%dm %02ds", s / 60, s % 60);
        return s + "s";
    }

    private void warn(String message) {
        System.err.println(message);
        JOptionPane.showMessageDialog(this, message, "Notice", JOptionPane.WARNING_MESSAGE);
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_H1);
        l.setForeground(TEXT_DARK);
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BOLD);
        l.setForeground(TEXT_MUTED);
        l.setBorder(new EmptyBorderInsets(0, 2, 4, 0));
        return l;
    }

    // wraps a label above a component for the side-by-side combos
    private JComponent labeled(String text, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(fieldLabel(text), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private JComponent emptyState(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorderInsets(24, 8, 24, 8));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(TEXT_MUTED);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(l, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        return p;
    }

    private GridBagConstraints formConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 0, 0);
        return c;
    }

    // A rounded white "shell" around an input so text fields and combos get rounded corners.
    private JComponent inputShell(JComponent inner) {
        RoundedPanel shell = new RoundedPanel(10, SURFACE, BORDER_SOFT);
        shell.setLayout(new BorderLayout());
        shell.setBorder(new EmptyBorderInsets(2, 10, 2, 6));

        inner.setOpaque(false);
        inner.setBorder(new EmptyBorderInsets(6, 0, 6, 0));
        inner.setFont(FONT_BASE);
        if (inner instanceof JTextField tf) {
            tf.setForeground(TEXT_DARK);
            tf.setCaretColor(ACCENT);
        }
        shell.add(inner, BorderLayout.CENTER);
        shell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return shell;
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setFont(FONT_BASE);
        combo.setBackground(SURFACE);
        combo.setForeground(TEXT_DARK);
        combo.setBorder(null);
    }

    private JScrollPane quietScroll(JComponent view) {
        // keep the scrollable content top-aligned inside the viewport
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(view, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private PillLabel channelPill(INotificationChannel chan) {
        return new PillLabel(channelLabel(chan), channelColor(chan));
    }

    private RoundedButton circleButton(String text, Color color) {
        RoundedButton b = new RoundedButton(text, blend(color, Color.WHITE, 0.86f), color);
        b.setFont(new Font(UI_FAMILY, Font.BOLD, 14));
        b.setPreferredSize(new Dimension(28, 28));
        b.setPadding(0, 0);
        b.setArc(14);
        return b;
    }

    // ---- label / colour mapping ----------------------------------------------------------------

    private static String channelLabel(INotificationChannel c) {
        if (c instanceof MailChannel) return "Mail";
        if (c instanceof SmsChannel) return "SMS";
        if (c instanceof WhatsAppChannel) return "WhatsApp";
        return "Channel";
    }

    private static Color channelColor(INotificationChannel c) {
        if (c instanceof MailChannel) return new Color(0x2563EB);
        if (c instanceof SmsChannel) return new Color(0xD97706);
        if (c instanceof WhatsAppChannel) return new Color(0x16A34A);
        return TEXT_MUTED;
    }

    private static String freqLabel(Frequency f) {
        return switch (f) {
            case high -> "High · every 2 min";
            case mid -> "Medium · every 2 h";
            case low -> "Low · every 6 h";
        };
    }

    private static Color freqColor(Frequency f) {
        return switch (f) {
            case high -> new Color(0xE11D48);
            case mid -> new Color(0xD97706);
            case low -> new Color(0x0D9488);
        };
    }

    private static String strategyLabel(IContentType t) {
        if (t instanceof IdenticalHtml) return "HTML (exact)";
        if (t instanceof IdenticalText) return "Text";
        if (t instanceof IdenticalSize) return "Size";
        return "Strategy";
    }

    private static Color strategyColor(IContentType t) {
        if (t instanceof IdenticalHtml) return new Color(0x7C3AED);
        if (t instanceof IdenticalText) return new Color(0x2563EB);
        if (t instanceof IdenticalSize) return new Color(0x0D9488);
        return ACCENT;
    }

    // Blend two colours: t=0 -> a, t=1 -> b.
    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ============================================================================================
    //  Custom components
    // ============================================================================================

    // A panel that paints a rounded rectangle background (and optional 1px border).
    private static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color bg;
        private final Color border;

        RoundedPanel(int arc, Color bg, Color border) {
            this.arc = arc;
            this.bg = bg;
            this.border = border;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            if (border != null) {
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // A white card with a soft drop shadow and hairline border (used for entry boxes).
    private static class CardPanel extends JPanel {
        private final int arc;

        CardPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            // soft shadow
            g2.setColor(new Color(20, 24, 50, 18));
            g2.fill(new RoundRectangle2D.Float(2, 4, w - 4, h - 4, arc, arc));
            g2.setColor(new Color(20, 24, 50, 12));
            g2.fill(new RoundRectangle2D.Float(1, 2, w - 2, h - 3, arc, arc));
            // card surface
            g2.setColor(SURFACE);
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 5, arc, arc));
            g2.setColor(BORDER_SOFT);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2f, h - 6f, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // A small filled dot, used as a coloured accent next to titles / chips.
    private static class Dot extends JComponent {
        private final Color color;

        Dot(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(10, 10));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int d = Math.min(getWidth(), getHeight());
            g2.fillOval(0, (getHeight() - d) / 2, d, d);
            g2.dispose();
        }
    }

    // A rounded "pill" label with a soft tinted background and strong coloured text.
    private static class PillLabel extends JLabel {
        private final Color tint;

        PillLabel(String text, Color color) {
            super(text);
            this.tint = blend(color, Color.WHITE, 0.84f);
            setFont(FONT_PILL);
            setForeground(blend(color, Color.BLACK, 0.12f));
            setBorder(new EmptyBorderInsets(4, 10, 4, 10));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(tint);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // A flat, rounded button with hover / press feedback.
    private static class RoundedButton extends JButton {
        private final Color base;
        private final Color hover;
        private final Color pressed;
        private int arc = 12;
        private int padX = 16;
        private int padY = 9;

        RoundedButton(String text, Color base, Color fg) {
            super(text);
            this.base = base;
            this.hover = blend(base, Color.WHITE, 0.10f);
            this.pressed = blend(base, Color.BLACK, 0.10f);
            setForeground(fg);
            setFont(FONT_BOLD);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorderInsets(padY, padX, padY, padX));
        }

        void setPadding(int x, int y) {
            this.padX = x;
            this.padY = y;
            setBorder(new EmptyBorderInsets(y, x, y, x));
        }

        void setArc(int arc) {
            this.arc = arc;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (getModel().isPressed()) c = pressed;
            else if (getModel().isRollover()) c = hover;
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // A FlowLayout variant that wraps to multiple lines and reports the correct preferred height,
    // so subscriber chips inside a card flow nicely and the card grows to fit them.
    private static class WrapPanel extends JPanel {
        WrapPanel(int hgap, int vgap) {
            super(new FlowLayout(FlowLayout.LEFT, hgap, vgap));
        }

        @Override public Dimension getPreferredSize() {
            int width = getWidth();
            if (width == 0) {
                Container p = getParent();
                width = (p != null) ? p.getWidth() : 0;
            }
            if (width == 0) return super.getPreferredSize();

            FlowLayout fl = (FlowLayout) getLayout();
            Insets in = getInsets();
            int maxW = width - in.left - in.right;
            int x = 0;
            int y = in.top + fl.getVgap();
            int rowH = 0;
            for (Component comp : getComponents()) {
                if (!comp.isVisible()) continue;
                Dimension d = comp.getPreferredSize();
                if (x == 0) {
                    x = d.width;
                } else if (x + fl.getHgap() + d.width <= maxW) {
                    x += fl.getHgap() + d.width;
                } else {
                    y += rowH + fl.getVgap();
                    x = d.width;
                    rowH = 0;
                }
                rowH = Math.max(rowH, d.height);
            }
            return new Dimension(width, y + rowH + fl.getVgap() + in.bottom);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    // A renderer that shows a custom label string for each combo item via a mapping function.
    private static class LabelRenderer extends DefaultListCellRenderer {
        private final java.util.function.Function<Object, String> mapper;

        LabelRenderer(java.util.function.Function<Object, String> mapper) {
            this.mapper = mapper;
        }

        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                 boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText(value == null ? "" : mapper.apply(value));
            setBorder(new EmptyBorderInsets(4, 6, 4, 6));
            return this;
        }
    }

    // Renders a User in the entry-form combo as "mail . channel".
    private static class UserRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof User u) {
                setText(u.getMailAddress() + "  ·  " + channelLabel(u.notChan));
            } else {
                setText("Select user");
            }
            setBorder(new EmptyBorderInsets(4, 6, 4, 6));
            return this;
        }
    }

    // Tiny EmptyBorder so we can build padding inline; behaves like BorderFactory.createEmptyBorder.
    private static class EmptyBorderInsets extends AbstractBorder {
        private final Insets insets;

        EmptyBorderInsets(int all) {
            this(all, all, all, all);
        }

        EmptyBorderInsets(int top, int left, int bottom, int right) {
            this.insets = new Insets(top, left, bottom, right);
        }

        @Override public Insets getBorderInsets(Component c) {
            return (Insets) insets.clone();
        }

        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(insets.top, insets.left, insets.bottom, insets.right);
            return i;
        }

        @Override public boolean isBorderOpaque() {
            return false;
        }
    }
}
