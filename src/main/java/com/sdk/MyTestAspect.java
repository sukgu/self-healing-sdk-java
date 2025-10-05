package com.sdk;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class MyTestAspect {
    @After("execution(* MyTestClass.someMethod())")
    public void beforeSomeMethod() {
        System.out.println("Aspect advice called.");
    }
}