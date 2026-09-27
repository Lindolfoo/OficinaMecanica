/* Tela de entrada. Tem dois modos:
 *
 *   ENTRAR      o normal: manda e-mail e senha para /api/auth/login
 *   CONFIGURAR  primeira execução, quando ainda não existe nenhuma conta:
 *               pede nome, e-mail e senha e cria o administrador
 *
 * Quem decide o modo é o servidor, em /api/auth/estado. Não existe senha
 * padrão no programa: quem define é o usuário, aqui, uma vez só.
 *
 * O token da sessão não passa por aqui: ele vem no cookie HttpOnly da
 * resposta, que este JavaScript não consegue (nem precisa) ler. */
(function ($) {
  'use strict';

  const $erro = $('#erroLogin');
  let configurando = false;

  function mostrarErro(mensagem) {
    $('#erroLoginTexto').text(mensagem);
    $erro.removeClass('d-none');
  }

  function carregando(ligado) {
    $('#btnEntrar').prop('disabled', ligado);
    $('#carregandoEntrar').toggleClass('d-none', !ligado);
    $('#textoEntrar').text(ligado
      ? (configurando ? 'Criando…' : 'Entrando…')
      : (configurando ? 'Criar administrador' : 'Entrar'));
  }

  /** Vira a tela para o modo de primeiro acesso. */
  function modoConfiguracao() {
    configurando = true;
    $('#tituloLogin').text('Primeiro acesso');
    $('#subtituloLogin').text('Crie a conta de administrador do sistema');
    $('#blocoPrimeiroAcesso, #blocoConfirmaSenha, #avisoConfiguracao').removeClass('d-none');
    $('#blocoManter, #btnEsqueci').addClass('d-none');
    $('#textoEntrar').text('Criar administrador');
    $('#senha').attr('placeholder', 'Crie uma senha').attr('autocomplete', 'new-password');
    $('#nome').trigger('focus');
  }

  // O servidor diz em que modo a tela deve abrir.
  $.getJSON('/api/auth/estado')
    .done(function (estado) {
      if (estado.precisaConfigurar) {
        modoConfiguracao();
      }
    })
    .fail(function () {
      mostrarErro('Não foi possível falar com o servidor. Ele está rodando?');
    });

  $('#formLogin').on('submit', function (e) {
    e.preventDefault();
    $erro.addClass('d-none');

    const email = $('#email').val().trim();
    const senha = $('#senha').val();
    if (!email || !senha) {
      mostrarErro('Preencha o e-mail e a senha.');
      return;
    }

    let url = '/api/auth/login';
    let dados = { email: email, senha: senha, manterConectado: $('#manterConectado').is(':checked') };

    if (configurando) {
      const nome = $('#nome').val().trim();
      if (nome.length < 2) {
        mostrarErro('Informe o seu nome.');
        return;
      }
      if (senha !== $('#confirmaSenha').val()) {
        mostrarErro('A confirmação não confere com a senha.');
        return;
      }
      url = '/api/auth/configurar';
      dados = { nome: nome, email: email, senha: senha };
    }

    carregando(true);
    $.ajax({ url: url, type: 'POST', data: dados })
      .done(function () {
        window.location.replace('/');       // replace: o botão "voltar" não retorna aqui
      })
      .fail(function (xhr) {
        let mensagem = configurando
          ? 'Não foi possível criar o administrador.'
          : 'Não foi possível entrar. Tente novamente.';
        try {
          mensagem = JSON.parse(xhr.responseText).erro || mensagem;
        } catch (err) {
          if (xhr.status === 0) {
            mensagem = 'Servidor indisponível. Verifique se a aplicação está no ar.';
          }
        }
        mostrarErro(mensagem);
        if (!configurando) {
          $('#senha').val('').trigger('focus');
        }
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
