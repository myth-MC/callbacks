package ovh.mythmc.callbacks.processor.v1;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import com.google.auto.service.AutoService;

import ovh.mythmc.callbacks.annotations.v1.Callback;
import ovh.mythmc.callbacks.annotations.v1.CallbackFieldGetter;

@SupportedAnnotationTypes({
    "ovh.mythmc.callbacks.annotations.v1.Callback",
    "ovh.mythmc.callbacks.annotations.v1.CallbackFieldGetter",
    "ovh.mythmc.callbacks.annotations.v1.CallbackFieldGetters"
})
@AutoService(javax.annotation.processing.Processor.class)
public final class CallbackAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(CallbackFieldGetter.class)) {
            if (annotatedElement.getKind() != ElementKind.CLASS && annotatedElement.getKind() != ElementKind.RECORD) {
                error(annotatedElement, "@%s cannot be used outside classes or records", CallbackFieldGetter.class);
                return true;
            }

            var annotation = annotatedElement.getAnnotation(CallbackFieldGetter.class);

            boolean methodExists = false;

            for (Element element : annotatedElement.getEnclosedElements()) {
                if (element.getKind() == ElementKind.METHOD) {
                    var enclosingClassMethod = (ExecutableElement) element;
                    if (enclosingClassMethod.getSimpleName().toString().equals(annotation.getter().substring(0, annotation.getter().length() - 2))) {
                        methodExists = true;
                        break;
                    }
                }
            }

            if (!methodExists) {
                error(annotatedElement, "Method '%s' does not exist", annotation.getter());
                return true;
            }
        }

        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(Callback.class)) {
            if (annotatedElement.getKind() != ElementKind.CLASS && annotatedElement.getKind() != ElementKind.RECORD) {
                error(annotatedElement, "@%s must be used in a class or record", Callback.class);
                return true;
            }

            if (annotatedElement.getModifiers().contains(Modifier.ABSTRACT)) {
                error(annotatedElement, "@%s cannot be used in abstract classes", Callback.class);
                return true;
            }

            var callbackAnnotatedClass = new CallbackAnnotatedClass(processingEnv, (TypeElement) annotatedElement);
            try {
                callbackAnnotatedClass.buildCallbackFile().writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
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
