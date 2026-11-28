import app.StackedAppBuilder;
import data_access.VitalMiniFormulaRecognizer;
import model.vital_mini;
import app.log_writer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public class app {
    // relative to the project root, which is where the app runs from (like the tokeniser path in vital_mini)
    public final vital_mini model = new vital_mini(Path.of("src", "main", "java", "model", "checkpoints", "model_ops.pt"));
    JFrame frame;
    public log_writer writer;

    void init() throws Exception {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String time = LocalDateTime.now().format(formatter);
        Path log_path =  Path.of("","logs",time).toAbsolutePath() ;
        writer = new log_writer(log_path.toString());
        writer.log(Level.INFO, String.join("penTex starts running at" , time));
        model.init();
        writer.log(Level.INFO, "Model init complete");
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default look.
            }
            view.glass.AppleLook.install();  // same Apple-style controls on mac + windows
            frame = new StackedAppBuilder()
                    .setFormulaRecognizer(new VitalMiniFormulaRecognizer(model))
                    .build();
            // Not windowClosing: the user may cancel the close from the unsaved-changes prompt.
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    model.close();
                }
            });
        });
    }

    void run() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public static void main(String[] args) {
        app application = new app();
        try {
            application.init();
            application.run();
        } catch (Exception e) {
            application.model.close();
            System.err.println("Application failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
