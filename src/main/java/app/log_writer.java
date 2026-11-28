package app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Writes log entries to a file on disk using {@code java.util.logging}.
 * The log file is opened in append mode, so existing entries are kept.
 */
public class log_writer {
    private final Logger logger;
    private final FileHandler fileHandler;

    /**
     * Opens (or appends to) the log file at {@code log_path}.
     * Missing parent directories are created automatically, and a
     * {@code .log} suffix is appended when the file name lacks one.
     *
     * @param log_path path of the log file to write to
     * @throws IOException if the log file cannot be opened or created
     */
    public log_writer(String log_path) throws IOException {
        Path path = Path.of(log_path).toAbsolutePath();
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".log")) {
            path = path.resolveSibling(fileName + ".log");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        fileHandler = new FileHandler(path.toString(), true);
        fileHandler.setFormatter(new SimpleFormatter());

        logger = Logger.getLogger(log_writer.class.getName());
        logger.setUseParentHandlers(false);
        logger.addHandler(fileHandler);
        logger.setLevel(Level.ALL);
    }

    /**
     * Appends an informational log entry to the log file.
     *
     * @param message the message to log
     */
    public void log(String message) {
        logger.info(message);
    }

    /**
     * Appends a log entry at the given level.
     *
     * @param level   the log level (for example {@link Level#INFO} or {@link Level#SEVERE})
     * @param message the message to log
     */
    public void log(Level level, String message) {
        logger.log(level, message);
    }

    /**
     * Appends a log entry with an associated exception.
     *
     * @param level   the log level
     * @param message the message to log
     * @param thrown  the exception to attach to the entry
     */
    public void log(Level level, String message, Throwable thrown) {
        logger.log(level, message, thrown);
    }

    /**
     * Flushes and releases the underlying file handler.
     */
    public void close() {
        fileHandler.close();
    }
}
