import org.junit.Test;
import org.opencron.common.ext.ExtensionLoader;
import org.opencron.common.job.*;
import org.opencron.common.util.IdGenerator;
import org.opencron.rpc.Client;
import org.opencron.rpc.Server;

import java.io.File;

public class NettyFileTest {


    @Test
    public void server() {
        Server server = ExtensionLoader.load(Server.class);
        server.start(8089, null);
    }

    @Test
    public void client() throws Exception {
        Client client = ExtensionLoader.load(Client.class);
        Request request = new Request();
        request.setId(IdGenerator.getId());
        request.setAction(Action.UPLOAD);
        request.setRpcType(RpcType.SYNC);
        request.setHost("127.0.0.1");
        request.setPort(8089);
        File file = new File("/Users/benjobs/movie/盗梦空间.mkv");
        RequestFile requestFile = new RequestFile(file);
        requestFile.setSavePath("/Users/benjobs/Desktop");
        request.setUploadFile(requestFile);
        Response response = client.sentSync(request);
        System.out.println(response.getAction());

    }

}
