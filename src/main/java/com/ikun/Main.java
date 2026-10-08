package com.ikun;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能AI景区管理系统 启动类
 *
 * <p>访问地址：http://localhost:8080/api</p>
 * <p>接口文档：http://localhost:8080/api/swagger-ui/index.html</p>
 *
 * @author smart-scenic
 */
@SpringBootApplication
@MapperScan("com.ikun.mapper")
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        System.out.println("""

                ==================================================
                  智能AI景区管理系统 启动成功
                  接口地址：http://localhost:8080/api
                  接口文档：http://localhost:8080/api/swagger-ui/index.html
                ==================================================
                """);
    }
}
