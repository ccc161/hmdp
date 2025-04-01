import com.hmdp.HmDianPingApplication;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

@SpringBootTest(classes = HmDianPingApplication.class)
public class RedisShopTest {
    @Resource
    private ShopServiceImpl service;

    @Test
    void testSaveShop() {
        for (long i = 0; i < 0; i++) {
            service.saveShopToRedis(i, 10L);
        }

    }
}
