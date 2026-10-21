import java.awt.*;
import java.awt.font.TextAttribute;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.*;

class motivation
{
    private static final String[] quotes = {
        "Small steps every day add up to big change.",
        "Consistency beats intensity.",
        "You don't have to be perfect, you just have to show up.",
        "Progress, not perfection.",
        "A habit is built one repetition at a time.",
        "The best time to start was yesterday; the next best time is now."
    };

    private static final String[] inspirations = {
        "Every completed habit is a vote for the person you want to become.",
        "Missed a day? Begin again today - what matters is that you keep going.",
        "Look how far you have already come, and let that carry you forward.",
        "Take care of your body and mind; your habits will follow.",
        "Your future self is grateful for what you do today."
    };

    private static final String[] milestone_msgs = {
        "Amazing work! You reached a milestone - keep the momentum going!",
        "Milestone achieved! Your consistency is paying off.",
        "You did it! Another milestone is behind you."
    };

    private static final Random rnd = new Random();

    public static String quote()             { return quotes[rnd.nextInt(quotes.length)]; }
    public static String inspiration()        { return inspirations[rnd.nextInt(inspirations.length)]; }
    public static String milestone_message()  { return milestone_msgs[rnd.nextInt(milestone_msgs.length)]; }
}

class user_account implements Serializable
{
    private static final long serialVersionUID = 1L;

    final String user_id;
    final byte[] salt;
    final byte[] hash;

    user_account(String user_id, byte[] salt, byte[] hash)
    {
        this.user_id = user_id;
        this.salt = salt;
        this.hash = hash;
    }
}

class wellness_record implements Serializable
{
    private static final long serialVersionUID = 1L;

    final LocalDateTime taken_on;
    final int mood;
    final int stress;
    final double sleep_hours;
    final int water_glasses;
    final String notes;

    wellness_record(int mood, int stress, double sleep_hours, int water_glasses, String notes)
    {
        this.taken_on = LocalDateTime.now();
        this.mood = mood;
        this.stress = stress;
        this.sleep_hours = sleep_hours;
        this.water_glasses = water_glasses;
        this.notes = notes;
    }

    public String toString()
    {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

        return f.format(taken_on) + "  |  mood " + mood + "/5  |  stress " + stress + "/5  |  sleep "
            + sleep_hours + "h  |  water " + water_glasses + " glasses"
            + (notes.isEmpty() ? "" : "  |  " + notes);
    }
}

class personal_habit implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String owner;
    private String habit_name;
    private boolean enabled;
    private final LocalDate created_date;
    private final Stack<String> progress;
    private final ArrayList<String> content;
    private final ArrayList<LocalDateTime> completion_dates;
    private final ArrayList<String> hyperlinks;
    private final TreeSet<Integer> milestones;
    private int completion_count;

    public personal_habit(int id, String owner, String name, String first_progress,
                          String first_requirement, int first_milestone)
    {
        this.id = id;
        this.owner = owner;
        this.habit_name = name;
        this.enabled = true;
        this.created_date = LocalDate.now();
        this.progress = new Stack<>();
        this.content = new ArrayList<>();
        this.completion_dates = new ArrayList<>();
        this.hyperlinks = new ArrayList<>();
        this.milestones = new TreeSet<>();
        this.completion_count = 0;

        progress.push(first_progress);
        content.add(first_requirement);
        milestones.add(first_milestone);
    }

    public int get_id()               { return id; }
    public String get_owner()         { return owner; }
    public String get_name()          { return habit_name; }
    public boolean is_enabled()       { return enabled; }
    public LocalDate get_created()    { return created_date; }
    public int get_completion_count() { return completion_count; }
    public boolean has_requirements() { return !content.isEmpty(); }
    public int hyperlink_count()      { return hyperlinks.size(); }
    public List<String> get_progress()
    {
        List<String> copy = new ArrayList<>(progress);
        Collections.reverse(copy);
        return copy;
    }

    public List<String> get_requirements()               { return new ArrayList<>(content); }
    public List<Integer> get_milestones()                 { return new ArrayList<>(milestones); }
    public List<String> get_hyperlinks()                  { return new ArrayList<>(hyperlinks); }

    public List<LocalDateTime> get_completions_recent_first()
    {
        List<LocalDateTime> copy = new ArrayList<>(completion_dates);
        Collections.reverse(copy);
        return copy;
    }

    public Integer next_milestone()   { return milestones.higher(completion_count); }

    public void update_name(String name)    { habit_name = name; }
    public void add_progress(String str)    { progress.push(str); }
    public void add_req(String str)         { content.add(str); }
    public boolean delete_req(int number)
    {
        if(number < 1 || number > content.size())
        {
            return false;
        }

        content.remove(number - 1);
        return true;
    }
    public boolean add_milestone(int count)     { return milestones.add(count); }
    public boolean remove_milestone(int count)  { return milestones.remove(count); }
    public boolean add_hyperlink(String link)
    {
        String url = link.contains("://") ? link : "https://" + link;

        try
        {
            URI u = new URI(url);
            String scheme = u.getScheme();

            if(u.getHost() == null || scheme == null
               || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
            {
                return false;
            }
        }
        catch(Exception e)
        {
            return false;
        }

        hyperlinks.add(url);
        return true;
    }
    public String open_hyperlink(int number)
    {
        if(number < 1 || number > hyperlinks.size())
        {
            return "Invalid hyperlink number.";
        }

        String url = hyperlinks.get(number - 1);

        try
        {
            if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            {
                Desktop.getDesktop().browse(new URI(url));
                return "Opening in your default browser: " + url;
            }
        }
        catch(Exception e)
        {

        }

        return "Could not open a browser automatically. Link: " + url;
    }

    public boolean toggle_enabled()
    {
        enabled = !enabled;
        return enabled;
    }

    public String record_completion()
    {
        if(!enabled)
        {
            return "This habit is disabled. Enable it before recording a completion.";
        }

        LocalDateTime completionTime = LocalDateTime.now();

        completion_dates.add(completionTime);
        completion_count++;

        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("Habit completed successfully!\n");
        sb.append("Completion date and time: ").append(f.format(completionTime)).append("\n");

        if(milestones.contains(completion_count))
        {
            sb.append("\nCongratulations! You achieved your milestone of ")
              .append(completion_count).append(" completion(s)!\n");
            sb.append(motivation.milestone_message());
        }
        else
        {
            Integer next = milestones.higher(completion_count);

            if(next != null)
            {
                sb.append("\nNext milestone: ").append(next).append(" completions (")
                  .append(next - completion_count).append(" to go).");
            }
        }

        return sb.toString();
    }
}

class data_store implements Serializable
{
    private static final long serialVersionUID = 1L;

    HashMap<String, user_account> users = new HashMap<>();
    HashMap<Integer, personal_habit> habits = new HashMap<>();
    HashMap<String, ArrayList<wellness_record>> wellness = new HashMap<>();
    int last_habit_id = 0;
}

class auth_manager
{
    private static final int ITERATIONS = 65536;

    private static byte[] hash_password(char[] pw, byte[] salt) throws Exception
    {
        PBEKeySpec spec = new PBEKeySpec(pw, salt, ITERATIONS, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    public static String register(data_store ds, String id, String password)
    {
        if(id.isEmpty() || password.isEmpty())
        {
            return "User ID and password are mandatory.";
        }

        if(ds.users.containsKey(id.toLowerCase()))
        {
            return "User ID already exists. Please choose another.";
        }

        try
        {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            ds.users.put(id.toLowerCase(), new user_account(id, salt, hash_password(password.toCharArray(), salt)));
            return null;
        }
        catch(Exception e)
        {
            return "Could not register user.";
        }
    }

    public static boolean login(data_store ds, String id, String password)
    {
        user_account ua = ds.users.get(id.toLowerCase());

        if(ua == null)
        {
            return false;
        }

        try
        {
            return MessageDigest.isEqual(ua.hash, hash_password(password.toCharArray(), ua.salt));
        }
        catch(Exception e)
        {
            return false;
        }
    }
}

class phabit
{
    private static final String DATA_FILE = "habitharmony.dat";

    private data_store ds = new data_store();
    private String current_user = null;
    public void save_data()
    {
        try
        {
            Path tmp = Paths.get(DATA_FILE + ".tmp");

            try(ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(tmp)))
            {
                oos.writeObject(ds);
            }

            Files.move(tmp, Paths.get(DATA_FILE),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch(Exception e)
        {
            System.err.println("Error while saving data: " + e.getMessage());
        }
    }

    public void load_data()
    {
        File f = new File(DATA_FILE);

        if(!f.exists())
        {
            return;
        }

        try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f)))
        {
            ds = (data_store) ois.readObject();
        }
        catch(Exception e)
        {
            System.err.println("Saved data could not be read (" + e.getMessage() + "). Starting fresh.");
            ds = new data_store();
        }
    }

    public boolean is_logged_in()  { return current_user != null; }
    public String current_user()   { return current_user; }
    public String register(String id, String password)
    {
        String err = auth_manager.register(ds, id, password);

        if(err == null)
        {
            save_data();
        }

        return err;
    }

    public String login(String id, String password)
    {
        if(id.isEmpty() || password.isEmpty())
        {
            return "User ID and password are mandatory.";
        }

        if(!auth_manager.login(ds, id, password))
        {
            return "Invalid User ID or password.";
        }

        current_user = ds.users.get(id.toLowerCase()).user_id;
        return null;
    }

    public void logout()
    {
        save_data();
        current_user = null;
    }

    public List<personal_habit> list_habits(boolean include_disabled)
    {
        List<personal_habit> list = new ArrayList<>();

        for(personal_habit p : ds.habits.values())
        {
            if(p.get_owner().equalsIgnoreCase(current_user) && (include_disabled || p.is_enabled()))
            {
                list.add(p);
            }
        }

        list.sort(Comparator.comparingInt(personal_habit::get_id));
        return list;
    }

    public List<personal_habit> search_habit(String text)
    {
        List<personal_habit> results = new ArrayList<>();

        for(personal_habit p : list_habits(true))
        {
            if(p.get_name().toLowerCase().contains(text.toLowerCase()))
            {
                results.add(p);
            }
        }

        return results;
    }

    private boolean name_taken(String name, personal_habit ignore)
    {
        for(personal_habit p : list_habits(true))
        {
            if(p != ignore && p.get_name().equalsIgnoreCase(name))
            {
                return true;
            }
        }

        return false;
    }

    public String create_habit(String name, String progress, String requirement, int milestone)
    {
        if(name.isEmpty() || progress.isEmpty() || requirement.isEmpty())
        {
            return "Name, progress and requirement cannot be blank.";
        }

        if(name_taken(name, null))
        {
            return "You already have a habit with that name.";
        }

        int id = ++ds.last_habit_id;

        ds.habits.put(id, new personal_habit(id, current_user, name, progress, requirement, milestone));
        save_data();
        return null;
    }

    public String rename_habit(personal_habit p, String new_name)
    {
        if(new_name.isEmpty())
        {
            return "Name cannot be blank.";
        }

        if(name_taken(new_name, p))
        {
            return "You already have a habit with that name.";
        }

        p.update_name(new_name);
        save_data();
        return null;
    }

    public String delete_habit(personal_habit p)
    {
        if(p == null || !p.get_owner().equalsIgnoreCase(current_user))
        {
            return "Habit not found.";
        }

        ds.habits.remove(p.get_id());
        save_data();
        return null;
    }

    public void persist()
    {
        save_data();
    }

    public void wellness_check(int mood, int stress, double sleep, int water, String notes)
    {
        ds.wellness.computeIfAbsent(current_user.toLowerCase(), k -> new ArrayList<>())
            .add(new wellness_record(mood, stress, sleep, water, notes));
        save_data();
    }

    public List<wellness_record> wellness_history()
    {
        ArrayList<wellness_record> list = ds.wellness.get(current_user.toLowerCase());
        List<wellness_record> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }
}

class habit_theme
{
    private static Font aster_font;

    static final Color BACKGROUND = new Color(12, 12, 12);
    static final Color SURFACE = new Color(25, 25, 25);
    static final Color BUTTON = new Color(185, 185, 185);
    static final Color FIELD = new Color(19, 19, 19);
    static final Color FOREGROUND = new Color(232, 232, 232);
    static final Color MUTED = new Color(170, 170, 170);
    static final Color OUTLINE = new Color(92, 92, 92);
    static final Color GREEN = new Color(24, 67, 43);
    static final Color GREEN_OUTLINE = new Color(77, 175, 105);
    static final Color RED = new Color(75, 33, 37);
    static final Color RED_OUTLINE = new Color(190, 81, 85);

    static void apply()
    {
        aster_font = loadAsterFont();
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", FOREGROUND);
        UIManager.put("Button.background", BUTTON);
        UIManager.put("Button.foreground", Color.BLACK);
        UIManager.put("Button.disabledText", new Color(55, 55, 55));
        UIManager.put("Button.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(OUTLINE), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        UIManager.put("Button.focus", OUTLINE);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", FOREGROUND);
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("TextField.caretForeground", FOREGROUND);
        UIManager.put("TextField.selectionBackground", new Color(65, 65, 65));
        UIManager.put("TextField.selectionForeground", FOREGROUND);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(OUTLINE), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        UIManager.put("PasswordField.background", FIELD);
        UIManager.put("PasswordField.foreground", FOREGROUND);
        UIManager.put("PasswordField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("PasswordField.caretForeground", FOREGROUND);
        UIManager.put("PasswordField.selectionBackground", new Color(65, 65, 65));
        UIManager.put("PasswordField.selectionForeground", FOREGROUND);
        UIManager.put("PasswordField.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(OUTLINE), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        UIManager.put("CheckBox.background", BACKGROUND);
        UIManager.put("CheckBox.foreground", FOREGROUND);
        UIManager.put("TabbedPane.background", BUTTON);
        UIManager.put("TabbedPane.foreground", Color.BLACK);
        UIManager.put("TabbedPane.selected", BUTTON);
        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(OUTLINE));
        UIManager.put("Viewport.background", BACKGROUND);
        UIManager.put("List.background", SURFACE);
        UIManager.put("List.foreground", FOREGROUND);
        UIManager.put("List.selectionBackground", new Color(65, 65, 65));
        UIManager.put("List.selectionForeground", FOREGROUND);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", FOREGROUND);
        UIManager.put("Table.gridColor", OUTLINE);
        UIManager.put("Table.selectionBackground", new Color(65, 65, 65));
        UIManager.put("Table.selectionForeground", FOREGROUND);
        UIManager.put("TableHeader.background", SURFACE);
        UIManager.put("TableHeader.foreground", FOREGROUND);
        UIManager.put("OptionPane.background", BACKGROUND);
        UIManager.put("OptionPane.messageForeground", FOREGROUND);
        UIManager.put("TitledBorder.titleColor", MUTED);
        UIManager.put("Spinner.background", FIELD);
        UIManager.put("Spinner.foreground", FOREGROUND);
        UIManager.put("ComboBox.background", FIELD);
        UIManager.put("ComboBox.foreground", FOREGROUND);
        UIManager.put("control", BACKGROUND);
        UIManager.put("text", FOREGROUND);
        UIManager.put("nimbusBase", SURFACE);
        UIManager.put("nimbusBlueGrey", OUTLINE);
        UIManager.put("textText", FOREGROUND);
    }

    static Font titleFont(int size)
    {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, (float) size);
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
        attributes.put(TextAttribute.TRACKING, 0.08f);
        return aster_font.deriveFont(attributes);
    }

    private static Font loadAsterFont()
    {
        String load_error = null;

        try(InputStream resource = habit_theme.class.getResourceAsStream("/fonts/Phantacon.ttf"))
        {
            if(resource != null)
            {
                return Font.createFont(Font.TRUETYPE_FONT, resource);
            }
        }
        catch(IOException | FontFormatException e)
        {
            load_error = e.getMessage();
        }

        Path font_path = Paths.get("fonts", "Phantacon.ttf");

        try
        {
            if(Files.isRegularFile(font_path))
            {
                try(InputStream file = Files.newInputStream(font_path))
                {
                    return Font.createFont(Font.TRUETYPE_FONT, file);
                }
            }
        }
        catch(IOException | FontFormatException e)
        {
            load_error = e.getMessage();
        }

        System.err.println("Phantacon font could not be loaded from the application resources or "
            + font_path.toAbsolutePath() + (load_error == null ? "." : ": " + load_error));
        return new Font("Serif", Font.BOLD, 12);
    }
}

class GUIhabit
{
    private final JFrame frame;
    private final phabit backend;

    public GUIhabit()
    {
        habit_theme.apply();
        backend = new phabit();
        backend.load_data();

        Runtime.getRuntime().addShutdownHook(new Thread(backend::save_data));

        frame = new JFrame("HabitHarmony");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(480, 320);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(habit_theme.BACKGROUND);

        JLabel title = new JLabel("HabitHarmony", SwingConstants.CENTER);
        JLabel subtitle = new JLabel("Make progress by building Habits.", SwingConstants.CENTER);

        JButton login_button = new JButton("Login");
        JButton register_button = new JButton("Register");

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 12));
        actions.setOpaque(false);
        actions.add(login_button);
        actions.add(register_button);

        JPanel centered_content = new JPanel(new GridBagLayout());
        centered_content.setBackground(habit_theme.BACKGROUND);
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints content_constraints = new GridBagConstraints();
        content_constraints.gridx = 0;
        content_constraints.fill = GridBagConstraints.HORIZONTAL;
        content_constraints.weightx = 1.0;
        content_constraints.insets = new Insets(8, 0, 8, 0);
        content_constraints.gridy = 0;
        content.add(title, content_constraints);
        content_constraints.gridy = 1;
        content.add(subtitle, content_constraints);
        content_constraints.gridy = 2;
        content_constraints.insets = new Insets(18, 0, 0, 0);
        content.add(actions, content_constraints);

        centered_content.add(content, new GridBagConstraints());
        frame.add(centered_content, BorderLayout.CENTER);
        Runnable scale_content = () ->
        {
            double scale = Math.min(frame.getWidth() / 480.0, frame.getHeight() / 320.0);
            scale = Math.max(1.0, Math.min(scale, 2.0));
            title.setFont(habit_theme.titleFont((int) Math.round(28 * scale)));
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, (int) Math.round(13 * scale)));
            Dimension button_size = new Dimension((int) Math.round(110 * scale), (int) Math.round(38 * scale));
            Font button_font = new Font("Segoe UI", Font.BOLD, (int) Math.round(15 * scale));
            for(JButton button : new JButton[]{login_button, register_button})
            {
                button.setPreferredSize(button_size);
                button.setFont(button_font);
            }
            content.revalidate();
            centered_content.revalidate();
            centered_content.repaint();
        };
        frame.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentResized(java.awt.event.ComponentEvent event)
            {
                scale_content.run();
            }
        });
        scale_content.run();

        login_button.addActionListener(e ->
        {
            frame.setVisible(false);
            new LoginWindow(frame, backend, this::onLoginSuccess);
        });

        register_button.addActionListener(e ->
        {
            frame.setVisible(false);
            new RegisterWindow(frame, backend);
        });

        frame.setVisible(true);
    }

    private void onLoginSuccess()
    {
        new Dashboard(backend, () -> frame.setVisible(true));
    }
}

class RegisterWindow
{
    public RegisterWindow(JFrame owner, phabit backend)
    {
        JDialog dialog = new JDialog(owner, "Register", true);
        dialog.setSize(380, 260);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getContentPane().setBackground(habit_theme.BACKGROUND);

        JLabel title = new JLabel("Create your account", SwingConstants.CENTER);
        title.setFont(habit_theme.titleFont(24));
        title.setOpaque(true);
        title.setBackground(habit_theme.OUTLINE);
        title.setBorder(BorderFactory.createEmptyBorder(18, 10, 2, 10));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 12));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JTextField userid_field = new JTextField();
        JPasswordField password_field = new JPasswordField();

        form.add(new JLabel("User ID"));
        form.add(userid_field);
        form.add(new JLabel("Password"));
        form.add(password_field);

        JButton submit = new JButton("Register");
        JButton cancel = new JButton("Cancel");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        buttons.add(submit);
        buttons.add(cancel);

        dialog.add(title, BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);

        Runnable close_to_entry = () ->
        {
            dialog.dispose();
            owner.setVisible(true);
        };

        submit.addActionListener(e ->
        {
            String userid = userid_field.getText().trim();
            String password = new String(password_field.getPassword());

            String error = backend.register(userid, password);

            if(error != null)
            {
                JOptionPane.showMessageDialog(dialog, error, "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JOptionPane.showMessageDialog(dialog, "Registration successful. You can now log in.");
            close_to_entry.run();
        });

        cancel.addActionListener(e -> close_to_entry.run());
        dialog.addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosed(java.awt.event.WindowEvent e) { owner.setVisible(true); }
        });

        dialog.setVisible(true);
    }
}

class LoginWindow
{
    public LoginWindow(JFrame owner, phabit backend, Runnable onSuccess)
    {
        JDialog dialog = new JDialog(owner, "Login", true);
        dialog.setSize(380, 260);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getContentPane().setBackground(habit_theme.BACKGROUND);

        JLabel title = new JLabel("Welcome back", SwingConstants.CENTER);
        title.setFont(habit_theme.titleFont(24));
        title.setOpaque(true);
        title.setBackground(habit_theme.OUTLINE);
        title.setBorder(BorderFactory.createEmptyBorder(18, 10, 2, 10));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 12));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JTextField userid_field = new JTextField();
        JPasswordField password_field = new JPasswordField();

        form.add(new JLabel("User ID"));
        form.add(userid_field);
        form.add(new JLabel("Password"));
        form.add(password_field);

        JButton submit = new JButton("Login");
        JButton cancel = new JButton("Cancel");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        buttons.add(submit);
        buttons.add(cancel);

        dialog.add(title, BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);

        Runnable close_to_entry = () ->
        {
            dialog.dispose();
            owner.setVisible(true);
        };

        Runnable attempt = () ->
        {
            String error = backend.login(userid_field.getText().trim(), new String(password_field.getPassword()));

            if(error != null)
            {
                JOptionPane.showMessageDialog(dialog, error, "Login Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            dialog.dispose();
            onSuccess.run();
        };

        submit.addActionListener(e -> attempt.run());
        password_field.addActionListener(e -> attempt.run());   // Enter key submits
        cancel.addActionListener(e -> close_to_entry.run());

        dialog.addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosed(java.awt.event.WindowEvent e)
            {
                if(!backend.is_logged_in())
                {
                    owner.setVisible(true);
                }
            }
        });

        dialog.setVisible(true);
    }
}

class Dashboard
{
    private final phabit backend;
    private final Runnable onLogout;
    private final JFrame frame;

    private JPanel habit_list_panel;
    private personal_habit selected_habit;
    private JCheckBox show_disabled;
    private JTextField search_field;
    private List<personal_habit> current_rows = new ArrayList<>();

    private DefaultListModel<String> wellness_model;

    public Dashboard(phabit backend, Runnable onLogout)
    {
        this.backend = backend;
        this.onLogout = onLogout;

        frame = new JFrame("HabitHarmony - " + backend.current_user());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(920, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(habit_theme.BACKGROUND);

        frame.add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Habits", buildHabitsTab());
        tabs.addTab("Wellness", buildWellnessTab());

        frame.add(tabs, BorderLayout.CENTER);

        refreshTable();
        refreshWellness();

        frame.setVisible(true);
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(habit_theme.OUTLINE);
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel welcome = new JLabel("Welcome, " + backend.current_user() + "  -  \"" + motivation.quote() + "\"");
        welcome.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e ->
        {
            backend.logout();
            frame.dispose();
            onLogout.run();
        });

        header.add(welcome, BorderLayout.WEST);
        header.add(logout, BorderLayout.EAST);
        return header;
    }

    private JPanel buildHabitsTab()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        search_field = new JTextField(16);
        JButton search_button = new JButton("Search");
        JButton clear_button = new JButton("Clear");
        show_disabled = new JCheckBox("Show disabled");
        JButton new_habit = new JButton("+ New Habit");

        toolbar.add(new JLabel("Find:"));
        toolbar.add(search_field);
        toolbar.add(search_button);
        toolbar.add(clear_button);
        toolbar.add(show_disabled);
        toolbar.add(new_habit);

        habit_list_panel = new JPanel();
        habit_list_panel.setLayout(new BoxLayout(habit_list_panel, BoxLayout.Y_AXIS));
        habit_list_panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane scroll = new JScrollPane(habit_list_panel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton manage_button = new JButton("Manage Selected");
        JButton delete_button = new JButton("Delete Selected");
        actions.add(manage_button);
        actions.add(delete_button);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);

        search_button.addActionListener(e -> refreshTable());
        clear_button.addActionListener(e -> { search_field.setText(""); refreshTable(); });
        show_disabled.addActionListener(e -> refreshTable());
        new_habit.addActionListener(e -> new HabitFormDialog(frame, backend, this::refreshTable));

        manage_button.addActionListener(e -> openSelected());

        delete_button.addActionListener(e ->
        {
            personal_habit p = selected();

            if(p == null)
            {
                JOptionPane.showMessageDialog(frame, "Select a habit first.");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(frame,
                "Delete habit '" + p.get_name() + "' permanently? This cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if(confirm == JOptionPane.YES_OPTION)
            {
                backend.delete_habit(p);
                refreshTable();
            }
        });

        return panel;
    }

    private personal_habit selected()
    {
        return selected_habit != null && current_rows.contains(selected_habit) ? selected_habit : null;
    }

    private void openSelected()
    {
        personal_habit p = selected();

        if(p == null)
        {
            JOptionPane.showMessageDialog(frame, "Select a habit first.");
            return;
        }

        new HabitDetailDialog(frame, backend, p, this::refreshTable);
    }

    private void refreshTable()
    {
        int selected_id = selected_habit == null ? -1 : selected_habit.get_id();
        String text = search_field.getText().trim();

        current_rows = text.isEmpty()
            ? backend.list_habits(show_disabled.isSelected())
            : backend.search_habit(text);

        if(!text.isEmpty() && !show_disabled.isSelected())
        {
            current_rows.removeIf(p -> !p.is_enabled());
        }

        habit_list_panel.removeAll();
        selected_habit = null;

        for(personal_habit p : current_rows)
        {
            JPanel card = buildHabitCard(p);
            habit_list_panel.add(card);

            if(p.get_id() == selected_id)
            {
                selected_habit = p;
            }
        }

        if(current_rows.isEmpty())
        {
            JLabel empty = new JLabel("No habits yet. Create one to get started.");
            empty.setForeground(habit_theme.MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(20, 12, 20, 12));
            habit_list_panel.add(empty);
        }

        updateCardBorders();
        habit_list_panel.revalidate();
        habit_list_panel.repaint();
    }

    private JPanel buildHabitCard(personal_habit habit)
    {
        boolean milestone_reached = habit.get_completion_count() > 0
            && habit.get_milestones().contains(habit.get_completion_count());
        Color fill = !habit.is_enabled() ? habit_theme.RED
            : milestone_reached ? habit_theme.GREEN : habit_theme.SURFACE;
        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(fill);
        card.setOpaque(true);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(cardOutline(habit), 1),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel heading = new JPanel(new BorderLayout(8, 2));
        heading.setOpaque(false);
        JLabel name = new JLabel(habit.get_name());
        name.setFont(new Font("Segoe UI", Font.BOLD, 17));
        JLabel status = new JLabel(!habit.is_enabled() ? "DISABLED"
            : milestone_reached ? "MILESTONE REACHED" : "ACTIVE");
        status.setForeground(!habit.is_enabled() ? new Color(255, 160, 160)
            : milestone_reached ? new Color(150, 245, 175) : habit_theme.MUTED);
        status.setFont(new Font("Segoe UI", Font.BOLD, 11));
        heading.add(name, BorderLayout.WEST);
        heading.add(status, BorderLayout.EAST);

        List<Integer> milestones = habit.get_milestones();
        String milestone_text = milestones.isEmpty() ? "None" : milestones.toString();
        Integer next_milestone = habit.next_milestone();
        String next_text = next_milestone == null ? "All milestones reached"
            : "Next milestone: " + next_milestone + " (" + (next_milestone - habit.get_completion_count()) + " to go)";
        JLabel details = new JLabel("Habit ID: " + habit.get_id() + "    |    Created: " + habit.get_created()
            + "    |    Completions: " + habit.get_completion_count() + "    |    " + next_text);
        details.setForeground(habit_theme.FOREGROUND);
        JLabel milestone_label = new JLabel("Milestones: " + milestone_text);
        milestone_label.setForeground(habit_theme.MUTED);
        List<String> progress = habit.get_progress();
        JLabel progress_label = new JLabel("Current progress: "
            + (progress.isEmpty() ? "Not started" : progress.get(0)));
        progress_label.setForeground(habit_theme.MUTED);

        JPanel information = new JPanel();
        information.setLayout(new BoxLayout(information, BoxLayout.Y_AXIS));
        information.setOpaque(false);
        information.add(details);
        information.add(milestone_label);
        information.add(progress_label);

        card.add(heading, BorderLayout.NORTH);
        card.add(information, BorderLayout.CENTER);

        java.awt.event.MouseAdapter card_listener = new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent event)
            {
                selected_habit = habit;
                updateCardBorders();

                if(event.getClickCount() == 2)
                {
                    openSelected();
                }
            }
        };
        attachCardListener(card, card_listener);
        return card;
    }

    private Color cardOutline(personal_habit habit)
    {
        if(!habit.is_enabled())
        {
            return habit_theme.RED_OUTLINE;
        }

        return habit.get_completion_count() > 0
            && habit.get_milestones().contains(habit.get_completion_count())
            ? habit_theme.GREEN_OUTLINE : habit_theme.OUTLINE;
    }

    private void attachCardListener(Component component, java.awt.event.MouseAdapter listener)
    {
        component.addMouseListener(listener);

        if(component instanceof Container)
        {
            for(Component child : ((Container) component).getComponents())
            {
                attachCardListener(child, listener);
            }
        }
    }

    private void updateCardBorders()
    {
        for(int i = 0; i < habit_list_panel.getComponentCount() && i < current_rows.size(); i++)
        {
            Component component = habit_list_panel.getComponent(i);

            if(component instanceof JPanel)
            {
                personal_habit habit = current_rows.get(i);
                Color outline = selected_habit == habit ? habit_theme.FOREGROUND : cardOutline(habit);
                ((JPanel) component).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(outline, selected_habit == habit ? 2 : 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            }
        }

        habit_list_panel.repaint();
    }

    private JPanel buildWellnessTab()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton new_check = new JButton("+ New Wellness Check");
        JButton refresh = new JButton("Refresh");
        toolbar.add(new_check);
        toolbar.add(refresh);

        wellness_model = new DefaultListModel<>();
        JList<String> list = new JList<>(wellness_model);
        list.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(list);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        new_check.addActionListener(e -> new WellnessCheckDialog(frame, backend, this::refreshWellness));
        refresh.addActionListener(e -> refreshWellness());

        return panel;
    }

    private void refreshWellness()
    {
        wellness_model.clear();

        for(wellness_record w : backend.wellness_history())
        {
            wellness_model.addElement(w.toString());
        }

        if(wellness_model.isEmpty())
        {
            wellness_model.addElement("No wellness checks recorded yet.");
        }
    }
}

class HabitFormDialog
{
    public HabitFormDialog(JFrame owner, phabit backend, Runnable onSaved)
    {
        JDialog dialog = new JDialog(owner, "New Habit", true);
        dialog.setSize(420, 320);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 10));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JTextField name_field = new JTextField();
        JTextField progress_field = new JTextField();
        JTextField requirement_field = new JTextField();
        JSpinner milestone_spinner = new JSpinner(new SpinnerNumberModel(7, 1, 100000, 1));

        form.add(new JLabel("Habit name"));
        form.add(name_field);
        form.add(new JLabel("Current progress"));
        form.add(progress_field);
        form.add(new JLabel("Requirement"));
        form.add(requirement_field);
        form.add(new JLabel("Milestone (completions)"));
        form.add(milestone_spinner);

        JButton save = new JButton("Create");
        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        buttons.add(save);
        buttons.add(cancel);

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);

        save.addActionListener(e ->
        {
            String err = backend.create_habit(
                name_field.getText().trim(),
                progress_field.getText().trim(),
                requirement_field.getText().trim(),
                (Integer) milestone_spinner.getValue());

            if(err != null)
            {
                JOptionPane.showMessageDialog(dialog, err, "Could Not Create Habit", JOptionPane.ERROR_MESSAGE);
                return;
            }

            dialog.dispose();
            onSaved.run();
        });

        cancel.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }
}

class HabitDetailDialog
{
    private final phabit backend;
    private final personal_habit habit;
    private final Runnable onChange;
    private final JDialog dialog;

    private JLabel status_label;
    private JLabel completion_label;
    private DefaultListModel<String> progress_model;
    private DefaultListModel<String> requirement_model;
    private DefaultListModel<String> milestone_model;
    private DefaultListModel<String> hyperlink_model;

    public HabitDetailDialog(JFrame owner, phabit backend, personal_habit habit, Runnable onChange)
    {
        this.backend = backend;
        this.habit = habit;
        this.onChange = onChange;

        dialog = new JDialog(owner, "Manage Habit", true);
        dialog.setSize(640, 640);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        dialog.add(buildTop(), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 2, 8, 8));
        center.add(buildProgressPanel());
        center.add(buildRequirementsPanel());
        center.add(buildMilestonesPanel());
        center.add(buildHyperlinksPanel());
        dialog.add(center, BorderLayout.CENTER);

        dialog.add(buildBottom(), BorderLayout.SOUTH);

        refresh();
        dialog.setVisible(true);
    }

    private JPanel buildTop()
    {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JTextField name_field = new JTextField(habit.get_name(), 18);
        JButton rename_button = new JButton("Rename");

        row1.add(new JLabel("Habit ID " + habit.get_id() + " :"));
        row1.add(name_field);
        row1.add(rename_button);

        rename_button.addActionListener(e ->
        {
            String err = backend.rename_habit(habit, name_field.getText().trim());

            if(err != null)
            {
                JOptionPane.showMessageDialog(dialog, err, "Rename Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }

            dialog.setTitle("Manage Habit - " + habit.get_name());
            onChange.run();
        });

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        status_label = new JLabel();
        JButton toggle_button = new JButton("Enable / Disable");
        JLabel created_label = new JLabel("Created: " + habit.get_created());

        row2.add(status_label);
        row2.add(toggle_button);
        row2.add(created_label);

        toggle_button.addActionListener(e ->
        {
            habit.toggle_enabled();
            backend.persist();
            refresh();
            onChange.run();
        });

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        completion_label = new JLabel();
        JButton complete_button = new JButton("Record Completion");

        row3.add(completion_label);
        row3.add(complete_button);

        complete_button.addActionListener(e ->
        {
            String msg = habit.record_completion();
            backend.persist();
            refresh();
            onChange.run();
            JOptionPane.showMessageDialog(dialog, msg, "Completion", JOptionPane.INFORMATION_MESSAGE);
        });

        top.add(row1);
        top.add(row2);
        top.add(row3);
        return top;
    }

    private JPanel buildProgressPanel()
    {
        JPanel panel = titled("Progress Log");

        progress_model = new DefaultListModel<>();
        JList<String> list = new JList<>(progress_model);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JTextField field = new JTextField();
        JButton add = new JButton("Add");
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(field, BorderLayout.CENTER);
        row.add(add, BorderLayout.EAST);
        panel.add(row, BorderLayout.SOUTH);

        add.addActionListener(e ->
        {
            String text = field.getText().trim();

            if(text.isEmpty())
            {
                return;
            }

            habit.add_progress(text);
            backend.persist();
            field.setText("");
            refresh();
        });

        return panel;
    }

    private JPanel buildRequirementsPanel()
    {
        JPanel panel = titled("Requirements");

        requirement_model = new DefaultListModel<>();
        JList<String> list = new JList<>(requirement_model);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JTextField field = new JTextField();
        JButton add = new JButton("Add");
        JButton delete = new JButton("Delete Selected");

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(field, BorderLayout.CENTER);
        row.add(add, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.add(row, BorderLayout.NORTH);
        south.add(delete, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        add.addActionListener(e ->
        {
            String text = field.getText().trim();

            if(text.isEmpty())
            {
                return;
            }

            habit.add_req(text);
            backend.persist();
            field.setText("");
            refresh();
        });

        delete.addActionListener(e ->
        {
            int idx = list.getSelectedIndex();

            if(idx < 0)
            {
                JOptionPane.showMessageDialog(dialog, "Select a requirement first.");
                return;
            }

            habit.delete_req(idx + 1);
            backend.persist();
            refresh();
        });

        return panel;
    }

    private JPanel buildMilestonesPanel()
    {
        JPanel panel = titled("Milestones (completions)");

        milestone_model = new DefaultListModel<>();
        JList<String> list = new JList<>(milestone_model);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove Selected");

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(spinner, BorderLayout.CENTER);
        row.add(add, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.add(row, BorderLayout.NORTH);
        south.add(remove, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        add.addActionListener(e ->
        {
            boolean ok = habit.add_milestone((Integer) spinner.getValue());
            backend.persist();

            if(!ok)
            {
                JOptionPane.showMessageDialog(dialog, "That milestone already exists.");
            }

            refresh();
        });

        remove.addActionListener(e ->
        {
            int idx = list.getSelectedIndex();

            if(idx < 0)
            {
                JOptionPane.showMessageDialog(dialog, "Select a milestone first.");
                return;
            }

            habit.remove_milestone(Integer.parseInt(milestone_model.get(idx)));
            backend.persist();
            refresh();
        });

        return panel;
    }

    private JPanel buildHyperlinksPanel()
    {
        JPanel panel = titled("Hyperlinks");

        hyperlink_model = new DefaultListModel<>();
        JList<String> list = new JList<>(hyperlink_model);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JTextField field = new JTextField();
        JButton add = new JButton("Add");
        JButton open = new JButton("Open Selected");

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(field, BorderLayout.CENTER);
        row.add(add, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.add(row, BorderLayout.NORTH);
        south.add(open, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        add.addActionListener(e ->
        {
            String text = field.getText().trim();

            if(text.isEmpty())
            {
                return;
            }

            if(!habit.add_hyperlink(text))
            {
                JOptionPane.showMessageDialog(dialog, "Enter a valid http/https link.", "Invalid Link", JOptionPane.ERROR_MESSAGE);
                return;
            }

            backend.persist();
            field.setText("");
            refresh();
        });

        open.addActionListener(e ->
        {
            int idx = list.getSelectedIndex();

            if(idx < 0)
            {
                JOptionPane.showMessageDialog(dialog, "Select a hyperlink first.");
                return;
            }

            JOptionPane.showMessageDialog(dialog, habit.open_hyperlink(idx + 1));
        });

        return panel;
    }

    private JPanel buildBottom()
    {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        bottom.add(close);
        return bottom;
    }

    private JPanel titled(String title)
    {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private void refresh()
    {
        status_label.setText("Status: " + (habit.is_enabled() ? "Enabled" : "Disabled"));

        Integer next = habit.next_milestone();
        completion_label.setText("Completions: " + habit.get_completion_count()
            + (next == null ? "" : "  (next milestone: " + next + ")"));

        progress_model.clear();

        for(String s : habit.get_progress())
        {
            progress_model.addElement(s);
        }

        requirement_model.clear();

        for(String s : habit.get_requirements())
        {
            requirement_model.addElement(s);
        }

        milestone_model.clear();

        for(Integer m : habit.get_milestones())
        {
            milestone_model.addElement(String.valueOf(m));
        }

        hyperlink_model.clear();

        for(String s : habit.get_hyperlinks())
        {
            hyperlink_model.addElement(s);
        }
    }
}

class WellnessCheckDialog
{
    public WellnessCheckDialog(JFrame owner, phabit backend, Runnable onSaved)
    {
        JDialog dialog = new JDialog(owner, "Wellness Check", true);
        dialog.setSize(420, 340);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 10));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JSpinner mood = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
        JSpinner stress = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
        JSpinner sleep = new JSpinner(new SpinnerNumberModel(7.0, 0.0, 24.0, 0.5));
        JSpinner water = new JSpinner(new SpinnerNumberModel(4, 0, 30, 1));
        JTextField notes = new JTextField();

        form.add(new JLabel("Mood (1 low - 5 great)"));
        form.add(mood);
        form.add(new JLabel("Stress (1 calm - 5 high)"));
        form.add(stress);
        form.add(new JLabel("Sleep last night (hours)"));
        form.add(sleep);
        form.add(new JLabel("Water today (glasses)"));
        form.add(water);
        form.add(new JLabel("Notes (optional)"));
        form.add(notes);

        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        buttons.add(save);
        buttons.add(cancel);

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);

        save.addActionListener(e ->
        {
            backend.wellness_check(
                (Integer) mood.getValue(), (Integer) stress.getValue(),
                (Double) sleep.getValue(), (Integer) water.getValue(),
                notes.getText().trim());

            dialog.dispose();
            onSaved.run();
            JOptionPane.showMessageDialog(owner, motivation.inspiration(), "Saved", JOptionPane.INFORMATION_MESSAGE);
        });

        cancel.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }
}

class habit
{
    public static void main(String[] args)
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch(Exception ignored)
        {
            
        }

        SwingUtilities.invokeLater(GUIhabit::new);
    }
}
