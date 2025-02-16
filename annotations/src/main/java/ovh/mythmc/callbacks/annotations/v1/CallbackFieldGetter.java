package ovh.mythmc.callbacks.annotations.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(value = CallbackFieldGetters.class)
@Inherited
public @interface CallbackFieldGetter {

    String field();
    
    String getter();

}