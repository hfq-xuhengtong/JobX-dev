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

package com.jobxhub.agent.service;

import com.jobxhub.agent.util.PropertiesLoader;
import com.jobxhub.common.Constants;
import com.jobxhub.common.logging.LoggerFactory;
import com.jobxhub.common.util.CommandUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;

public class MessageService {

    private Logger logger = LoggerFactory.getLogger(MessageService.class);

    private Configuration conf = HBaseConfiguration.create();

    private String tableName = "jobx_log";
    private String family = "record";
    private String qualifier = "message";

    private static final long CHECK_DELAY = 500;  // 每500毫秒写入一次

    private ByteArrayOutputStream outputStream;
    private String pid;
    private File logFile;
    private FileOutputStream fileOutputStream;
    private volatile boolean running = false;
    private Integer msgLength = 0;

    public MessageService(ByteArrayOutputStream outputStream, String pid) {
        // conf.set("hbase.rootdir", "hdfs://CDH-HUAXIA-00005:8020/hbase");
        // conf.set("hbase.zookeeper.quorum", "CDH-HUAXIA-00005:2181");\
        // 设置Zookeeper,直接设置IP地址
        conf.set("hbase.zookeeper.quorum", PropertiesLoader.getProperty(Constants.PARAM_HBASE_ZOOKEEPER_QUORUM));
        this.outputStream = outputStream;
        this.pid = pid;
        this.logFile = CommandUtils.createLogFile(pid);
    }

    public void writeMessage() throws IOException {
        byte[] bytes = outputStream.toByteArray();
        if (bytes.length > msgLength) {
            fileOutputStream.write(bytes, msgLength, bytes.length - msgLength);
            msgLength = bytes.length;
        }
    }

    public void start() throws IOException {
        this.fileOutputStream = new FileOutputStream(logFile);
        running = true;
        Executors.newSingleThreadExecutor().execute((new KeepWriting()));
    }

    public void stop() {
        if (running) {
            running = false;
            logger.info("[JobX]:stop writing message, pid: " + this.pid);
        }
        try {
            fileOutputStream.close();
        } catch (Exception e) {
            logger.debug("[JobX]:writeMsg error:{}", e.getMessage());
        }

    }

    class KeepWriting implements Runnable {

        @Override
        public void run() {
            while (running) {
                try {
                    MessageService.this.writeMessage();
                } catch (IOException e) {
                    logger.debug("[JobX]:writeMsg error:{}", e.getMessage());
                    MessageService.this.stop();
                }
                try {
                    Thread.currentThread().sleep(CHECK_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if(!running){
                // write into HBase
                try {
                    MessageService.this.createTable();
                    MessageService.this.saveMessage();
                } catch (Exception e){
                    logger.debug("[JobX] Write log into HBase failed! {}", e.getMessage());
                }

                /* sleep 60 seconds and delete log file
                try {
                    Thread.sleep(60000);
                    logFile.delete();
                } catch (Exception e){
                    logger.warn("[JobX] Delete log file failed! {}", e.getMessage());
                }
                */
            }
        }
    }


    private void createTable() throws Exception {
        Connection connection = ConnectionFactory.createConnection(conf);
        Admin admin = connection.getAdmin();
        TableName tableNameObj = TableName.valueOf(tableName);
        if (admin.tableExists(tableNameObj)) {
            logger.info("[JobX]Table [ {} ] exists!", tableName);
        } else {
            HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
            tableDesc.addFamily(new HColumnDescriptor(family));
            admin.createTable(tableDesc);
            logger.info("[JobX]:Create table [ {} ] success!", tableName);
        }
        admin.close();
        connection.close();
    }


    private void saveMessage() throws Exception {
        String rowKey = "jobx_" + pid;
        String value = outputStream.toString();

        Connection connection = ConnectionFactory.createConnection(conf);
        Table table = connection.getTable(TableName.valueOf(tableName));
        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(value));
        table.put(put);
        table.close();
        connection.close();
        logger.info("[JobX]:INSERT INTO " + tableName + " (rowKey, value) VALUES (" + rowKey + " , " + value.substring(0,100) + " )");

    }


}
