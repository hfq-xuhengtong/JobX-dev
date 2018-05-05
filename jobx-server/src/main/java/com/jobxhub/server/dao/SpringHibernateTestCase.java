/**
 * Copyright (c) 2015 The JobX Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jobxhub.server.dao;

import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Spring的支持数据库事务和依赖注入的JUnit 4 TestCase基类简写.
 * 加载applicationContext-*test.xml配置,
 * 如果需要默认载入更多的applicatioContext.xml,在项目中重写本类.
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath*:app-*.xml" })
public class SpringHibernateTestCase extends AbstractTransactionalJUnit4SpringContextTests {
    /**
     * 刷新默认的sessionFactory,强制Hibernate执行SQL以验证ORM配置.
     * SQL执行的结果只要不进行提交就不会影响测试数据库的实际数据.
     */
    public void flush() {
        flush("sessionFactory");
    }

    /**
     * 刷新sessionFactory,强制Hibernate执行SQL以验证ORM配置.
     * SQL执行的结果只要不进行提交就不会影响测试数据库的实际数据.
     */
    public void flush(String sessionFactoryName) {
        ((SessionFactory) applicationContext.getBean(sessionFactoryName)).getCurrentSession().flush();
    }
    
    
    //-- Assert 函数 --//

    protected void assertEquals(Object expected, Object actual) {
        Assert.assertEquals(expected, actual);
    }

    protected void assertEquals(String message, Object expected, Object actual) {
        Assert.assertEquals(message, expected, actual);
    }

    protected void assertTrue(boolean condition) {
        Assert.assertTrue(condition);
    }

    protected void assertTrue(String message, boolean condition) {
        Assert.assertTrue(message, condition);
    }

    protected void assertFalse(boolean condition) {
        Assert.assertFalse(condition);
    }

    protected void assertFalse(String message, boolean condition) {
        Assert.assertFalse(message, condition);
    }

    protected void assertNull(Object object) {
        Assert.assertNull(object);
    }

    protected void assertNull(String message, Object object) {
        Assert.assertNull(message, object);
    }

    protected void assertNotNull(Object object) {
        Assert.assertNotNull(object);
    }

    protected void assertNotNull(String message, Object object) {
        Assert.assertNotNull(message, object);
    }
}