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

    private void writeNode(FileWriter writer, TreeNode node, int depth) throws IOException {
        String indent = INDENT.repeat(depth);

        if (node.isTerminal()) {
           
            writer.write(indent + "<node>\n");
            writer.write(indent + INDENT + "<id>" + node.getNodeId() + "</id>\n");
            writer.write(indent + INDENT + "<contents>" + escapeXml(node.getValue()) + "</contents>\n");
            if (node.getParent() != null) {
                writer.write(indent + INDENT + "<parent>" + node.getParent().getNodeId() + "</parent>\n");
            }
            writer.write(indent + "</node>\n");
        } else {
            
            writer.write(indent + "<node>\n");
            writer.write(indent + INDENT + "<id>" + node.getNodeId() + "</id>\n");
            writer.write(indent + INDENT + "<contents>" + escapeXml(node.getValue()) + "</contents>\n");
            if (!node.getChildren().isEmpty()) {
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
