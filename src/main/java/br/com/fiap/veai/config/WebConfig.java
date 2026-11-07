package br.com.fiap.veai.config;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.fiap.veai.auth.UsuarioLogadoResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UsuarioLogadoResolver usuarioLogadoResolver;

    public WebConfig(UsuarioLogadoResolver usuarioLogadoResolver) {
        this.usuarioLogadoResolver = usuarioLogadoResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(usuarioLogadoResolver);
    }

    /** Relógio injetável: os testes conseguem fixar "agora". */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
