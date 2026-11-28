package view.canvas;

// what the popup said last for a formula
final class Read {
    final long revision;
    final String latex;
    final String problem;

    Read(long revision, String latex, String problem) {
        this.revision = revision;
        this.latex = latex;
        this.problem = problem;
    }
}
