package com.jobxhub.agent.test;

import com.jobxhub.common.util.collection.HashMap;
import org.junit.Test;

import java.util.Map;

public class TestDemo {

    @Test
    public void test1() {
        Map<String,String> map = new HashMap<String,String>(0);
        map.put("a","1");
        map.put("b","2");
        map.put("C","3");

        Map<String,String> map1 = new HashMap<>(0);
        map1.putAll(map);
        map.clear();

        System.out.println(map1.get("a"));
        System.out.println(map.get("a"));

    }

}
