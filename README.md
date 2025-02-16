<div align="center">
  <p>
    <!-- <img src="https://assets.mythmc.ovh/social/logo-small.png"> -->
    <h1>callbacks</h1>
    <a href="https://github.com/myth-MC/callbacks/releases/latest"><img src="https://img.shields.io/github/v/release/myth-MC/callbacks" alt="Latest release" /></a>
    <a href="https://github.com/myth-MC/callbacks/pulls"><img src="https://img.shields.io/github/issues-pr/myth-MC/callbacks" alt="Pull requests" /></a>
    <a href="https://github.com/myth-MC/callbacks/issues"><img src="https://img.shields.io/github/issues/myth-MC/callbacks" alt="Issues" /></a>
    <a href="https://github.com/myth-MC/callbacks/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="License" /></a>
    <br>
    A Java library for the management of event flows.
  </p>
</div>

<details open="open">
  <summary>🧲 Quick navigation</summary>
  <ol>
    <li>
      <a href="#information">📚 Information</a>
    </li>
    <li>
      <a href="#setup">📥 Project Setup</a>
    </li>
    <li>
      <a href="#generation">🖊️ Generating Callbacks</a>
    </li>
    <li>
      <a href="#handlers-and-listeners">⛓️‍💥 Using Handlers and Listeners</a>
    </li>
    <li>
      <a href="#callback-invocation">❗️ Invoking a Callback</a>
    </li>
    <li>
      <a href="#references">📕 References</a>
    </li>
  </ol>
</details>

<div id="information"></div>

# 📚 Information

**Callbacks** is an annotation-based Java library for managing event flows. 
It works by generating **callback classes** (source files) at build time which can be intercepted with custom handlers and listeners.

- **Handlers** provide access to the callback's object so that it can be _handled_, hence the name.
- **Listeners** give access to the object's parameters, which are mirrored from the callback's constructor and passed to the listener when a callback is handled.
  Listeners cannot modify the object at all, they just _listen_ to the result.
  
<div id="setup"></div>

# 📥 Project Setup

## Maven
```xml
<repositories>
  <repository>
    <id>myth-mc-releases</id>
    <name>myth-MC Repository</name>
    <url>https://repo.mythmc.ovh/releases</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>ovh.mythmc</groupId>
    <artifactId>callbacks-lib</artifactId>
    <version>$latest$</version>
    <scope>compile/<scope>
  </dependency>
</dependencies>
```

## Gradle Kotlin
```kts
repositories {
    maven {
        name = "mythmc"
        url = uri("https://repo.mythmc.ovh/releases")
    }
}

dependencies {
    implementation("ovh.mythmc:callbacks-lib:$latest$")
}
```

## Gradle Groovy
```gradle
repositories {
    maven {
        name = 'mythmc'
        url = 'https://repo.mythmc.ovh/releases'
    }
}

dependencies {
    compileOnly 'ovh.mythmc:callbacks-lib:$latest$'
}
```

<div id="generation"></div>

# 🖊️ Generating Callbacks

## Simple Callback Class
Generating a callback class can be as simple as putting a single `@Callback` annotation above your class' definition. For example:

```java
@Callback
public class Example {

  public final String exampleField;

  public Example(String exampleField) {
    this.exampleField = exampleField;
  }

}
```

This will generate a class named `ExampleCallback` in the same package as the source class. 

## Private fields
Classes with private fields and public getters require some additional configuration by using the `@CallbackFieldGetter` annotation:

```java
@Callback
@CallbackFieldGetter(field = "exampleField", getter = "getExampleField()")
public class Example {

  private final String exampleField;

  public Example(String exampleField) {
    this.exampleField = exampleField;
  }

  public String getExampleField() {
    return exampleField;
  }

}
```

>[!TIP]
>The `@CallbackFieldGetter` annotation is marked as _repeteable_, which means that you can use it as many times as you need.
>You can also group these annotations with `@CallbackFieldGetters`:
>```java
>@CallbackFieldGetters({
>  @CallbackFieldGetter(field = "exampleField", getter = "getExampleField()"),
>  @CallbackFieldGetter(field = "exampleField2", getter = "getExampleField2()")
>})
>```

## Inheritance
When extending a `@Callback` annotated class, all of its callback field getters will be inherited from the super class. 
This means that we can easily extend our `Example` class with even more fields:

```java
@Callback
@CallbackFieldGetter(field = "newField", getter = "getNewField()")
public class ExtendExample extends Example {

  private final String newField;

  public ExtendExample(String exampleField, String newField) {
    super(exampleField);
    this.newField = newField;
  }

  public String getNewField() {
    return newField;
  }

}
```

>[!WARNING]
>The `@Callback` annotation is **not** inherited.

## Constructors
You may also find cases where you might want to use a specific constructor from your class. This can be done too by using the `constructor` property within the `@Callback` annotation:
```java
@Callback(constructor = 2)
public class ConstructorExample {

  public String field;

  ConstructorExample() { // 1
  }

  public ConstructorExample(String field) { // 2
    this.field = field;
  }

}
```

## Record Classes
Record classes are also supported:
```java
@Callback
public record ExampleRecord(String field) {

}
```

<div id="handlers-and-listeners"></div>

# ⛓️‍💥 Using Handlers and Listeners
Handlers and listeners allow us to intercept callbacks and handle them accordingly.

## Handlers
**Handlers** provide access to the callback's object so that it can be _handled_, hence the name. Let's register one in our `ConstructorExampleCallback` and modify the value of `field`:
```java
var callbackInstance = ConstructorExampleCallback.INSTANCE; // Singleton instance of our callback
var handlerIdentifier = IdentifierKey.of("group", "identifier"); // Unique identifier of our handler. You can also use a namespaced string ("group:identifier")

callbackInstance.registerHandler(handlerIdentifier, constructorExample -> {
  constructorExample.field = "Hello world!";
});
```

## Listeners
**Listeners** give access to the object's parameters, which are mirrored from the callback's constructor and passed to the listener when a callback has been handled.
Listeners cannot modify the object at all, they just _listen_ to the result. Let's register one in our 'ConstructorExampleCallback' and listen to the result:
```java
var callbackInstance = ConstructorExampleCallback.INSTANCE; // Singleton instance of our callback
var listenerIdentifier = IdentifierKey.of("group", "identifier"); // Unique identifier of our listener. You can also use a namespaced string ("group:identifier")

callbackInstance.registerListener(listenerIdentifier, field -> {
  System.out.println(field);
});
```

## Generic Types
If the callback object uses generic types, we'll need to specify the types we're expecting while registering handlers or listeners. For example:
```java
callbackInstance.registerListener(listenerIdentifier, field -> {
  System.out.println(field);
}, String.class);
```

<div id="callback-invocation"></div>

# ❗️ Invoking a Callback
Once we have our handlers and listeners registered, we can invoke the callback:
```java
var callbackInstance = ConstructorExampleCallback.INSTANCE; // Singleton instance of our callback

callbackInstance.invoke(new ConstructorExample(""), result -> { // We can get the result of the callback (which will be a ConstructorExample as well)
  System.out.println("Callback result: " + result.toString());
});

callbackInstance.invoke(new ConstructorExample("")); // Or we can ignore it
```

<div id="references"></div>

# 📕 References
Most of the information on this README comes from our blog post "Callbacks: a modern approach to events". 

These are some projects we've used as inspiration for the library:

- [Using the Event API](https://www.spigotmc.org/wiki/using-the-event-api/) by Spigot
- [Annotation Processing 101](https://hannesdorfmann.com/annotation-processing/annotationprocessing101/) by Hannes Dorfmann: _great post on Java annotation processing with detailed explanations and lots of examples_
- [JavaPoet](https://github.com/palantir/javapoet) by palantir: _this is actually the library we're usimg to generate callback classes as source files_
- [Lifecycle API](https://docs.papermc.io/paper/dev/lifecycle) by PaperMC
- [Working With Events](https://docs.papermc.io/velocity/dev/event-api) by PaperMC
- [Events](https://docs.spongepowered.org/stable/en/plugin/event/index.html) by SpongePowered
- [Events](https://docs.minecraftforge.net/en/latest/concepts/events/) by MinecraftForge
