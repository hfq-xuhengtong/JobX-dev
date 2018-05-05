import com.jobxhub.server.domain.Agent;
import org.junit.Test;


public class TestDemo {

    @Test
    public void test1() throws Exception {

        Agent agent = new Agent();

        Boolean flag = true;

        System.out.println(flag.equals(agent.getStatus()));

        String str = "tar -xzvf $1 /$1>> ff.log";
        //(?is)(?!true|false).
        System.out.println(str.replaceAll("[^\\s+]+\\$1|\\$1"," agent "));

    }

}
