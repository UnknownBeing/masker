package world.michi.masker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @ClassName MaskerRunner
 * @Author 17
 * @Date 2019/11/27 16:10
 * @Description ...
 **/

@Component
@Slf4j
public class MaskerInputRunner implements ApplicationRunner {

    @Resource
    MaskerService maskerService;

    @Override
    @Async("asyncServiceExecutor")
    public void run(ApplicationArguments args) {

        while(true){

            maskerService.in();

        }
    }
}
