package pdf_editor.adapter;

/**
 * Plain data copy of one canvas formula and its answer, as {@link CanvasAnswerRecovery} needs it.
 * Lets this adapter read the canvas without depending on the view's own formula classes.
 *
 * @param id       the formula's id on the canvas
 * @param revision changes whenever the formula's ink is edited
 * @param answered whether an answer is shown next to it
 * @param error    whether the shown answer is an error
 * @param latex    typeset LaTeX of the answer, or "" when it is plain text
 * @param text     readable text of the answer, or "" when there is none
 */
public record CanvasFormulaAnswer(long id, long revision, boolean answered, boolean error,
                                  String latex, String text) {

    public CanvasFormulaAnswer {
        latex = latex == null ? "" : latex;
        text = text == null ? "" : text;
    }

    /** A formula with no answer yet. */
    public static CanvasFormulaAnswer unanswered(long id, long revision) {
        return new CanvasFormulaAnswer(id, revision, false, false, "", "");
    }
}
