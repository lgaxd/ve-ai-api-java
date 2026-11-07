package br.com.fiap.veai.auth;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import br.com.fiap.veai.usuario.Papel;

/** Converte o JWT autenticado em {@link UsuarioLogado} (sub = id do usuário, claim papel). */
@Component
public class UsuarioLogadoResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UsuarioLogado.class.equals(parameter.getParameterType());
    }

    @Override
    public UsuarioLogado resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                         NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Faça login para continuar.");
        }
        return new UsuarioLogado(Long.valueOf(jwt.getSubject()), Papel.valueOf(jwt.getClaimAsString(TokenService.CLAIM_PAPEL)));
    }
}
