package pdf_editor.use_case;

import pdf_editor.domain.PdfMaterial;
import pdf_editor.domain.PngImage;


/**
 * Output port of the selection use cases: where chosen formulas, results and ink are kept for
 * PDF placement. The in-memory material store implements it, so the use case does not depend on
 * that concrete class (Dependency Inversion).
 */
public interface MaterialArchive {

    PdfMaterial addFormula(String latex);

    PdfMaterial addCalculation(String latex, String result);

    PdfMaterial addInk(String title, PngImage image);
}
