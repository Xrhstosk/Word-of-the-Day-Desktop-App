import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 1. Μοντέλο Δεδομένων
class Word {
    private String englishWord;
    private String greekTranslation;
    private String phonetic;
    private String definition;
    private String example;

    public Word(String englishWord, String greekTranslation, String phonetic, String definition, String example) {
        this.englishWord = englishWord;
        this.greekTranslation = greekTranslation;
        this.phonetic = phonetic;
        this.definition = definition;
        this.example = example;
    }

    public String getEnglishWord() { return englishWord; }
    public String getGreekTranslation() { return greekTranslation; }
    public String getPhonetic() { return phonetic; }
    public String getDefinition() { return definition; }
    public String getExample() { return example; }
}

// 2. Υπηρεσία Δεδομένων αποκλειστικά μέσω Online APIs
class WordService {
    private HttpClient client;

    public WordService() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public Word fetchOnlineRandomWord() {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                // Βήμα 1: Λήψη τυχαίας λέξης από το online Datamuse API (ταχύτατο και σταθερό)
                char randomLetter = (char) ('a' + (int) (Math.random() * 26));
                String randomApiUrl = "https://api.datamuse.com/words?sp=" + randomLetter + "?????&max=30";
                
                HttpRequest randReq = HttpRequest.newBuilder()
                        .uri(URI.create(randomApiUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> randRes = client.send(randReq, HttpResponse.BodyHandlers.ofString());
                String rawWord = extractRandomWordFromDatamuse(randRes.body());

                if (rawWord.isEmpty()) continue;

                // Βήμα 2: Λήψη ορισμού & φωνητικής από το Free Dictionary API
                HttpRequest dictReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.dictionaryapi.dev/api/v2/entries/en/" + rawWord))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> dictRes = client.send(dictReq, HttpResponse.BodyHandlers.ofString());

                if (dictRes.statusCode() == 200) {
                    String json = dictRes.body();
                    String definition = extractJsonValue(json, "definition");
                    String example = extractJsonValue(json, "example");
                    String phonetic = extractJsonValue(json, "text");

                    if (definition.isEmpty()) definition = "Δεν βρέθηκε αναλυτικός ορισμός στο online λεξικό.";
                    if (example.isEmpty()) example = "Δεν υπάρχει διαθέσιμο παράδειγμα χρήσης.";
                    if (phonetic.isEmpty()) phonetic = "/" + rawWord + "/";

                    // Βήμα 3: Online Μετάφραση στα Ελληνικά (MyMemory API: en -> el)
                    String encodedWord = URLEncoder.encode(rawWord, StandardCharsets.UTF_8);
                    String transUrl = "https://api.mymemory.translated.net/get?q=" + encodedWord + "&langpair=en%7Cel";

                    HttpRequest transReq = HttpRequest.newBuilder()
                            .uri(URI.create(transUrl))
                            .header("User-Agent", "Mozilla/5.0")
                            .timeout(Duration.ofSeconds(6))
                            .GET()
                            .build();

                    HttpResponse<String> transRes = client.send(transReq, HttpResponse.BodyHandlers.ofString());
                    String greekRaw = extractJsonValue(transRes.body(), "translatedText");
                    String greekTranslation = decodeUnicodeEscapes(greekRaw);

                    if (greekTranslation.isEmpty()) {
                        greekTranslation = "-";
                    }

                    String formattedEnglish = rawWord.substring(0, 1).toUpperCase() + rawWord.substring(1);
                    return new Word(formattedEnglish, greekTranslation, phonetic, definition, example);
                }
            } catch (Exception e) {
                // Σε σφάλμα δοκιμάζει την επόμενη προσπάθεια
            }
        }
        return new Word("Offline", "Εκτός σύνδεσης", "/ˈɒf.laɪn/", "Δεν ήταν δυνατή η σύνδεση στα online APIs.", "Ελέγξτε τη σύνδεσή σας στο διαδίκτυο.");
    }

    private String extractRandomWordFromDatamuse(String json) {
        Pattern pattern = Pattern.compile("\"word\"\\s*:\\s*\"([a-zA-Z]+)\"");
        Matcher matcher = pattern.matcher(json);
        java.util.List<String> list = new java.util.ArrayList<>();
        while (matcher.find()) {
            list.add(matcher.group(1));
        }
        if (!list.isEmpty()) {
            return list.get((int) (Math.random() * list.size()));
        }
        return "";
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"");
        }
        return "";
    }

    private String decodeUnicodeEscapes(String input) {
        if (input == null || input.isEmpty()) return "";
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int hexVal = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(Character.toString((char) hexVal)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

// 3. Γραφικό Περιβάλλον (GUI)
public class WordOfTheDayApp extends JFrame {

    private WordService wordService;
    private Word currentWord;
    private JLabel englishWordLabel;
    private JLabel greekTranslationLabel;
    private JLabel phoneticLabel;
    private JTextArea definitionArea;
    private JTextArea exampleArea;
    private JButton nextWordBtn;
    private JButton playAudioBtn;

    public WordOfTheDayApp() {
        wordService = new WordService();

        setTitle("Λέξη της Ημέρας");
        setSize(540, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // Top Panel
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        JLabel appTitle = new JLabel("✨ Λέξη της Ημέρας ✨", SwingConstants.CENTER);
        appTitle.setFont(new Font("SansSerif", Font.BOLD, 22));

        String todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        JLabel dateLabel = new JLabel("Σήμερα: " + todayFormatted, SwingConstants.CENTER);
        dateLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        dateLabel.setForeground(Color.GRAY);

        topPanel.add(appTitle);
        topPanel.add(dateLabel);
        add(topPanel, BorderLayout.NORTH);

        // Center Card Panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 20, 10, 20),
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1, true)
        ));
        contentPanel.setBackground(new Color(250, 250, 250));

        englishWordLabel = new JLabel("Φόρτωση...", SwingConstants.CENTER);
        englishWordLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        englishWordLabel.setForeground(new Color(20, 75, 150));
        englishWordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        greekTranslationLabel = new JLabel("", SwingConstants.CENTER);
        greekTranslationLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        greekTranslationLabel.setForeground(new Color(34, 139, 34));
        greekTranslationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        phoneticLabel = new JLabel("", SwingConstants.CENTER);
        phoneticLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        phoneticLabel.setForeground(new Color(110, 110, 110));
        phoneticLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel defHeader = new JLabel("Ορισμός (Online EN):");
        defHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        defHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        definitionArea = createStyledTextArea();

        JLabel exHeader = new JLabel("Παράδειγμα:");
        exHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        exHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        exampleArea = createStyledTextArea();

        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(englishWordLabel);
        contentPanel.add(greekTranslationLabel);
        contentPanel.add(phoneticLabel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(defHeader);
        contentPanel.add(definitionArea);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(exHeader);
        contentPanel.add(exampleArea);
        contentPanel.add(Box.createVerticalStrut(5));

        add(contentPanel, BorderLayout.CENTER);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        playAudioBtn = new JButton("🔊 Προφορά");
        playAudioBtn.setFocusPainted(false);
        playAudioBtn.addActionListener(e -> speakWord());

        nextWordBtn = new JButton("🎲 Τυχαία Λέξη (Online API)");
        nextWordBtn.setFocusPainted(false);
        nextWordBtn.addActionListener(e -> loadNextRandomWordAsync());

        bottomPanel.add(playAudioBtn);
        bottomPanel.add(nextWordBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        loadNextRandomWordAsync();
    }

    private void displayWord(Word word) {
        if (word != null) {
            this.currentWord = word;
            englishWordLabel.setText(word.getEnglishWord());
            greekTranslationLabel.setText("🇬🇷 " + word.getGreekTranslation());
            phoneticLabel.setText(word.getPhonetic());
            definitionArea.setText(word.getDefinition());
            exampleArea.setText("\"" + word.getExample() + "\"");
        }
    }

    private void loadNextRandomWordAsync() {
        nextWordBtn.setEnabled(false);
        nextWordBtn.setText("⏳ Φόρτωση...");

        new Thread(() -> {
            Word w = wordService.fetchOnlineRandomWord();
            SwingUtilities.invokeLater(() -> {
                displayWord(w);
                nextWordBtn.setText("🎲 Τυχαία Λέξη (Online API)");
                nextWordBtn.setEnabled(true);
            });
        }).start();
    }

    private void speakWord() {
        if (currentWord == null || currentWord.getEnglishWord().equals("Offline")) return;
        new Thread(() -> {
            try {
                String textToSpeak = currentWord.getEnglishWord();
                String command = "Add-Type -AssemblyName System.Speech; " +
                        "(New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('" + textToSpeak + "');";
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
                pb.start();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private JTextArea createStyledTextArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setAlignmentX(Component.CENTER_ALIGNMENT);
        area.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        return area;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WordOfTheDayApp app = new WordOfTheDayApp();
            app.setVisible(true);
        });
    }
}