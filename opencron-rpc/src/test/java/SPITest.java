import org.junit.Test;
import org.opencron.common.ext.ExtensionLoader;
import org.opencron.common.util.SystemPropertyUtils;
import org.opencron.rpc.Client;
import org.opencron.rpc.Server;

import java.io.IOException;

public class SPITest {

    @Test
    public void testSpi() throws IOException {
        boolean xx =  SystemPropertyUtils.getBoolean("aaa",false);

        System.out.println(xx);
        //SystemPropertyUtils.setProperty("aaa","true");


        xx =  SystemPropertyUtils.getBoolean("aaa",false);

        System.out.println(xx);
    }

}
