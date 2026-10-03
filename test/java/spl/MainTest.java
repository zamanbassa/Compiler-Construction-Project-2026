package spl;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/** End-to-end tests for the compiler entry point. */
public final class MainTest {
    private MainTest() {
    }

    public static void main(String[] args) throws Exception {
        testCustomInputAndOutputPaths();
        System.out.println("All Main tests passed.");
    }

    private static void testCustomInputAndOutputPaths() throws Exception {
        Path input = Files.createTempFile("spl-main-test-", ".spl");
        Path output = Files.createTempFile("spl-main-test-", ".xml");
        try {
            Files.writeString(input, "#x : : print \"hello\" ; ");
            Files.deleteIfExists(output);

            Main.main(new String[] {input.toString(), output.toString()});

            assert Files.exists(output) : "Custom XML output was not created";
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(output.toFile());
            assert "tree".equals(document.getDocumentElement().getTagName());
            assert document.getElementsByTagName("node").getLength() > 1;
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }
}
