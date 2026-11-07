package br.com.fiap.veai.comum;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converte qualquer erro no formato {timestamp, status, erro, mensagem, campos[]} com texto em português. */
@RestControllerAdvice
public class TratadorErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErroResposta> api(ApiException e) {
        return responder(e.status(), e.getMessage());
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    ResponseEntity<ErroResposta> limite(LimiteExcedidoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.segundosParaLiberar()))
                .body(ErroResposta.de(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException e) {
        List<CampoErro> campos = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.add(new CampoErro(f.getField(), f.getDefaultMessage())));
        e.getBindingResult().getGlobalErrors().forEach(g -> campos.add(new CampoErro(g.getObjectName(), g.getDefaultMessage())));
        return ResponseEntity.badRequest().body(ErroResposta.de(HttpStatus.BAD_REQUEST, "Confira os dados enviados.", campos));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErroResposta> validacaoParametros(HandlerMethodValidationException e) {
        List<CampoErro> campos = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new CampoErro(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(ErroResposta.de(HttpStatus.BAD_REQUEST, "Confira os dados enviados.", campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroResposta> corpoInvalido(HttpMessageNotReadableException e) {
        return responder(HttpStatus.BAD_REQUEST, "Não entendi os dados enviados. Confira o formato e os valores.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErroResposta> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErroResposta.de(HttpStatus.BAD_REQUEST, "Valor inválido para " + e.getName() + ".",
                List.of(new CampoErro(e.getName(), "Valor inválido: " + e.getValue()))));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ErroResposta> faltando(Exception e) {
        return responder(HttpStatus.BAD_REQUEST, "Faltou informar um dado obrigatório: " + e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErroResposta> arquivoGrande(MaxUploadSizeExceededException e) {
        return responder(HttpStatus.CONTENT_TOO_LARGE, "A foto passa de 5 MB. Envie uma foto menor.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErroResposta> tipoMidia(HttpMediaTypeNotSupportedException e) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de envio não suportado.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErroResposta> metodo(HttpRequestMethodNotSupportedException e) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Operação não permitida neste endereço.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException e) {
        return responder(HttpStatus.NOT_FOUND, "Endereço não encontrado.");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ErroResposta> semPermissao(Exception e) {
        return responder(HttpStatus.FORBIDDEN, "Você não tem permissão para esta ação.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErroResposta> naoAutenticado(AuthenticationException e) {
        return responder(HttpStatus.UNAUTHORIZED, "Faça login para continuar.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException e) {
        log.warn("Violação de integridade: {}", e.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "Esta operação conflita com dados já existentes. Atualize e tente de novo.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResposta> inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Algo deu errado do nosso lado. Tente de novo em instantes.");
    }

    private static ResponseEntity<ErroResposta> responder(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(ErroResposta.de(status, mensagem));
    }
}
