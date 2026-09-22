package spl.tree;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Serializes a syntax tree into the inspectable tree.xml representation. */
public class XMLWriter {
    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String INDENT = "  ";

    public void write(TreeNode root, String outputPath) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("Output path cannot be blank");
        }

        Path path = Path.of(outputPath);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(XML_DECLARATION);
                writer.write("<tree>\n");
                writeNode(writer, root, 1);
                writer.write("</tree>\n");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write XML to " + path, exception);
        }
    }

    private void writeNode(BufferedWriter writer, TreeNode node, int depth) throws IOException {
        String indent = INDENT.repeat(depth);

        writer.write(indent + "<node>\n");
        writer.write(indent + INDENT + "<id>" + node.getNodeId() + "</id>\n");
        writer.write(indent + INDENT + "<contents>" + escapeXml(node.getValue()) + "</contents>\n");

        if (!node.isTerminal() && !node.getChildren().isEmpty()) {
            writer.write(indent + INDENT + "<children>\n");
            List<Integer> childrenIds = node.getChildrenIds();
            for (Integer childId : childrenIds) {
                writer.write(indent + INDENT + INDENT + "<child>" + childId + "</child>\n");
            }
            writer.write(indent + INDENT + "</children>\n");
        }

        if (node.getParent() != null) {
            writer.write(indent + INDENT + "<parent>" + node.getParent().getNodeId() + "</parent>\n");
        }

        writer.write(indent + "</node>\n");

        for (TreeNode child : node.getChildren()) {
            writeNode(writer, child, depth + 1);
        }
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
