# Word-of-the-Day-Desktop-App
Δημιουργία αυτόνομης desktop εφαρμογής για ανάκτηση λεξιλογίου, ορισμών και φωνητικής εκφώνησης
Πρόκειται για μια αυτόνομη desktop εφαρμογή σε Java (JDK 17+) η οποία ανακτά σε πραγματικό χρόνο αγγλικό λεξιλόγιο, ορισμούς, φωνητική μεταγραφή και ελληνική μετάφραση, με υποστήριξη φωνητικής εκφώνησης (TTS).

Βιβλιοθήκες & Τεχνολογίες (Zero External Dependencies):

GUI & Layout: javax.swing & java.awt για το γραφικό περιβάλλον (JFrame, BoxLayout, BorderLayout).

Networking: java.net.http.HttpClient (Java 11+) για ασύγχρονα/σύγχρονα GET αιτήματα με παραμετροποιημένα timeouts και custom User-Agent headers.

Data Parsing: java.util.regex (Pattern, Matcher) για lightweight εξαγωγή πεδίων από JSON payloads χωρίς εξωτερικά parsers (π.χ. Jackson/Gson), καθώς και χειροκίνητο decoding των Unicode escape sequences (\uXXXX) σε UTF-8.

Concurrency: Χειροκίνητο multithreading (java.lang.Thread) για τα network I/O requests ώστε να μην μπλοκάρει το Event Dispatch Thread (EDT), με ασφαλή ενημέρωση του UI μέσω SwingUtilities.invokeLater().

OS Integration & Packaging: java.lang.ProcessBuilder για κλήση του System.Speech.Synthesis.SpeechSynthesizer μέσω PowerShell (TTS), και πακετάρισμα με jar και jpackage σε αυτόνομο Windows app-image (.exe με ενσωματωμένο JRE).

Ροή Εκτέλεσης (API Pipeline):

Datamuse API: Αίτημα με τυχαίο αρχικό γράμμα για ανάκτηση έγκυρης αγγλικής λέξης.

Free Dictionary API: Κλήση για άντληση του ορισμού, παραδείγματος χρήσης και φωνητικής (IPA).

MyMemory Translation API: Μετάφραση της λέξης (langpair=en|el) και αποκωδικοποίηση των ελληνικών χαρακτήρων πριν την εμφάνιση στο UI.
