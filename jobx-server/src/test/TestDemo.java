import org.junit.Test;
import com.jobxhub.common.ext.ExtensionLoader;
import com.jobxhub.common.serialize.Serializer;


public class TestDemo {

    @Test
    public void test1() throws Exception {

        String str = "tar -xzvf $1 /$1>> ff.log";
        //(?is)(?!true|false).
        System.out.println(str.replaceAll("[^\\s+]+\\$1|\\$1"," agent "));

    }

}
