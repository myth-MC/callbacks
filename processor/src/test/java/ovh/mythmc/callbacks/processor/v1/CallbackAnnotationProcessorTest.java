package ovh.mythmc.callbacks.processor.v1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

final class CallbackAnnotationProcessorTest {

    @Test
    void generatesTopLevelClassForNestedType() throws IOException {
        var result = compile(
                "sample.CUserPresence",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.Callback;

                interface CUserPresence {
                    @Callback
                    final class Join {
                        public Join() {}
                    }
                }
                """);

        assertTrue(result.success(), formatDiagnostics(result.diagnostics()));

        Path generatedCallback = result.generatedSources().resolve("sample/JoinCallback.java");
        assertTrue(Files.exists(generatedCallback), "expected generated source file: " + generatedCallback);

        String generatedSource = Files.readString(generatedCallback);
        assertTrue(
                generatedSource.contains("public static final JoinCallback INSTANCE = new JoinCallback();"),
                generatedSource);
        assertFalse(generatedSource.contains("CUserPresence.JoinCallback INSTANCE"), generatedSource);
    }

    @Test
    void rejectsAbstractCallbackType() {
        var result = compile(
                "sample.AbstractCallback",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.Callback;

                @Callback
                abstract class AbstractCallback {}
                """);

        assertFalse(result.success(), "compilation unexpectedly succeeded");
        assertTrue(
                formatDiagnostics(result.diagnostics()).contains("@interface ovh.mythmc.callbacks.annotations.v1.Callback cannot be used in abstract classes"),
                formatDiagnostics(result.diagnostics()));
    }

    @Test
    void rejectsMissingCallbackFieldGetter() {
        var result = compile(
                "sample.WithMissingGetter",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.CallbackField;

                @CallbackField(field = "value", getter = "missing()")
                final class WithMissingGetter {
                    private final String value = "x";
                }
                """);

        assertFalse(result.success(), "compilation unexpectedly succeeded");
        assertTrue(
                formatDiagnostics(result.diagnostics()).contains("Method 'missing()' does not exist"),
                formatDiagnostics(result.diagnostics()));
    }

    @Test
    void includesExtraParametersInGeneratedListenerSignature() throws IOException {
        var result = compile(
                "sample.PlayerJoin",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.Callback;
                import ovh.mythmc.callbacks.annotations.v1.CallbackField;

                @Callback
                @CallbackField(field = "username", getter = "getUsername()")
                @CallbackField(field = "extra", getter = "getExtra()", isExtraParameter = true)
                final class PlayerJoin {
                    private final String username;
                    private final int extra;

                    public PlayerJoin(String username) {
                        this.username = username;
                        this.extra = 7;
                    }

                    public String getUsername() {
                        return username;
                    }

                    public int getExtra() {
                        return extra;
                    }
                }
                """);

        assertTrue(result.success(), formatDiagnostics(result.diagnostics()));

        Path generatedCallback = result.generatedSources().resolve("sample/PlayerJoinCallback.java");
        assertTrue(Files.exists(generatedCallback), "expected generated source file: " + generatedCallback);

        String generatedSource = Files.readString(generatedCallback);
        assertTrue(generatedSource.contains("void trigger(String username, int extra);"), generatedSource);
        assertTrue(generatedSource.contains("listener.trigger(callback.getUsername(), callback.getExtra())"), generatedSource);
    }

    @Test
    void generatesRecordAccessorsInListenerInvocation() throws IOException {
        var result = compile(
                "sample.RecordJoin",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.Callback;

                @Callback
                record RecordJoin(String username, int level) {}
                """);

        assertTrue(result.success(), formatDiagnostics(result.diagnostics()));

        Path generatedCallback = result.generatedSources().resolve("sample/RecordJoinCallback.java");
        assertTrue(Files.exists(generatedCallback), "expected generated source file: " + generatedCallback);

        String generatedSource = Files.readString(generatedCallback);
        assertTrue(generatedSource.contains("void trigger(String username, int level);"), generatedSource);
        assertTrue(generatedSource.contains("listener.trigger(callback.username(), callback.level())"), generatedSource);
    }

    @Test
    void generatesParameterizedTypesForGenericCallbacks() throws IOException {
        var result = compile(
                "sample.GenericCallback",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.Callback;
                import ovh.mythmc.callbacks.annotations.v1.CallbackField;

                @Callback
                @CallbackField(field = "value", getter = "value()")
                final class GenericCallback<T> {
                    private final T value;

                    GenericCallback(T value) {
                        this.value = value;
                    }

                    T value() {
                        return value;
                    }
                }
                """);

        assertTrue(result.success(), formatDiagnostics(result.diagnostics()));

        Path generatedCallback = result.generatedSources().resolve("sample/GenericCallbackCallback.java");
        assertTrue(Files.exists(generatedCallback), "expected generated source file: " + generatedCallback);

        String generatedSource = Files.readString(generatedCallback);
        assertTrue(generatedSource.contains("public interface GenericCallbackCallbackHandler<T>"), generatedSource);
        assertTrue(generatedSource.contains("void handle(GenericCallback<T> callback);"), generatedSource);
    }

    @Test
    void usesSelectedConstructorIndexForListenerSignature() throws IOException {
        var result = compile(
                "sample.MultipleConstructors",
                """
                package sample;

                import ovh.mythmc.callbacks.annotations.v1.Callback;
                import ovh.mythmc.callbacks.annotations.v1.CallbackField;

                @Callback(constructor = 2)
                @CallbackField(field = "username", getter = "getUsername()")
                final class MultipleConstructors {
                    private final String username;
                    int id;
                    boolean admin;

                    MultipleConstructors() {
                        this.username = "guest";
                        this.id = 0;
                        this.admin = false;
                    }

                    MultipleConstructors(int id, boolean admin) {
                        this.username = id + ":" + admin;
                        this.id = id;
                        this.admin = admin;
                    }

                    String getUsername() {
                        return username;
                    }
                }
                """);

        assertTrue(result.success(), formatDiagnostics(result.diagnostics()));

        Path generatedCallback = result.generatedSources().resolve("sample/MultipleConstructorsCallback.java");
        assertTrue(Files.exists(generatedCallback), "expected generated source file: " + generatedCallback);

        String generatedSource = Files.readString(generatedCallback);
        assertTrue(generatedSource.contains("void trigger(int id, boolean admin);"), generatedSource);
        assertTrue(generatedSource.contains("listener.trigger(callback.id, callback.admin)"), generatedSource);
    }

    private static CompilationResult compile(String qualifiedName, String source) {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler, "No system Java compiler available");

            Path rootDir = Files.createTempDirectory("callback-processor-test");
            Path classesDir = rootDir.resolve("classes");
            Path generatedSourcesDir = rootDir.resolve("generated");
            Files.createDirectories(classesDir);
            Files.createDirectories(generatedSourcesDir);

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(generatedSourcesDir.toFile()));

            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-proc:full");

            JavaFileObject sourceFile = new InMemorySourceFile(qualifiedName, source);
            var compilationTask = compiler.getTask(null, fileManager, diagnostics, options, null, List.of(sourceFile));
            compilationTask.setProcessors(List.of(new CallbackAnnotationProcessor()));

            boolean success = compilationTask.call();
            fileManager.close();

            return new CompilationResult(success, diagnostics.getDiagnostics(), generatedSourcesDir);
        } catch (IOException e) {
            throw new IllegalStateException("failed to run in-memory compilation", e);
        }
    }

    private static String formatDiagnostics(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        var builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            builder.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(null))
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    private record CompilationResult(
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            Path generatedSources) {}

    private static final class InMemorySourceFile extends SimpleJavaFileObject {
        private final String sourceCode;

        private InMemorySourceFile(String qualifiedName, String sourceCode) {
            super(URI.create("string:///" + qualifiedName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }
}
