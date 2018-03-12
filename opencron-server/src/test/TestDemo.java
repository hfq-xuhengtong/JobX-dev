import org.junit.Test;
import org.opencron.common.ext.ExtensionLoader;
import org.opencron.common.serialize.ObjectInput;
import org.opencron.common.serialize.ObjectOutput;
import org.opencron.common.serialize.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;


public class TestDemo {

    @Test
    public void test1() throws Exception {

        String msg = "opencron....";

        Serializer serializer = ExtensionLoader.load(Serializer.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ObjectOutput objectOutput = serializer.serialize(outputStream);
        objectOutput.writeObject(msg);
        objectOutput.flushBuffer();
        byte[] data = outputStream.toByteArray();
        outputStream.reset();


        ObjectInput objectInput = serializer.deserialize(new ByteArrayInputStream(data));
        String result = objectInput.readUTF();
        System.out.println(result);

    }

}
