package br.com.fiap.veai.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.fiap.veai.auth.UsuarioLogado;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    static {
        // O usuário logado vem do JWT, não da requisição: não aparece no Swagger.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UsuarioLogado.class);
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vê AI · API")
                        .version("1.0.0")
                        .description("""
                                Assistente para pessoas com deficiência visual: descreve o ambiente com IA \
                                e transforma barreiras de acessibilidade em relatos para a rede de apoio. \
                                Mesmo contrato nas versões Java e .NET.""")
                        .contact(new Contact().name("Vê AI · FIAP 2TDSPX")))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
