package com.thoughtworks.i0.jpa;

import com.thoughtworks.i0.core.Facet;
import com.thoughtworks.i0.jpa.internal.JpaPersistUnitEnabler;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Inherited
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Facet(JpaPersistUnitEnabler.class)
public @interface JpaPersist {
    String unit();
}
