package org.opencron.common.serialize.support;

import org.opencron.common.serialize.ObjectInput;
import org.opencron.common.serialize.ObjectOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AbstractSerializer {

    public byte[] serialize(ByteArrayOutputStream outputStream,ObjectOutput objectOutput,Object object) throws IOException {
        objectOutput.writeObject(object);
        objectOutput.flushBuffer();
        byte[] data = outputStream.toByteArray();
        outputStream.flush();
        outputStream.close();
        return data;
    }

    public <T> T deserialize(ObjectInput objectInput, Class<T> clazz) {
        try {
            return objectInput.readObject(clazz);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
