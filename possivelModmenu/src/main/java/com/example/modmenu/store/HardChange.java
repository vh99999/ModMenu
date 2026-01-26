package com.example.modmenu.store;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field in GenesisConfig as a "Hard" change.
 * Hard changes require the dimension to be regenerated to take full effect.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface HardChange {
}
