package spl.tree;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class XMLWriter {
    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String INDENT = "  ";

    public void write(TreeNode root, String outputPath) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(XML_DECLARATION);
            writer.write("<tree>\n");
            writeNode(writer, root, 1);
            writer.write("</tree>\n");
        } catch (IOException e) {
            System.err.println("Error writing XML to " + outputPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeNode(FileWriter writer, TreeNode node, int depth) throws IOException {}
    
}
