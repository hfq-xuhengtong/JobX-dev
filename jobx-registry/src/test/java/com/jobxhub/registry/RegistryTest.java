package com.jobxhub.registry;

import com.jobxhub.registry.zookeeper.zkclient.ZkclientZookeeperClient;
import org.junit.Test;
import com.jobxhub.registry.zookeeper.ChildListener;
import com.jobxhub.registry.zookeeper.ZookeeperClient;

import java.io.IOException;
import java.util.List;

public class RegistryTest {

    private ZookeeperClient zookeeperClient;

    String url = "zookeeper://127.0.0.1:2181";

 //   @Before
    public void init() {
        zookeeperClient = new ZkclientZookeeperClient(URL.valueOf(url));
    }

    @Test
    public void create() throws IOException {
        zookeeperClient.create("/jobx/agent/6",true);
        System.in.read();
    }

    @Test
    public void delete() throws IOException {
        zookeeperClient.delete("/jobx/agent/2");
        System.in.read();
    }

    @Test
    public void lister() throws IOException {


        zookeeperClient.addChildListener("/jobx/agent",new ChildListener(){
            @Override
            public void childChanged(String path, List<String> children) {
                System.out.println("add:----->"+path);
                for (String child:children) {
                    System.out.println(child);
                }
            }
        });

        zookeeperClient.removeChildListener("/jobx/agent",new ChildListener(){
            @Override
            public void childChanged(String path, List<String> children) {
                System.out.println("remove:----->"+path);
                for (String child:children) {
                    System.out.println(child);
                }
            }
        });


        System.in.read();
    }

    @Test
    public void get(){
        List<String> paths = zookeeperClient.getChildren("/jobx/agent");

        for (String path:paths)
            System.out.println(path);
    }

    @Test
    public void backup(){
        URL url = URL.valueOf("jobx.registry=zookeeper://172.17.112.130:2181?backup=172.17.112.129:2181,172.17.112.128:2181,172.17.112.131:2181,172.17.112.127:2181");
        System.out.println(url.getBackupAddress());
    }

}
