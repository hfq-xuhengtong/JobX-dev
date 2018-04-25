package com.jobxhub.agent.test;

import org.junit.Test;

public class TestDemo {

    @Test
    public void test1(){
        System.out.println(" call d:\\job.bat".replaceAll("^call\\s+",""));
    }
}
