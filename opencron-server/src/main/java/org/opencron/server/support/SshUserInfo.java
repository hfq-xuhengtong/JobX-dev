package org.opencron.server.support;

import com.jcraft.jsch.UserInfo;

public class SshUserInfo  implements UserInfo {
    //private String passphrase = null;

    public SshUserInfo() {
        //this.passphrase = passphrase;
    }
    public String getPassphrase() {
        return null;//passphrase;
    }
    public String getPassword() {
        return null;
    }
    public boolean promptPassphrase(String s) {
        return true;
    }
    public boolean promptPassword(String s) {
        return true;
    }
    public boolean promptYesNo(String s) {
        return true;
    }
    public void showMessage(String s) {
        System.out.println(s);
    }
}