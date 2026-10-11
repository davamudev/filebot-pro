package dev.davamu.filebotpro.ui.rename;

import static java.awt.Font.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Font;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import dev.davamu.filebotpro.ui.theme.ThemeManager;
import dev.davamu.filebotpro.util.ui.LazyDocumentListener;

public class FormatExpressionTextArea extends RSyntaxTextArea {

	public FormatExpressionTextArea() {
		this(new RSyntaxDocument(new FormatExpressionTokenMakerFactory(), FormatExpressionTokenMakerFactory.SYNTAX_STYLE_GROOVY_FORMAT_EXPRESSION));
	}

	public FormatExpressionTextArea(RSyntaxDocument syntaxDocument) {
		super(syntaxDocument, "", 1, 80);

		applyTheme();

		setAntiAliasingEnabled(true);
		setAnimateBracketMatching(true);
		setAutoIndentEnabled(true);
		setBracketMatchingEnabled(true);
		setCloseCurlyBraces(true);
		setCodeFoldingEnabled(false);
		setHyperlinksEnabled(false);
		setUseFocusableTips(false);
		setClearWhitespaceLinesEnabled(false);
		setHighlightCurrentLine(false);
		setHighlightSecondaryLanguages(false);
		setLineWrap(false);
		setMarkOccurrences(false);
		setPaintMarkOccurrencesBorder(false);
		setPaintTabLines(false);

		// dynamically resize the code editor depending on how many lines the current format expression has
		getDocument().addDocumentListener(new LazyDocumentListener(0, evt -> {
			int r1 = getRows();
			int r2 = (int) getText().chars().filter(c -> c == '\n').count() + 1;
			if (r1 != r2) {
				setRows(r2);
				getWindow(FormatExpressionTextArea.this).revalidate();
			}
		}));
	}

	private final Runnable themeListener = this::applyTheme;

	@Override
	public void addNotify() {
		super.addNotify();
		ThemeManager.addThemeListener(themeListener);
	}

	@Override
	public void removeNotify() {
		ThemeManager.removeThemeListener(themeListener);
		super.removeNotify();
	}

	/**
	 * Apply the light or dark syntax highlighting theme and the editor font.
	 */
	protected void applyTheme() {
		try {
			Theme.load(openTheme()).apply(this);
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		Font monospaced = UIManager.getFont("monospaced.font");
		setFont(monospaced != null ? monospaced.deriveFont(PLAIN, 14f) : new Font(MONOSPACED, PLAIN, 14));
	}

	protected InputStream openTheme() {
		if (isDark()) {
			return FormatExpressionTextArea.class.getResourceAsStream("FormatExpressionTextArea.Theme.Dark.xml");
		} else {
			return FormatExpressionTextArea.class.getResourceAsStream("FormatExpressionTextArea.Theme.xml");
		}
	}

	/**
	 * Load the editor classes and parse the syntax theme ahead of time (off the EDT), so the first editor doesn't block the UI.
	 */
	public static void warmup() throws Exception {
		Class.forName(RSyntaxTextArea.class.getName());
		Class.forName(RSyntaxDocument.class.getName());
		Class.forName(FormatExpressionTokenMaker.class.getName());
		Class.forName(RTextScrollPane.class.getName());

		try (InputStream theme = FormatExpressionTextArea.class.getResourceAsStream(isDark() ? "FormatExpressionTextArea.Theme.Dark.xml" : "FormatExpressionTextArea.Theme.xml")) {
			Theme.load(theme);
		}
	}

	public void onChange(Consumer<DocumentEvent> handler) {
		getDocument().addDocumentListener(new LazyDocumentListener(handler));
	}

	public void onChange(int delay, Consumer<DocumentEvent> handler) {
		getDocument().addDocumentListener(new LazyDocumentListener(delay, handler));
	}

}
