package com.hongdi.nexusforum.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author He Hongdi
 * @version 1.0.0
 * @ClassName LoginRequired.java
 * @Description 拦截器注解
 * @createTime 4/30/2020 7:24 PM
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {
}
