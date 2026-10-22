package spl.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import spl.tree.TreeNode;

/** One explicitly requested target output. Reject aliases and invalidate stale artifacts first. */
public final class TargetFileOutput {
    private final Path target;
    public TargetFileOutput(Path source, Path xml, Path target) throws IOException {
        this.target = target.toAbsolutePath().normalize();
        if (same(source, xml) || same(source, target) || same(xml, target))
            throw new CodeGenerationException("source, XML and target paths must be different");
        // A failed compilation must not leave a previous fragment looking current.
        if (Files.isDirectory(this.target)) throw new IOException("target output is a directory: " + target);
        Files.deleteIfExists(this.target);
    }
    private static boolean same(Path a, Path b) throws IOException {
        if (a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize())) return true;
        return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
    }
    public static TreeNode mainAlgorithm(CodeGenerationInput input) {
        TreeNode program = input.getTree();
        if ("SPL_PROG".equals(program.getValue()) && program.getChildren().size() == 1)
            program = program.getChildren().get(0);
        if (!"P".equals(program.getValue())) throw new CodeGenerationException(program, "expected program P");
        TreeNode algo = null;
        for (TreeNode child : program.getChildren()) if ("ALGO".equals(child.getValue())) {
            if (algo != null) throw new CodeGenerationException(program, "multiple main algorithms");
            algo = child;
        }
        if (algo == null) throw new CodeGenerationException(program, "missing main ALGO");
        return algo;
    }
    public void write(CodeGenerationInput input) throws IOException {
        String translationUnit = new ProgramCodeGenerator(input).generate();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".spl-target-", ".tmp");
        try {
            Files.writeString(temporary, translationUnit, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
