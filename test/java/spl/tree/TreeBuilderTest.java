package spl.tree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.parser.Parser;

/** XML and end-to-end lexer-parser-tree-writer tests. */
public final class TreeBuilderTest {
    private TreeBuilderTest() {
    }

    public static void main(String[] args) throws Exception {
        testSimpleTree();
        testComplexTree();
        testParsedProgramPipeline();
        System.out.println("All XML tests passed.");
    }

    private static void testSimpleTree() throws Exception {
        TreeNode.resetIdCounter();

        TreeNode root = new TreeNode("SPL_PROG");
        TreeNode print = new TreeNode("print", true);
        root.addChild(print);

        Path output = writeAndParse(root);
        try {
            Document document = parseXml(output);
            assert "tree".equals(document.getDocumentElement().getTagName());
            assert document.getElementsByTagName("node").getLength() == 2;
            assert document.getElementsByTagName("contents").item(0).getTextContent()
                    .equals("SPL_PROG");
            assert document.getElementsByTagName("contents").item(1).getTextContent()
                    .equals("print");
            assert root.getChildrenIds().contains(print.getNodeId());
            assert print.getParent() == root;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void testComplexTree() throws Exception {
        TreeNode.resetIdCounter();

        TreeNode root = new TreeNode("SPL_PROG");
        TreeNode declarations = new TreeNode("V_DECL");
        TreeNode variable = new TreeNode("#x_", true);
        TreeNode algorithm = new TreeNode("ALGO");
        TreeNode instruction = new TreeNode("INSTR");
        TreeNode print = new TreeNode("print", true);

        declarations.addChild(variable);
        instruction.addChild(print);
        algorithm.addChild(instruction);
        root.addChild(declarations);
        root.addChild(algorithm);

        Path output = writeAndParse(root);
        try {
            Document document = parseXml(output);
            assert document.getElementsByTagName("node").getLength() == 6;
            assert variable.getParent() == declarations;
            assert instruction.getParent() == algorithm;
            assert root.getChildren().get(0) == declarations;
            assert root.getChildren().get(1) == algorithm;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void testParsedProgramPipeline() throws Exception {
        TreeNode.resetIdCounter();
        String source = ": : print \"hello\" ; ";

        List<Token> tokens = new Lexer().tokenize(source);
        TreeNode root = new Parser().parse(tokens);
        assert "SPL_PROG".equals(root.getValue());

        Path output = writeAndParse(root);
        try {
            Document document = parseXml(output);
            NodeList contents = document.getElementsByTagName("contents");
            assert containsContents(contents, "SPL_PROG");
            assert containsContents(contents, "print");
            assert containsContents(contents, "&quot;hello&quot;")
                    || containsContents(contents, "\"hello\"");
            assert document.getElementsByTagName("node").getLength() > 1;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static Path writeAndParse(TreeNode root) throws Exception {
        Path output = Files.createTempFile("spl-tree-test-", ".xml");
        TreeBuilder builder = new TreeBuilder();
        builder.setRoot(root);
        builder.buildTree(output.toString());
        assert Files.exists(output) : "XML output was not created";
        return output;
    }

    private static Document parseXml(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(path.toFile());
    }

    private static boolean containsContents(NodeList nodes, String expected) {
        for (int i = 0; i < nodes.getLength(); i++) {
            if (expected.equals(nodes.item(i).getTextContent())) {
                return true;
            }
        }
        return false;
    }
}
