package com.shaadrag.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
// import org.springframework.context.ApplicationContext;

// import com.shaadrag.configserver.config.SecurityConfig;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

	public static void main(String[] args) {

		// ApplicationContext context =
		// SpringApplication.run(ConfigServerApplication.class, args);
		// SecurityConfig config = context.getBean(SecurityConfig.class);
		// System.out.println(config.getIdentitySvcName());
		// System.out.println(config.getIdentitySvcPassword());
		// System.out.println(config.getGatewaySvcName());
		// System.out.println(config.getGatewaySvcPassword());

		SpringApplication.run(ConfigServerApplication.class, args);

	}

}
