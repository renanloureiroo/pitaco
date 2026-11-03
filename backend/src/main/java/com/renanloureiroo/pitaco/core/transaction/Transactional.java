package com.renanloureiroo.pitaco.core.transaction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Anotação do projeto, não do Spring: o caso de uso declara que precisa de transação sem
// depender de quem a abre. Quem a implementa é o TransactionalAdvisor, na infra.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Transactional {}
