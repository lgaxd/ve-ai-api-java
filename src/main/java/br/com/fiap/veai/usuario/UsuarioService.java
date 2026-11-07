package br.com.fiap.veai.usuario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.veai.comum.ApiException;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return repository.buscarComRegioes(id).orElseThrow(() -> ApiException.naoEncontrado("Usuário não encontrado."));
    }

    @Transactional
    public Usuario atualizarPreferencias(Long id, PreferenciasRequest req) {
        Usuario u = buscar(id);
        u.atualizarPreferencias(req.velocidadeVoz(), req.perguntaLivreAtiva());
        return u;
    }
}
