package ovh.mythmc.callbacks.processor.v1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;

import ovh.mythmc.callbacks.annotations.v1.Callback;
import ovh.mythmc.callbacks.annotations.v1.CallbackFieldGetter;
import ovh.mythmc.callbacks.annotations.v1.CallbackFieldGetters;
import ovh.mythmc.callbacks.key.IdentifierKey;

public final class CallbackAnnotatedClass {

    private final static String CALLBACK_SUFFIX = "Callback";

    private final static String HANDLER_SUFFIX = "CallbackHandler";

    private final static String LISTENER_SUFFIX = "CallbackListener";

    public final TypeElement typeElement;

    public final Name qualifiedName;

    public final Name simpleName;

    private final Name packageName;

    CallbackAnnotatedClass(ProcessingEnvironment processingEnvironment, TypeElement typeElement) {
        this.typeElement = typeElement;
        this.qualifiedName = typeElement.getQualifiedName();
        this.simpleName = typeElement.getSimpleName();
        this.packageName = processingEnvironment.getElementUtils().getPackageOf(typeElement).getQualifiedName();
    }

    JavaFile buildCallbackFile() {
        ArrayList<ParameterSpec> parameters = getParametersAsSpecs();
        Collection<TypeVariableName> typeVariables = getTypeVariableNames();

        final ClassName objectClass = ClassName.bestGuess(qualifiedName.toString());
        var objectParameter = ParameterSpec.builder(objectClass, "callback").build();
        if (!typeVariables.isEmpty())
            objectParameter = ParameterSpec.builder(ParameterizedTypeName.get(objectClass, typeVariables.toArray(new TypeVariableName[typeVariables.size()])), "callback").build();

        final var callbackListenerTypeSpec = buildListenerInterface(parameters, typeVariables, packageName.toString(), simpleName.toString());
        final var callbackHandlerTypeSpec = buildHandlerInterface(objectParameter, parameters, typeVariables, qualifiedName.toString(), packageName.toString(), simpleName.toString());

        final var callbackListenerClass = ClassName.get(packageName.toString(), simpleName + CALLBACK_SUFFIX, simpleName + LISTENER_SUFFIX);
        var callbackListenerParameter = ParameterSpec.builder(callbackListenerClass, "callbackListener").build();

        final var callbackHandlerClass = ClassName.get(packageName.toString(), simpleName + CALLBACK_SUFFIX, simpleName + HANDLER_SUFFIX);
        var callbackHandlerParameter = ParameterSpec.builder(callbackHandlerClass, "callbackHandler").build();

        if (!typeVariables.isEmpty()) {
            callbackListenerParameter = ParameterSpec.builder(ParameterizedTypeName.get(callbackListenerClass, typeVariables.toArray(new TypeVariableName[typeVariables.size()])), "callbackListener").build();
            callbackHandlerParameter = ParameterSpec.builder(ParameterizedTypeName.get(callbackHandlerClass, typeVariables.toArray(new TypeVariableName[typeVariables.size()])), "callbackHandler").build();
        }

        final Collection<ParameterSpec> typeVariableNamesAsParameters = getTypeVariableNamesAsParameterSpecs();

        // Static instance
        var instance = FieldSpec.builder(
                ClassName.bestGuess(qualifiedName.toString() + CALLBACK_SUFFIX), 
                "INSTANCE", 
                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("new " + qualifiedName.toString() + CALLBACK_SUFFIX + "()")
            .build();

        // Listener map
        var mapOfCallbackListeners = ParameterizedTypeName.get(ClassName.get("java.util", "HashMap"), TypeName.get(String.class), callbackListenerClass);
        var listenerMap = FieldSpec.builder(mapOfCallbackListeners.box(), "callbackListeners")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", HashMap.class)
            .build();

        // Handler map
        var mapOfCallbackHandlers = ParameterizedTypeName.get(ClassName.get("java.util", "HashMap"), TypeName.get(String.class), callbackHandlerClass);
        var handlerMap = FieldSpec.builder(mapOfCallbackHandlers.box(), "callbackHandlers")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", HashMap.class)
            .build();

        // Constructor
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build();

        var identifierKeyParameter = ParameterSpec.builder(IdentifierKey.class, "identifier").build();

        var registerListener = MethodSpec.methodBuilder("registerListener")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(identifierKeyParameter)
            .addParameter(callbackListenerParameter)
            .addParameters(typeVariableNamesAsParameters)
            .addStatement("callbackListeners.put($N.toString(), $N)", identifierKeyParameter, callbackListenerParameter)
            .build();

        var registerListenerWithStringKey = MethodSpec.methodBuilder("registerListener")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(String.class, "key")
            .addParameter(callbackListenerParameter)
            .addParameters(typeVariableNamesAsParameters)
            .addStatement("callbackListeners.put(key, $N)", callbackListenerParameter)
            .build();

        var registerHandler = MethodSpec.methodBuilder("registerHandler")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(identifierKeyParameter)
            .addParameter(callbackHandlerParameter)
            .addParameters(typeVariableNamesAsParameters)
            .addStatement("callbackHandlers.put($N.toString(), $N)", identifierKeyParameter, callbackHandlerParameter)
            .build();

        var registerHandlerWithStringKey = MethodSpec.methodBuilder("registerHandler")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(String.class, "key")
            .addParameter(callbackHandlerParameter)
            .addParameters(typeVariableNamesAsParameters)
            .addStatement("callbackHandlers.put(key, $N)", callbackHandlerParameter)
            .build();

        var unregisterListeners = MethodSpec.methodBuilder("unregisterListeners")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ArrayTypeName.of(IdentifierKey.class), "identifiers")
            .varargs(true)
            .addStatement("$T.stream(identifiers).forEach(key -> callbackListeners.remove(key.toString()))", Arrays.class)
            .build();

        var unregisterListenersWithStringKey = MethodSpec.methodBuilder("unregisterListeners")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ArrayTypeName.of(String.class), "identifiers")
            .varargs(true)
            .addStatement("$T.stream(identifiers).forEach(callbackListeners::remove)", Arrays.class)
            .build();

        var unregisterHandlers = MethodSpec.methodBuilder("unregisterHandlers")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ArrayTypeName.of(IdentifierKey.class), "identifiers")
            .varargs(true)
            .addStatement("$T.stream(identifiers).forEach(key -> callbackHandlers.remove(key.toString()))", Arrays.class)
            .build();

        var unregisterHandlersWithStringKey = MethodSpec.methodBuilder("unregisterHandlers")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ArrayTypeName.of(String.class), "identifiers")
            .varargs(true)
            .addStatement("$T.stream(identifiers).forEach(callbackHandlers::remove)", Arrays.class)
            .build();
        
        var consumerOfObject = ParameterizedTypeName.get(ClassName.get("java.util.function", "Consumer"), objectParameter.type());
        var invokeWithResult = MethodSpec.methodBuilder("invoke")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(objectParameter)
            .addParameter(consumerOfObject, "result")
            .beginControlFlow("for ($T handler : callbackHandlers.values())", callbackHandlerClass)
            .addStatement("handler.handle(callback)")
            .endControlFlow()
            .beginControlFlow("for ($T listener : callbackListeners.values())", callbackListenerClass)
            .addStatement("java.util.concurrent.CompletableFuture.runAsync(() -> listener.trigger(" + getParameterGetters() + "))")
            .endControlFlow()
            .beginControlFlow("if (result != null)")
            .addStatement("result.accept(callback)")
            .endControlFlow()
            .build();

        var handleWithResult = MethodSpec.methodBuilder("handle")   
            .addAnnotation(Deprecated.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(objectParameter)
            .addParameter(consumerOfObject, "result")
            .addStatement("invoke(callback, result)")
            .build();

        var invoke = MethodSpec.methodBuilder("invoke")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(objectParameter)
            .addStatement("handle(callback, null)")
            .build();

        var handle = MethodSpec.methodBuilder("handle")
            .addAnnotation(Deprecated.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariables(typeVariables)
            .addParameter(objectParameter)
            .addStatement("invoke(callback)")
            .build();

        // Class
        TypeSpec callbackClass = TypeSpec.classBuilder(simpleName + CALLBACK_SUFFIX)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addField(instance)
            .addField(handlerMap)
            .addField(listenerMap)
            .addMethod(constructor)
            .addMethod(registerHandler)
            .addMethod(registerHandlerWithStringKey)
            .addMethod(unregisterHandlers)
            .addMethod(unregisterHandlersWithStringKey)
            .addMethod(registerListener)
            .addMethod(registerListenerWithStringKey)
            .addMethod(unregisterListeners)
            .addMethod(unregisterListenersWithStringKey)
            .addMethod(invokeWithResult)
            .addMethod(handleWithResult)
            .addMethod(invoke)
            .addMethod(handle)
            .addType(callbackHandlerTypeSpec)
            .addType(callbackListenerTypeSpec)
            .build();

        JavaFile callbackFile = JavaFile.builder(packageName.toString(), callbackClass)
            .build();

        return callbackFile;
    }

    private ArrayList<ParameterSpec> getParametersAsSpecs() {
        final ArrayList<ParameterSpec> parameterSpecs = new ArrayList<>();

        getConstructorParameters().entrySet().stream()
            .map(entry -> {
                return ParameterSpec.builder(ClassName.get(entry.getValue()), entry.getKey())  
                    .build();
            })
            .forEach(parameterSpecs::add);

        return parameterSpecs;
    }

    private Map<String, TypeMirror> getConstructorParameters() {
        final Map<String, TypeMirror> parametersMap = new LinkedHashMap<>();

        int currentConstructorIndex = 1;
        final int targetConstructorIndex = getCallbackAnnotation().constructor();

        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (!parametersMap.isEmpty())
                break;

            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                if (currentConstructorIndex == targetConstructorIndex) {
                    var constructorElement = (ExecutableElement) enclosedElement;
                    var constructorParameters = constructorElement.getParameters();
    
                    constructorParameters.forEach(constructorParameter -> {
                        parametersMap.put(constructorParameter.toString(), constructorParameter.asType());
                    });
                    break;
                }

                currentConstructorIndex++;
            }
        }

        return parametersMap;
    }

    private Collection<TypeVariableName> getTypeVariableNames() {
        return typeElement.getTypeParameters().stream().map(typeParameter -> TypeVariableName.get(typeParameter)).toList();
    }

    private Collection<ParameterSpec> getTypeVariableNamesAsParameterSpecs() {
        return getTypeVariableNames().stream()
            .map(typeVariable -> {
                ParameterizedTypeName parameterizedTypeName = ParameterizedTypeName.get(ClassName.get("java.lang", "Class"), typeVariable);
                return ParameterSpec.builder(parameterizedTypeName, typeVariable.name().toLowerCase()).build();
            })
            .toList();
    }

    private String getParameterGetters() {
        var parameterNames = getConstructorParameters().keySet();
        String objectFieldsString = "";

        for (int i = 0; i < parameterNames.size(); i++) {
            objectFieldsString = objectFieldsString + "callback." + getFieldGetter((String) parameterNames.toArray()[i]);

            if (i < parameterNames.size() - 1)
                objectFieldsString = objectFieldsString + ", ";
        }

        return objectFieldsString;
    }

    private String getFieldGetter(String fieldName) {
        var getter = fieldName;
        if (typeElement.getKind().equals(ElementKind.RECORD))
            return getter + "()";

        final ArrayList<CallbackFieldGetter> fieldGetters = new ArrayList<>();
        fieldGetters.addAll(Arrays.asList(typeElement.getAnnotationsByType(CallbackFieldGetter.class)));

        final CallbackFieldGetters fieldGettersAnnotation = typeElement.getAnnotation(CallbackFieldGetters.class);
        if (fieldGettersAnnotation != null)
            fieldGetters.addAll(Arrays.asList(fieldGettersAnnotation.value()));

        for (CallbackFieldGetter fieldGetterAnnotation : fieldGetters) {
            if (fieldGetterAnnotation.field().equals(fieldName)) {
                getter = fieldGetterAnnotation.getter();
                break;
            }
        }

        return getter;
    }

    private TypeSpec buildListenerInterface(Iterable<ParameterSpec> parameters, Collection<TypeVariableName> typeVariables, String packageName, String simpleName) {
        var callbackListenerBuilder = TypeSpec.interfaceBuilder(simpleName + LISTENER_SUFFIX)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(FunctionalInterface.class)
            .addMethod(MethodSpec.methodBuilder("trigger")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameters(parameters)
                .build());

        if (!typeVariables.isEmpty())
            callbackListenerBuilder = callbackListenerBuilder
                .addTypeVariables(typeVariables);

        return callbackListenerBuilder.build();
    }

    private TypeSpec buildHandlerInterface(ParameterSpec objectParameter, Iterable<ParameterSpec> parameters, Collection<TypeVariableName> typeVariables, String qualifiedName, String packageName, String simpleName) {
        var callbackHandlerBuilder = TypeSpec.interfaceBuilder(simpleName + HANDLER_SUFFIX)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(FunctionalInterface.class)
            .addMethod(MethodSpec.methodBuilder("handle")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(objectParameter)
                .build());

        if (!typeVariables.isEmpty())
            callbackHandlerBuilder = callbackHandlerBuilder
                .addTypeVariables(typeVariables);

        return callbackHandlerBuilder.build();
    }

    private Callback getCallbackAnnotation() {
        return typeElement.getAnnotation(Callback.class);
    }
    
}
