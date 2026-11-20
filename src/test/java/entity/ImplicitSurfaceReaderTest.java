package entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplicitSurfaceReaderTest {
    private final ImplicitSurfaceReader reader = new ImplicitSurfaceReader();

    @Test
    void sphereIsLeftMinusRight() throws LatexParseException {
        ImplicitSurface s = reader.read("x^2+y^2+z^2=1");
        assertEquals(0, s.evaluate(1, 0, 0), 1e-12);
        assertEquals(-1, s.evaluate(0, 0, 0), 1e-12);
        assertEquals("x^2+y^2+z^2=1", s.getLatex());
    }

    @Test
    void planeAndProductRead() throws LatexParseException {
        assertEquals(2, reader.read("x+y+z=1").evaluate(1, 1, 1), 1e-12);
        assertEquals(0, reader.read("xyz=1").evaluate(1, 1, 1), 1e-12);
        assertEquals(0, reader.read("z^{2}=x^{2}+y^{2}").evaluate(3, 4, 5), 1e-12);
    }

    @Test
    void undefinedPointIsNaN() throws LatexParseException {
        assertTrue(Double.isNaN(reader.read("\\sqrt{x}+y+z=0").evaluate(-1, 0, 0)));
    }

    @Test
    void needsExactlyXYZAndOneEquals() {
        assertThrows(LatexParseException.class, () -> reader.read("x+y=1"));
        assertThrows(LatexParseException.class, () -> reader.read("x+y+z+w=1"));
        assertThrows(LatexParseException.class, () -> reader.read("a+b+c=1"));
        assertThrows(LatexParseException.class, () -> reader.read("x+y+z"));
        assertThrows(LatexParseException.class, () -> reader.read("x+y+z>1"));
        assertThrows(LatexParseException.class, () -> reader.read("x=y=z"));
        assertThrows(LatexParseException.class, () -> reader.read("x+y+z="));
        assertThrows(LatexParseException.class, () -> reader.read(""));
        assertThrows(LatexParseException.class, () -> reader.read(null));
    }
}
