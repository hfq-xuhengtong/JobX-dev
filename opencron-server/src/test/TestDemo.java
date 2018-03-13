import org.junit.Test;
import org.opencron.common.ext.ExtensionLoader;
import org.opencron.common.serialize.Serializer;


public class TestDemo {

    @Test
    public void test1() throws Exception {

        String msg = "opencron....";

        Serializer serializer = ExtensionLoader.load(Serializer.class);

        byte[] data = serializer.serialize(msg);

        String result = serializer.deserialize(data,String.class);
        System.out.println(result);

    }

}
