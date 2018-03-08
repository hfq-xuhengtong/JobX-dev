package org.opencron.server.support;

import org.opencron.common.Constants;
import org.opencron.server.domain.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;


@Component
public class TerminalContext implements Serializable {

    @Autowired
    private RedisCacheManager redisCacheManager;

    public Terminal get(String key) {
        return redisCacheManager.get(key(key),Terminal.class);
    }

    public void put(String key, Terminal terminal) {
        redisCacheManager.put(Constants.PARAM_TERMINAL_TOKEN_KEY,key);
        redisCacheManager.put(key(key),terminal);
    }

    public Terminal remove(String key) {
        Terminal terminal = get(key);
        redisCacheManager.evict(key(key));
        return terminal;
    }

    private String key(String key){
        return Constants.PARAM_TERMINAL_PREFIX_KEY + key;
    }

    public String getToken() {
        return redisCacheManager.get(Constants.PARAM_TERMINAL_TOKEN_KEY,String.class);
    }
}