/* Tela de login. Só faz três coisas: manda e-mail e senha para /api/auth/login,
 * mostra o erro que voltar e, dando certo, vai para o sistema.
 *
 * O token da sessão não passa por aqui: ele vem no cookie HttpOnly da resposta,
 * que este JavaScript não consegue (nem precisa) ler. */
(function ($) {
  'use strict';

  const $erro = $('#erroLogin');

  function mostrarErro(mensagem) {
    $('#erroLoginTexto').text(mensagem);
    $erro.removeClass('d-none');
  }

  function carregando(ligado) {
    $('#btnEntrar').prop('disabled', ligado);
    $('#carregandoEntrar').toggleClass('d-none', !ligado);
    $('#textoEntrar').text(ligado ? 'Entrando…' : 'Entrar');
  }

  $('#formLogin').on('submit', function (e) {
    e.preventDefault();
    $erro.addClass('d-none');

    const email = $('#email').val().trim();
    const senha = $('#senha').val();
    if (!email || !senha) {
      mostrarErro('Preencha o e-mail e a senha.');
      return;
    }

    carregando(true);
    $.ajax({
      url: '/api/auth/login',
      type: 'POST',
      data: {
        email: email,
        senha: senha,
        // Marcado, a sessão passa a valer por uma semana em vez de meia hora parada.
        manterConectado: $('#manterConectado').is(':checked')
      }
    })
      .done(function () {
        window.location.replace('/');       // replace: o botão "voltar" não retorna ao login
      })
      .fail(function (xhr) {
        let mensagem = 'Não foi possível entrar. Tente novamente.';
        try {
          mensagem = JSON.parse(xhr.responseText).erro || mensagem;
        } catch (err) {
          if (xhr.status === 0) {
            mensagem = 'Servidor indisponível. Verifique se a aplicação está no ar.';
          }
        }
        mostrarErro(mensagem);
        $('#senha').val('').trigger('focus');
      })
      .always(function () { carregando(false); });
  });

  /* Não há recuperação por e-mail: o link explica o caminho real, que é pedir
     a um ADMIN para redefinir a senha. */
  $('#btnEsqueci').on('click', function () {
    $('#dicaSenha').toggleClass('d-none');
  });

  // Aviso de Caps Lock: erro de senha mais comum que existe.
  $('#senha, #email').on('keyup keydown', function (e) {
    const ligado = e.originalEvent && typeof e.originalEvent.getModifierState === 'function'
      && e.originalEvent.getModifierState('CapsLock');
    $('#avisoCapsLock').toggleClass('d-none', !ligado);
  });

})(jQuery);
