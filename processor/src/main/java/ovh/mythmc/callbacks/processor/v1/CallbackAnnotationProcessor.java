package ovh.mythmc.callbacks.processor.v1;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import com.google.auto.service.AutoService;

import ovh.mythmc.callbacks.annotations.v1.Callback;
import ovh.mythmc.callbacks.annotations.v1.CallbackField;

@SupportedAnnotationTypes({
    "ovh.mythmc.callbacks.annotations.v1.Callback",
    "ovh.mythmc.callbacks.annotations.v1.CallbackField",
    "ovh.mythmc.callbacks.annotations.v1.CallbackFields"
})
@AutoService(javax.annotation.processing.Processor.class)
public final class CallbackAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean hasErrors = false;

        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(CallbackField.class)) {
            hasErrors |= !validateCallbackFieldUsage(annotatedElement);
        }

        if (hasErrors) {
            return true;
        }

        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(Callback.class)) {
            if (!validateCallbackUsage(annotatedElement)) {
                hasErrors = true;
                continue;
            }

            generateCallback((TypeElement) annotatedElement);
        }

        return true;
    }

    private boolean validateCallbackFieldUsage(Element annotatedElement) {
        if (annotatedElement.getKind() != ElementKind.CLASS && annotatedElement.getKind() != ElementKind.RECORD) {
            error(annotatedElement, "@%s cannot be used outside classes or records", CallbackField.class);
            return false;
        }

        var annotation = annotatedElement.getAnnotation(CallbackField.class);

        boolean methodExists = false;
        boolean fieldExists = false;

        for (Element element : annotatedElement.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.FIELD) {
                if (element.getSimpleName().toString().equals(annotation.getter().replace("()", ""))) {
                    methodExists = true;
                }
            }

            if (element.getKind() == ElementKind.FIELD) {
                if (element.getSimpleName().toString().equals(annotation.field())) {
                    fieldExists = true;
                }
            }
        }

        if (!methodExists) {
            error(annotatedElement, "Method '%s' does not exist", annotation.getter());
            return false;
        }

        if (!fieldExists) {
            error(annotatedElement, "Field '%s' does not exist", annotation.field());
            return false;
        }

        return true;
    }

    private boolean validateCallbackUsage(Element annotatedElement) {
        if (annotatedElement.getKind() != ElementKind.CLASS && annotatedElement.getKind() != ElementKind.RECORD) {
            error(annotatedElement, "@%s must be used in a class or record", Callback.class);
            return false;
        }

        if (annotatedElement.getModifiers().contains(Modifier.ABSTRACT)) {
            error(annotatedElement, "@%s cannot be used in abstract classes", Callback.class);
            return false;
        }

        return true;
    }

    private void generateCallback(TypeElement annotatedElement) {
        var callbackAnnotatedClass = new CallbackAnnotatedClass(processingEnv, annotatedElement);
        try {
            callbackAnnotatedClass.buildCallbackFile().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            error(annotatedElement, "Failed to generate callback class: %s", e.getMessage());
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
    
    private void error(Element element, String message, Object... args) {
        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.ERROR,
            String.format(message, args),
            element);
    }

}
