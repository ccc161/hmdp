import com.hmdp.HmDianPingApplication;
import com.hmdp.dto.SeckillOrderMessageConsumption;
import com.hmdp.service.ISeckillOrderMessageConsumptionService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = HmDianPingApplication.class)
public class ConsumptionTest {
    @Resource
    ISeckillOrderMessageConsumptionService messageConsumptionService;
    @Test
    public void testSave() {
        SeckillOrderMessageConsumption messageConsumption = new SeckillOrderMessageConsumption();
        String s = "123";
        messageConsumption.setMessageId(s);
        messageConsumption.setOrderId(s);
        messageConsumption.setQuantity(1);
        messageConsumption.setProductId(s);
        messageConsumption.setProcessStatus(0);
        messageConsumptionService.save(messageConsumption);
    }
}
