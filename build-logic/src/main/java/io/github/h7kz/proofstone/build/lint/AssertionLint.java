package io.github.h7kz.proofstone.build.lint;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Fails a test class whose assertions are all negative (robustness R2, self-tests section 2). A negative assertion
 * checks only absence: its outermost matcher is in {@link #NEGATIVE}. A method annotated {@code @ControlRun} is exempt,
 * because a control run is where a pure absence check is meaningful. Parsed with javac's tree API, so no dependency
 * is added. examples/smoke is not linted: it holds the framework's own self-tests.
 */
public final class AssertionLint {

    /** Outermost matchers that only check absence. */
    static final Set<String> NEGATIVE = Set.of("isEmpty", "isNull", "isFalse", "isNotPresent", "doesNotContain",
            "doesNotContainKey", "noneMatch", "assertNull", "assertFalse");

    /** Static assertion roots (AssertJ and JUnit). */
    private static final Set<String> STATIC_ROOTS = Set.of("assertThat", "assertThatThrownBy", "assertThatCode",
            "assertThatExceptionOfType", "assertEquals", "assertNotEquals", "assertTrue", "assertFalse", "assertNull",
            "assertNotNull", "assertSame", "assertThrows");

    /** DualAssert entry points (ADR-0003), matched only when qualified by DualAssert. */
    private static final Set<String> DUAL_ROOTS = Set.of("item", "level");

    /** The user-facing example suites. Relative to the repository root. */
    static final List<String> TARGETS = List.of(
            "examples/bad-tests/src/test/java",
            "examples/gradle-paper-plugin/src/e2eTest/java",
            "examples/maven-paper-plugin/src/test/java");

    private AssertionLint() {
    }

    /** One assertion chain: its outermost matcher, whether it is negative, and whether its method is exempt. */
    record Assertion(String matcher, boolean negative, boolean exempt) {
    }

    /** Lints every target under {@code repoRoot}. A missing target is an error, never a silent pass. */
    public static List<String> scan(Path repoRoot) throws IOException {
        List<String> violations = new ArrayList<>();
        for (String target : TARGETS) {
            Path dir = repoRoot.resolve(target);
            if (!Files.isDirectory(dir)) {
                violations.add("assertion lint: target " + target + " is missing");
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    violations.addAll(violations(file));
                }
            }
        }
        return violations;
    }

    /** Lints one file. Each message names the file, the line and the class. */
    public static List<String> violations(Path file) throws IOException {
        return violations(file, Files.readString(file, StandardCharsets.UTF_8));
    }

    static List<String> violations(Path file, String text) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("assertion lint needs a JDK (javac), not a JRE");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null,
                List.of(new Source(file, text)));
        List<String> out = new ArrayList<>();
        Iterable<? extends CompilationUnitTree> units = task.parse();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                out.add(file + ":" + d.getLineNumber() + ": cannot parse: " + d.getMessage(null));
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        Trees trees = Trees.instance(task);
        for (CompilationUnitTree unit : units) {
            for (var type : unit.getTypeDecls()) {
                if (!(type instanceof ClassTree cls)) {
                    continue;
                }
                Collector collector = new Collector(trees, unit);
                collector.scan(cls, null);
                List<Assertion> counted = collector.found.stream().filter(a -> !a.exempt()).toList();
                if (!counted.isEmpty() && counted.stream().allMatch(Assertion::negative)) {
                    long line = unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, cls));
                    out.add(file + ":" + line + ": " + cls.getSimpleName() + " has only negative assertions ("
                            + counted.stream().map(Assertion::matcher).distinct().sorted().toList()
                            + "); add a positive check or mark the method @ControlRun(...)");
                }
            }
        }
        return out;
    }

    /** Walks a class and records one {@link Assertion} per assertion chain. */
    private static final class Collector extends TreeScanner<Void, Void> {
        final List<Assertion> found = new ArrayList<>();
        private final Trees trees;
        private final CompilationUnitTree unit;
        private boolean controlRun;

        Collector(Trees trees, CompilationUnitTree unit) {
            this.trees = trees;
            this.unit = unit;
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            boolean outer = controlRun;
            controlRun = outer || hasControlRun(node);
            try {
                return super.visitMethod(node, p);
            } finally {
                controlRun = outer;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
            // Walk the chain outermost-first: a.b().c() gives [c, b] and the innermost receiver.
            List<String> names = new ArrayList<>();
            List<ExpressionTree> arguments = new ArrayList<>();
            ExpressionTree current = node;
            String qualifier = null;
            while (current instanceof MethodInvocationTree call) {
                arguments.addAll(call.getArguments());
                ExpressionTree select = call.getMethodSelect();
                if (select instanceof MemberSelectTree member) {
                    names.add(member.getIdentifier().toString());
                    qualifier = member.getExpression().toString();
                    current = member.getExpression();
                } else if (select instanceof IdentifierTree id) {
                    names.add(id.getName().toString());
                    qualifier = null;
                    current = null;
                    break;
                } else {
                    current = null;
                    break;
                }
            }
            if (!names.isEmpty() && isAssertion(names.get(names.size() - 1), qualifier)) {
                String matcher = names.get(0);
                found.add(new Assertion(matcher, NEGATIVE.contains(matcher), controlRun));
            }
            // Arguments and non-call receivers can hold further assertions (lambdas, nested expressions).
            if (current != null) {
                scan(current, p);
            }
            for (ExpressionTree argument : arguments) {
                scan(argument, p);
            }
            return null;
        }

        private static boolean isAssertion(String root, String qualifier) {
            return STATIC_ROOTS.contains(root) || (DUAL_ROOTS.contains(root) && "DualAssert".equals(qualifier));
        }

        private static boolean hasControlRun(MethodTree method) {
            for (AnnotationTree annotation : method.getModifiers().getAnnotations()) {
                String type = annotation.getAnnotationType().toString();
                if (type.equals("ControlRun") || type.endsWith(".ControlRun")) {
                    return true;
                }
            }
            return false;
        }
    }

    /** An in-memory Java source, so the file name and extension play no part in parsing. */
    private static final class Source extends SimpleJavaFileObject {
        private final String text;

        Source(Path file, String text) {
            super(file.toUri(), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
