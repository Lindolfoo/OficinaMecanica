/* Oficina Mecânica — front-end em jQuery puro (sem frameworks SPA).
 * Cada módulo (Clientes, Veículos, Serviços, OS, Usuários) segue o mesmo padrão:
 *   carregar()  -> GET lista e monta a tabela
 *   abrirModal() -> preenche o formulário (novo ou edição)
 *   submit       -> POST (novo) ou PUT (edição)
 *   excluir      -> DELETE após confirmação
 *
 * A página só começa a funcionar depois de confirmar a sessão em
 * /api/auth/sessao — ver o bloco "início" no fim do arquivo.
 */
(function ($) {
  'use strict';

  const API = '/api';

  /** Dados de quem está logado. Preenchido no início, antes de qualquer tela. */
  let sessao = null;

  // ------------------------------------------------------------ utilidades

  /** Escapa texto antes de inserir no HTML (evita XSS a partir de dados do banco). */
  function esc(v) {
    return $('<div>').text(v == null ? '' : String(v)).html();
  }

  function fmtMoeda(v) {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  function fmtData(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('pt-BR') + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  function fmtCpf(cpf) {
    return cpf ? String(cpf).replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4') : '';
  }

  /** Extrai a mensagem {"erro": "..."} de uma resposta com falha. */
  function erroDe(xhr) {
    try {
      return JSON.parse(xhr.responseText).erro || 'Erro inesperado';
    } catch (e) {
      return xhr.status === 0 ? 'Servidor indisponível. Verifique se a aplicação está no ar.' : 'Erro ' + xhr.status;
    }
  }

  function toast(msg, tipo) {
    const $t = $(
      '<div class="toast align-items-center text-bg-' + (tipo || 'success') + ' border-0" role="alert">' +
        '<div class="d-flex"><div class="toast-body">' + esc(msg) + '</div>' +
        '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Fechar"></button></div>' +
      '</div>');
    $('#toasts').append($t);
    $t.on('hidden.bs.toast', function () { $t.remove(); });
    new bootstrap.Toast($t[0], { delay: 4000 }).show();
  }

  function debounce(fn, ms) {
    let t;
    return function () { clearTimeout(t); t = setTimeout(fn, ms); };
  }

  // ------------------------------------------------------------ sessão e segurança

  /* Todo POST/PUT/DELETE leva o token anti-CSRF no cabeçalho. O token não está
     no cookie de propósito: é justamente por um site de fora não conseguir lê-lo
     que o ataque de CSRF não passa (ver security/FiltroAutenticacao.java). */
  $.ajaxSetup({
    beforeSend: function (xhr, opcoes) {
      if (sessao && !/^(GET|HEAD|OPTIONS)$/i.test(opcoes.type || 'GET')) {
        xhr.setRequestHeader('X-CSRF-Token', sessao.csrfToken);
      }
    }
  });

  /* Sessão expirada em qualquer chamada: volta para o login em vez de deixar a
     tela quebrada mostrando erro atrás de erro. */
  $(document).ajaxError(function (evento, xhr) {
    if (xhr.status === 401) {
      window.location.replace('/login.html');
    }
  });

  function iniciais(nome) {
    const partes = String(nome || '').trim().split(/\s+/);
    const primeira = partes[0] ? partes[0][0] : '?';
    const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primeira + ultima).toUpperCase();
  }

  function aplicarSessao() {
    $('#usuarioNome').text(sessao.nome);
    $('#usuarioEmail').text(sessao.email);
    $('#usuarioPerfil').text(sessao.admin ? 'Administrador' : 'Atendente');
    $('#usuarioIniciais').text(iniciais(sessao.nome));
    // O menu de usuários só aparece para ADMIN — e o servidor confere de novo
    // a cada chamada, porque esconder botão não é controle de acesso.
    $('.somente-admin').toggleClass('d-none', !sessao.admin);
  }

  $('#btnSair').on('click', function () {
    $.ajax({ url: API + '/auth/logout', type: 'POST' })
      .always(function () { window.location.replace('/login.html'); });
  });

  // ------------------------------------------------------------ navegação

  const PAGINAS = {
    dashboard: { titulo: 'Dashboard', carregar: carregarDashboard },
    clientes: { titulo: 'Clientes', carregar: carregarClientes },
    veiculos: { titulo: 'Veículos', carregar: carregarVeiculos },
    servicos: { titulo: 'Serviços', carregar: carregarServicos },
    ordens: { titulo: 'Ordens de Serviço', carregar: carregarOrdens },
    usuarios: { titulo: 'Usuários', carregar: carregarUsuarios }
  };

  function irPara(nome) {
    const pagina = PAGINAS[nome] ? nome : 'dashboard';
    $('.bl-item').removeClass('ativo').filter('[data-pagina="' + pagina + '"]').addClass('ativo');
    $('.pagina').removeClass('ativa');
    $('#pg' + pagina.charAt(0).toUpperCase() + pagina.slice(1)).addClass('ativa');
    $('#tituloPagina').text(PAGINAS[pagina].titulo);
    fecharMenuNoCelular();
    PAGINAS[pagina].carregar();
  }

  $('.bl-item').on('click', function () { irPara($(this).data('pagina')); });

  function fecharMenuNoCelular() {
    $('body').removeClass('menu-aberto');
    $('#blSombra').removeClass('visivel');
  }

  $('#btnMenu').on('click', function () {
    $('body').toggleClass('menu-aberto');
    $('#blSombra').toggleClass('visivel', $('body').hasClass('menu-aberto'));
  });

  $('#blSombra').on('click', fecharMenuNoCelular);

  /* Menu recolhido fica gravado no navegador: quem prefere a coluna de ícones
     não precisa recolher de novo a cada visita. */
  $('#btnRecolher').on('click', function () {
    const recolhido = !$('body').hasClass('recolhido');
    $('body').toggleClass('recolhido', recolhido);
    $('#iconeRecolher').toggleClass('bi-chevron-left', !recolhido).toggleClass('bi-chevron-right', recolhido);
    try {
      window.localStorage.setItem('oficina.menuRecolhido', recolhido ? '1' : '0');
    } catch (e) { /* navegação anônima: só não lembra da escolha */ }
  });

  function restaurarMenu() {
    let recolhido = false;
    try {
      recolhido = window.localStorage.getItem('oficina.menuRecolhido') === '1';
    } catch (e) { /* idem */ }
    if (recolhido) {
      $('body').addClass('recolhido');
      $('#iconeRecolher').removeClass('bi-chevron-left').addClass('bi-chevron-right');
    }
  }

  // ------------------------------------------------------------ DASHBOARD

  /* As cores dos gráficos vêm do style.css. Assim existe um lugar só para
     mexer na paleta, e o SVG desenhado aqui nunca sai do tom dos cartões. */
  function corDoTema(variavel, alternativa) {
    const valor = getComputedStyle(document.documentElement).getPropertyValue(variavel).trim();
    return valor || alternativa;
  }

  const CORES_STATUS = {
    ABERTA: corDoTema('--os-aberta', '#d87b46'),
    EM_ANDAMENTO: corDoTema('--os-andamento', '#dbb157'),
    CONCLUIDA: corDoTema('--os-concluida', '#61b885'),
    CANCELADA: corDoTema('--os-cancelada', '#aeb4ba')
  };

  const COR_TEXTO_GRAFICO = corDoTema('--painel-texto', '#3f4750');

  function cartaoIndicador(classe, icone, rotulo, valor, nota) {
    return '<div class="col-sm-6 col-xl-3">' +
      '<div class="card indicador ' + classe + '">' +
        '<div class="indicador-icone"><i class="bi ' + icone + '"></i></div>' +
        '<div>' +
          '<div class="indicador-rotulo">' + esc(rotulo) + '</div>' +
          '<div class="indicador-valor">' + esc(valor) + '</div>' +
          '<div class="indicador-nota">' + esc(nota) + '</div>' +
        '</div>' +
      '</div></div>';
  }

  /**
   * Rosca de participação desenhada à mão em SVG.
   * O projeto não usa biblioteca de gráficos (nem tem internet garantida), então
   * o caminho de cada fatia é calculado aqui mesmo com seno e cosseno.
   */
  function desenharRosca(fatias) {
    const total = fatias.reduce(function (s, f) { return s + f.valor; }, 0);
    if (!total) {
      return '<p class="vazio">Nenhuma ordem de serviço registrada ainda.</p>';
    }

    const cx = 130, cy = 130, raio = 108, buraco = 66;
    let svg = '<svg viewBox="0 0 260 260" role="img" aria-label="Distribuição das ordens por situação">';

    if (fatias.filter(function (f) { return f.valor > 0; }).length === 1) {
      // Uma fatia só: um arco de 360° degenera, então o anel é desenhado inteiro.
      const unica = fatias.find(function (f) { return f.valor > 0; });
      svg += '<circle cx="' + cx + '" cy="' + cy + '" r="' + ((raio + buraco) / 2) + '" fill="none" stroke="'
           + unica.cor + '" stroke-width="' + (raio - buraco) + '"></circle>';
    } else {
      let angulo = -Math.PI / 2;               // começa no topo
      fatias.forEach(function (f) {
        if (!f.valor) return;
        const abertura = (f.valor / total) * Math.PI * 2;
        const fim = angulo + abertura;
        const grande = abertura > Math.PI ? 1 : 0;
        const p = function (r, a) { return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; };
        const a1 = p(raio, angulo), a2 = p(raio, fim), b1 = p(buraco, fim), b2 = p(buraco, angulo);
        svg += '<path d="M' + a1 + ' A' + raio + ' ' + raio + ' 0 ' + grande + ' 1 ' + a2 +
               ' L' + b1 + ' A' + buraco + ' ' + buraco + ' 0 ' + grande + ' 0 ' + b2 + ' Z"' +
               ' fill="' + f.cor + '"></path>';
        angulo = fim;
      });
    }

    svg += '<text x="' + cx + '" y="' + (cy - 4) + '" text-anchor="middle" font-size="30" font-weight="700" fill="'
         + COR_TEXTO_GRAFICO + '">' + total + '</text>';
    svg += '<text x="' + cx + '" y="' + (cy + 20) + '" text-anchor="middle" font-size="12" fill="#6c757d">'
         + (total === 1 ? 'ordem' : 'ordens') + '</text>';
    svg += '</svg>';

    let legenda = '<div class="legenda">';
    fatias.forEach(function (f) {
      legenda += '<span><i style="background:' + f.cor + '"></i>' + esc(f.rotulo) + ': <strong>' + f.valor + '</strong></span>';
    });
    legenda += '</div>';

    return svg + legenda;
  }

  /** Barras do faturamento por mês, também em SVG escrito à mão. */
  function desenharBarras(meses) {
    if (!meses.length) {
      return '<p class="vazio">Sem movimento nos últimos meses.</p>';
    }

    const larg = 560, alt = 240, base = alt - 34, topo = 16;
    const maior = Math.max.apply(null, meses.map(function (m) { return Number(m.total); })) || 1;
    const passo = larg / meses.length;
    const larguraBarra = Math.min(54, passo * 0.55);

    let svg = '<svg viewBox="0 0 ' + larg + ' ' + alt + '" role="img" aria-label="Faturamento por mês">';
    svg += '<line x1="0" y1="' + base + '" x2="' + larg + '" y2="' + base + '" stroke="#e5e7eb" stroke-width="1"></line>';

    meses.forEach(function (m, i) {
      const valor = Number(m.total);
      const altura = Math.max(3, (valor / maior) * (base - topo));
      const x = i * passo + (passo - larguraBarra) / 2;
      const y = base - altura;
      const partes = m.mes.split('-');            // "2026-09" -> ["2026", "09"]
      const rotulo = partes[1] + '/' + partes[0].slice(2);

      svg += '<rect x="' + x + '" y="' + y + '" width="' + larguraBarra + '" height="' + altura +
             '" rx="5" fill="' + CORES_STATUS.ABERTA + '" opacity="' + (i === meses.length - 1 ? '1' : '.8') + '"></rect>';
      svg += '<text x="' + (x + larguraBarra / 2) + '" y="' + (y - 6) + '" text-anchor="middle" font-size="11" fill="#6c757d">'
           + (valor >= 1000 ? (valor / 1000).toFixed(1) + 'k' : valor.toFixed(0)) + '</text>';
      svg += '<text x="' + (x + larguraBarra / 2) + '" y="' + (base + 18) + '" text-anchor="middle" font-size="11" fill="#6c757d">'
           + rotulo + '</text>';
      svg += '<text x="' + (x + larguraBarra / 2) + '" y="' + (base + 30) + '" text-anchor="middle" font-size="9" fill="#a3aab1">'
           + m.quantidade + ' OS</text>';
    });

    svg += '</svg>';
    return svg;
  }

  function carregarDashboard() {
    $.getJSON(API + '/dashboard')
      .done(function (d) {
        const ind = d.indicadores;

        $('#cartoesIndicadores').html(
          cartaoIndicador('', 'bi-clipboard-pulse', 'OS em aberto', ind.osAbertas,
            fmtMoeda(ind.emAberto) + ' a receber') +
          cartaoIndicador('verde', 'bi-check2-circle', 'OS concluídas', ind.osConcluidas,
            'Ticket médio ' + fmtMoeda(ind.ticketMedio)) +
          cartaoIndicador('ambar', 'bi-cash-coin', 'Faturamento', fmtMoeda(ind.faturamento),
            'Somente ordens concluídas') +
          cartaoIndicador('grafite', 'bi-people', 'Clientes', ind.clientes,
            ind.veiculos + ' veículo(s) · ' + ind.servicosAtivos + ' serviço(s) ativo(s)'));

        $('#graficoStatus').html(desenharRosca(d.porStatus.map(function (s) {
          return {
            rotulo: (ROTULO_STATUS[s.status] || [s.status])[0],
            valor: Number(s.quantidade),
            cor: CORES_STATUS[s.status] || '#adb5bd'
          };
        })));

        $('#graficoMeses').html(desenharBarras(d.porMes));

        const $top = $('#tabelaTopServicos tbody').empty();
        if (!d.topServicos.length) {
          $top.append('<tr><td colspan="3" class="vazio">Nenhum item lançado ainda.</td></tr>');
        }
        d.topServicos.forEach(function (s) {
          $top.append(
            '<tr>' +
              '<td>' + esc(s.descricao) +
                ' <span class="badge text-bg-light border ms-1">' + rotuloTipo(s.tipo) + '</span></td>' +
              '<td class="text-center">' + s.quantidade + '</td>' +
              '<td class="text-end fw-medium">' + fmtMoeda(s.total) + '</td>' +
            '</tr>');
        });

        const $ult = $('#tabelaUltimasOrdens tbody').empty();
        if (!d.ultimasOrdens.length) {
          $ult.append('<tr><td colspan="4" class="vazio">Nenhuma ordem de serviço.</td></tr>');
        }
        d.ultimasOrdens.forEach(function (o) {
          $ult.append(
            '<tr>' +
              '<td class="text-secondary">#' + o.id + '</td>' +
              '<td><span class="badge text-bg-dark">' + esc(o.veiculoPlaca) + '</span><br>' +
                  '<small class="text-secondary">' + esc(o.clienteNome) + '</small></td>' +
              '<td>' + badgeStatus(o.status) + '</td>' +
              '<td class="text-end fw-medium">' + fmtMoeda(o.valorTotal) + '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  }

  // ------------------------------------------------------------ CLIENTES

  const modalCliente = new bootstrap.Modal('#modalCliente');

  function carregarClientes() {
    const busca = $('#buscaCliente').val().trim();
    $.getJSON(API + '/clientes', busca ? { busca: busca } : {})
      .done(function (lista) {
        const $tb = $('#tabelaClientes tbody').empty();
        $('#totalClientes').text(lista.length + ' cliente(s)');
        if (!lista.length) {
          $tb.append('<tr><td colspan="6" class="text-center text-secondary py-4">' +
            (busca ? 'Nenhum cliente encontrado para "' + esc(busca) + '".' : 'Nenhum cliente cadastrado. Clique em "Novo cliente" para começar.') +
            '</td></tr>');
          return;
        }
        lista.forEach(function (c) {
          $tb.append(
            '<tr data-id="' + c.id + '">' +
              '<td class="text-secondary">' + c.id + '</td>' +
              '<td class="fw-medium nome">' + esc(c.nome) + '</td>' +
              '<td>' + fmtCpf(c.cpf) + '</td>' +
              '<td>' + esc(c.telefone) + '</td>' +
              '<td>' + esc(c.email) + '</td>' +
              '<td class="text-end text-nowrap">' +
                '<button class="btn btn-sm btn-outline-primary btn-editar" title="Editar"><i class="bi bi-pencil"></i></button> ' +
                '<button class="btn btn-sm btn-outline-danger btn-excluir" title="Excluir"><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  }

  function abrirModalCliente(c) {
    const $f = $('#formCliente');
    $f[0].reset();
    $f.removeClass('was-validated');
    $('#clienteId').val(c ? c.id : '');
    $('#tituloModalCliente').text(c ? 'Editar cliente #' + c.id : 'Novo cliente');
    if (c) {
      $('#nome').val(c.nome);
      $('#cpf').val(fmtCpf(c.cpf));
      $('#telefone').val(c.telefone);
      $('#email').val(c.email);
    }
    modalCliente.show();
    setTimeout(function () { $('#nome').trigger('focus'); }, 300);
  }

  $('#btnNovoCliente').on('click', function () { abrirModalCliente(null); });
  $('#buscaCliente').on('input', debounce(carregarClientes, 250));

  $('#tabelaClientes').on('click', '.btn-editar', function () {
    const id = $(this).closest('tr').data('id');
    $.getJSON(API + '/clientes/' + id)
      .done(abrirModalCliente)
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaClientes').on('click', '.btn-excluir', function () {
    const $tr = $(this).closest('tr');
    const id = $tr.data('id');
    const nome = $tr.find('.nome').text();
    if (!window.confirm('Excluir o cliente "' + nome + '"?')) return;
    $.ajax({ url: API + '/clientes/' + id, type: 'DELETE' })
      .done(function () { toast('Cliente excluído'); carregarClientes(); })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#formCliente').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) {
      $(this).addClass('was-validated');
      return;
    }
    const id = $('#clienteId').val();
    const $btn = $('#btnSalvarCliente').prop('disabled', true);
    $.ajax({
      url: id ? API + '/clientes/' + id : API + '/clientes',
      type: id ? 'PUT' : 'POST',
      data: $(this).serialize()          // envia application/x-www-form-urlencoded
    })
      .done(function () {
        modalCliente.hide();
        toast(id ? 'Cliente atualizado' : 'Cliente cadastrado');
        carregarClientes();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });


  // ------------------------------------------------------------ VEÍCULOS

  const modalVeiculo = new bootstrap.Modal('#modalVeiculo');
  const modalHistorico = new bootstrap.Modal('#modalHistorico');

  function carregarVeiculos() {
    const busca = $('#buscaVeiculo').val().trim();
    $.getJSON(API + '/veiculos', busca ? { busca: busca } : {})
      .done(function (lista) {
        const $tb = $('#tabelaVeiculos tbody').empty();
        $('#totalVeiculos').text(lista.length + ' veículo(s)');
        if (!lista.length) {
          $tb.append('<tr><td colspan="7" class="text-center text-secondary py-4">Nenhum veículo encontrado.</td></tr>');
          return;
        }
        lista.forEach(function (v) {
          $tb.append(
            '<tr data-id="' + v.id + '">' +
              '<td class="text-secondary">' + v.id + '</td>' +
              '<td><span class="badge text-bg-dark placa">' + esc(v.placa) + '</span></td>' +
              '<td class="fw-medium">' + esc(v.marca) + ' ' + esc(v.modelo) + '</td>' +
              '<td>' + v.ano + '</td>' +
              '<td>' + esc(v.cor) + '</td>' +
              '<td>' + esc(v.clienteNome) + '</td>' +
              '<td class="text-end text-nowrap">' +
                '<button class="btn btn-sm btn-outline-secondary btn-historico" title="Histórico de atendimentos"><i class="bi bi-clock-history"></i></button> ' +
                '<button class="btn btn-sm btn-outline-primary btn-editar" title="Editar"><i class="bi bi-pencil"></i></button> ' +
                '<button class="btn btn-sm btn-outline-danger btn-excluir" title="Excluir"><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  }

  /**
   * Histórico de atendimentos do veículo: todas as OS dele, da mais recente para a
   * mais antiga, com o quanto já foi gasto no carro.
   */
  function abrirHistoricoVeiculo(v) {
    $('#tituloModalHistorico').text('Histórico — ' + v.placa + ' · ' + v.marca + ' ' + v.modelo);
    const $tb = $('#tabelaHistorico tbody').empty();
    $('#resumoHistorico').text('Carregando…');
    modalHistorico.show();

    $.getJSON(API + '/ordens', { veiculoId: v.id })
      .done(function (lista) {
        if (!lista.length) {
          $('#resumoHistorico').text('Este veículo ainda não passou pela oficina.');
          $tb.append('<tr><td colspan="6" class="text-center text-secondary py-3">Nenhuma ordem de serviço.</td></tr>');
          return;
        }
        let gasto = 0;
        lista.forEach(function (o) {
          gasto += Number(o.valorTotal);
          $tb.append(
            '<tr>' +
              '<td class="text-secondary">' + o.id + '</td>' +
              '<td>' + esc(o.descricaoProblema) + '</td>' +
              '<td class="text-nowrap">' + fmtData(o.dataAbertura) + '</td>' +
              '<td class="text-nowrap">' + fmtData(o.dataConclusao) + '</td>' +
              '<td>' + badgeStatus(o.status) + '</td>' +
              '<td class="text-end fw-medium">' + fmtMoeda(o.valorTotal) + '</td>' +
            '</tr>');
        });
        $('#resumoHistorico').html(lista.length + ' atendimento(s) · já gasto no veículo: <strong>'
            + fmtMoeda(gasto) + '</strong>');
      })
      .fail(function (xhr) {
        $('#resumoHistorico').text('');
        toast(erroDe(xhr), 'danger');
      });
  }

  /** Preenche o <select> de proprietários com os clientes cadastrados. */
  function carregarSelectClientes(selecionado) {
    return $.getJSON(API + '/clientes').done(function (lista) {
      const $sel = $('#veiculoCliente').empty().append('<option value="">Selecione…</option>');
      lista.forEach(function (c) {
        $sel.append($('<option>').val(c.id).text(c.nome + ' — ' + fmtCpf(c.cpf)));
      });
      if (selecionado) $sel.val(selecionado);
    });
  }

  function abrirModalVeiculo(v) {
    const $f = $('#formVeiculo');
    $f[0].reset();
    $f.removeClass('was-validated');
    $('#veiculoId').val(v ? v.id : '');
    $('#tituloModalVeiculo').text(v ? 'Editar veículo #' + v.id : 'Novo veículo');
    carregarSelectClientes(v ? v.clienteId : null).done(function () {
      if (v) {
        $('#placa').val(v.placa);
        $('#marca').val(v.marca);
        $('#modelo').val(v.modelo);
        $('#ano').val(v.ano);
        $('#cor').val(v.cor);
      }
      modalVeiculo.show();
    });
  }

  $('#btnNovoVeiculo').on('click', function () { abrirModalVeiculo(null); });
  $('#buscaVeiculo').on('input', debounce(carregarVeiculos, 250));

  $('#tabelaVeiculos').on('click', '.btn-historico', function () {
    $.getJSON(API + '/veiculos/' + $(this).closest('tr').data('id'))
      .done(abrirHistoricoVeiculo)
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaVeiculos').on('click', '.btn-editar', function () {
    $.getJSON(API + '/veiculos/' + $(this).closest('tr').data('id'))
      .done(abrirModalVeiculo)
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaVeiculos').on('click', '.btn-excluir', function () {
    const $tr = $(this).closest('tr');
    if (!window.confirm('Excluir o veículo de placa ' + $tr.find('.placa').text() + '?')) return;
    $.ajax({ url: API + '/veiculos/' + $tr.data('id'), type: 'DELETE' })
      .done(function () { toast('Veículo excluído'); carregarVeiculos(); })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#formVeiculo').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    const id = $('#veiculoId').val();
    const $btn = $('#btnSalvarVeiculo').prop('disabled', true);
    $.ajax({
      url: id ? API + '/veiculos/' + id : API + '/veiculos',
      type: id ? 'PUT' : 'POST',
      data: $(this).serialize()
    })
      .done(function () {
        modalVeiculo.hide();
        toast(id ? 'Veículo atualizado' : 'Veículo cadastrado');
        carregarVeiculos();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });

  // ------------------------------------------------------------ SERVIÇOS

  const modalServico = new bootstrap.Modal('#modalServico');

  function rotuloTipo(tipo) {
    return tipo === 'PECA' ? 'Peça' : 'Mão de obra';
  }

  function carregarServicos() {
    const busca = $('#buscaServico').val().trim();
    $.getJSON(API + '/servicos', busca ? { busca: busca } : {})
      .done(function (lista) {
        const $tb = $('#tabelaServicos tbody').empty();
        $('#totalServicos').text(lista.length + ' item(ns) no catálogo');
        if (!lista.length) {
          $tb.append('<tr><td colspan="6" class="text-center text-secondary py-4">Nenhum serviço encontrado.</td></tr>');
          return;
        }
        lista.forEach(function (s) {
          $tb.append(
            '<tr data-id="' + s.id + '">' +
              '<td class="text-secondary">' + s.id + '</td>' +
              '<td class="fw-medium descricao">' + esc(s.descricao) + '</td>' +
              '<td><span class="badge text-bg-light border">' + rotuloTipo(s.tipo) + '</span></td>' +
              '<td class="text-end">' + fmtMoeda(s.preco) + '</td>' +
              '<td>' + (s.ativo
                ? '<span class="badge text-bg-success">Disponível</span>'
                : '<span class="badge text-bg-secondary">Inativo</span>') + '</td>' +
              '<td class="text-end text-nowrap">' +
                '<button class="btn btn-sm btn-outline-primary btn-editar" title="Editar"><i class="bi bi-pencil"></i></button> ' +
                '<button class="btn btn-sm btn-outline-danger btn-excluir" title="Excluir"><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  }

  function abrirModalServico(s) {
    const $f = $('#formServico');
    $f[0].reset();
    $f.removeClass('was-validated');
    $('#servicoId').val(s ? s.id : '');
    $('#tituloModalServico').text(s ? 'Editar serviço #' + s.id : 'Novo serviço');
    if (s) {
      $('#descricao').val(s.descricao);
      $('#tipo').val(s.tipo);
      $('#preco').val(s.preco);
      $('#ativo').prop('checked', s.ativo);
    } else {
      $('#ativo').prop('checked', true);
    }
    modalServico.show();
  }

  $('#btnNovoServico').on('click', function () { abrirModalServico(null); });
  $('#buscaServico').on('input', debounce(carregarServicos, 250));

  $('#tabelaServicos').on('click', '.btn-editar', function () {
    $.getJSON(API + '/servicos/' + $(this).closest('tr').data('id'))
      .done(abrirModalServico)
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaServicos').on('click', '.btn-excluir', function () {
    const $tr = $(this).closest('tr');
    if (!window.confirm('Excluir "' + $tr.find('.descricao').text() + '" do catálogo?')) return;
    $.ajax({ url: API + '/servicos/' + $tr.data('id'), type: 'DELETE' })
      .done(function () { toast('Serviço excluído'); carregarServicos(); })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#formServico').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    const id = $('#servicoId').val();
    const $btn = $('#btnSalvarServico').prop('disabled', true);
    $.ajax({
      url: id ? API + '/servicos/' + id : API + '/servicos',
      type: id ? 'PUT' : 'POST',
      data: $(this).serialize() + '&ativo=' + $('#ativo').is(':checked')
    })
      .done(function () {
        modalServico.hide();
        toast(id ? 'Serviço atualizado' : 'Serviço cadastrado');
        carregarServicos();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });

  // ------------------------------------------------------------ ORDENS DE SERVIÇO

  const modalOrdem = new bootstrap.Modal('#modalOrdem');

  /* Itens em edição no modal. Numa OS nova eles só vão para o banco no submit
     (gravados em uma única transação); numa OS existente cada item é enviado
     imediatamente para a API. */
  let itensEmEdicao = [];
  let catalogoServicos = [];

  const ROTULO_STATUS = {
    ABERTA: ['Aberta', 'text-bg-primary'],
    EM_ANDAMENTO: ['Em andamento', 'text-bg-warning'],
    CONCLUIDA: ['Concluída', 'text-bg-success'],
    CANCELADA: ['Cancelada', 'text-bg-secondary']
  };

  function badgeStatus(status) {
    const r = ROTULO_STATUS[status] || [status, 'text-bg-light'];
    return '<span class="badge ' + r[1] + '">' + r[0] + '</span>';
  }

  /**
   * Monta a via impressa da OS em #impressaoOs e chama a impressão do navegador.
   * Não há biblioteca de PDF no projeto: quem gera o arquivo é o próprio
   * navegador, pela opção "Salvar como PDF" da caixa de impressão.
   */
  function imprimirOrdem(id) {
    $.getJSON(API + '/ordens/' + id)
      .done(function (o) {
        let linhas = '';
        let total = 0;
        o.itens.forEach(function (it) {
          const subtotal = Number(it.valorUnitario) * it.quantidade;
          total += subtotal;
          linhas +=
            '<tr>' +
              '<td>' + esc(it.servicoDescricao) + '</td>' +
              '<td class="c">' + (it.servicoTipo === 'PECA' ? 'Peça' : 'Mão de obra') + '</td>' +
              '<td class="c">' + it.quantidade + '</td>' +
              '<td class="d">' + fmtMoeda(it.valorUnitario) + '</td>' +
              '<td class="d">' + fmtMoeda(subtotal) + '</td>' +
            '</tr>';
        });
        if (!o.itens.length) {
          linhas = '<tr><td colspan="5" class="c">Nenhum item lançado.</td></tr>';
        }

        $('#impressaoOs').html(
          '<div class="os-topo">' +
            '<h1>Oficina Mecânica</h1>' +
            '<div class="d"><strong>ORDEM DE SERVIÇO Nº ' + o.id + '</strong><br>' +
              (ROTULO_STATUS[o.status] || [o.status])[0] + '</div>' +
          '</div>' +
          '<div class="os-dados">' +
            '<div><h2>Cliente</h2>' + esc(o.clienteNome) + '</div>' +
            '<div><h2>Veículo</h2>' + esc(o.veiculoPlaca) + ' — ' + esc(o.veiculoDescricao) +
              (o.kmAtual ? '<br>' + o.kmAtual + ' km' : '') + '</div>' +
            '<div><h2>Datas</h2>Abertura: ' + fmtData(o.dataAbertura) +
              '<br>Conclusão: ' + fmtData(o.dataConclusao) + '</div>' +
          '</div>' +
          '<h2>Problema relatado</h2><p>' + esc(o.descricaoProblema) + '</p>' +
          '<h2>Serviços e peças</h2>' +
          '<table><thead><tr><th>Descrição</th><th class="c">Tipo</th><th class="c">Qtd.</th>' +
            '<th class="d">Valor unit.</th><th class="d">Subtotal</th></tr></thead>' +
            '<tbody>' + linhas + '</tbody></table>' +
          '<p class="os-total">Total: ' + fmtMoeda(total) + '</p>' +
          (o.observacoes ? '<h2>Observações</h2><p>' + esc(o.observacoes) + '</p>' : '') +
          '<div class="assinaturas">' +
            '<div>Responsável pela oficina</div>' +
            '<div>' + esc(o.clienteNome) + '</div>' +
          '</div>');

        window.print();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  }

  function carregarResumo() {
    $.getJSON(API + '/ordens/resumo').done(function (lista) {
      const $r = $('#resumoOs').empty();
      lista.forEach(function (l) {
        const r = ROTULO_STATUS[l.status] || [l.status, ''];
        $r.append(
          '<div class="col-6 col-md-3">' +
            '<div class="card p-3 h-100">' +
              '<div class="small text-secondary">' + r[0] + '</div>' +
              '<div class="fs-4 fw-semibold">' + l.quantidade + '</div>' +
              '<div class="small text-secondary">' + fmtMoeda(l.total) + '</div>' +
            '</div>' +
          '</div>');
      });
    });
  }

  function carregarOrdens() {
    const params = {};
    const busca = $('#buscaOrdem').val().trim();
    const status = $('#filtroStatus').val();
    if (busca) params.busca = busca;
    if (status) params.status = status;

    $.getJSON(API + '/ordens', params)
      .done(function (lista) {
        const $tb = $('#tabelaOrdens tbody').empty();
        $('#totalOrdens').text(lista.length + ' ordem(ns) de serviço');
        if (!lista.length) {
          $tb.append('<tr><td colspan="8" class="text-center text-secondary py-4">Nenhuma ordem de serviço encontrada.</td></tr>');
          return;
        }
        lista.forEach(function (o) {
          $tb.append(
            '<tr data-id="' + o.id + '">' +
              '<td class="text-secondary">' + o.id + '</td>' +
              '<td><span class="badge text-bg-dark">' + esc(o.veiculoPlaca) + '</span><br>' +
                  '<small class="text-secondary">' + esc(o.veiculoDescricao) + '</small></td>' +
              '<td>' + esc(o.clienteNome) + '</td>' +
              '<td class="problema">' + esc(o.descricaoProblema) + '</td>' +
              '<td class="text-nowrap">' + fmtData(o.dataAbertura) + '</td>' +
              '<td>' + badgeStatus(o.status) + '</td>' +
              '<td class="text-end fw-medium">' + fmtMoeda(o.valorTotal) + '</td>' +
              '<td class="text-end text-nowrap">' +
                '<button class="btn btn-sm btn-outline-secondary btn-imprimir" title="Imprimir"><i class="bi bi-printer"></i></button> ' +
                '<button class="btn btn-sm btn-outline-primary btn-editar" title="Abrir"><i class="bi bi-pencil"></i></button> ' +
                '<button class="btn btn-sm btn-outline-danger btn-excluir" title="Excluir"><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
    carregarResumo();
  }

  /** Carrega os <select> de veículo e de itens do catálogo (apenas ativos). */
  function carregarSelectsOrdem(veiculoSelecionado) {
    const p1 = $.getJSON(API + '/veiculos').done(function (lista) {
      const $sel = $('#ordemVeiculo').empty().append('<option value="">Selecione…</option>');
      lista.forEach(function (v) {
        $sel.append($('<option>').val(v.id).text(v.placa + ' — ' + v.marca + ' ' + v.modelo + ' (' + v.clienteNome + ')'));
      });
      if (veiculoSelecionado) $sel.val(veiculoSelecionado);
    });

    const p2 = $.getJSON(API + '/servicos', { ativos: true }).done(function (lista) {
      catalogoServicos = lista;
      const $sel = $('#itemServico').empty();
      lista.forEach(function (s) {
        $sel.append($('<option>').val(s.id).text(s.descricao + ' — ' + fmtMoeda(s.preco)));
      });
    });

    return $.when(p1, p2);
  }

  function renderItens() {
    const $tb = $('#tabelaItens tbody').empty();
    if (!itensEmEdicao.length) {
      $tb.append('<tr><td colspan="5" class="text-center text-secondary py-3">Nenhum item lançado ainda.</td></tr>');
    }
    let total = 0;
    itensEmEdicao.forEach(function (it, indice) {
      const subtotal = Number(it.valorUnitario) * it.quantidade;
      total += subtotal;
      $tb.append(
        '<tr data-indice="' + indice + '">' +
          '<td>' + esc(it.servicoDescricao) + '</td>' +
          '<td class="text-center">' + it.quantidade + '</td>' +
          '<td class="text-end">' + fmtMoeda(it.valorUnitario) + '</td>' +
          '<td class="text-end">' + fmtMoeda(subtotal) + '</td>' +
          '<td class="text-end"><button type="button" class="btn btn-sm btn-outline-danger btn-remover-item" title="Remover"><i class="bi bi-x-lg"></i></button></td>' +
        '</tr>');
    });
    $('#totalOs').text(fmtMoeda(total));
  }

  function abrirModalOrdem(o) {
    const $f = $('#formOrdem');
    $f[0].reset();
    $f.removeClass('was-validated');
    $('#ordemId').val(o ? o.id : '');
    $('#tituloModalOrdem').text(o ? 'Ordem de serviço #' + o.id : 'Nova ordem de serviço');
    itensEmEdicao = o ? o.itens.slice() : [];

    carregarSelectsOrdem(o ? o.veiculoId : null).done(function () {
      if (o) {
        $('#ordemStatus').val(o.status);
        $('#descricaoProblema').val(o.descricaoProblema);
        $('#kmAtual').val(o.kmAtual);
        $('#observacoes').val(o.observacoes);
      }
      renderItens();
      modalOrdem.show();
    });
  }

  $('#btnNovaOrdem').on('click', function () { abrirModalOrdem(null); });
  $('#buscaOrdem').on('input', debounce(carregarOrdens, 250));
  $('#filtroStatus').on('change', carregarOrdens);

  $('#tabelaOrdens').on('click', '.btn-imprimir', function () {
    imprimirOrdem($(this).closest('tr').data('id'));
  });

  $('#tabelaOrdens').on('click', '.btn-editar', function () {
    $.getJSON(API + '/ordens/' + $(this).closest('tr').data('id'))
      .done(abrirModalOrdem)
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaOrdens').on('click', '.btn-excluir', function () {
    const id = $(this).closest('tr').data('id');
    if (!window.confirm('Excluir a ordem de serviço #' + id + ' e todos os seus itens?')) return;
    $.ajax({ url: API + '/ordens/' + id, type: 'DELETE' })
      .done(function () { toast('Ordem de serviço excluída'); carregarOrdens(); })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#btnAdicionarItem').on('click', function () {
    const servicoId = Number($('#itemServico').val());
    const quantidade = Number($('#itemQuantidade').val());
    if (!servicoId) { toast('Selecione um item do catálogo', 'danger'); return; }
    if (!(quantidade >= 1)) { toast('Quantidade deve ser no mínimo 1', 'danger'); return; }
    if (itensEmEdicao.some(function (i) { return Number(i.servicoId) === servicoId; })) {
      toast('Esse item já está na ordem de serviço', 'danger');
      return;
    }

    const ordemId = $('#ordemId').val();
    if (ordemId) {
      // OS já existe: grava o item direto na API
      $.ajax({ url: API + '/ordens/' + ordemId + '/itens', type: 'POST',
               data: { servicoId: servicoId, quantidade: quantidade } })
        .done(function (os) { itensEmEdicao = os.itens; renderItens(); $('#itemQuantidade').val(1); })
        .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
    } else {
      // OS nova: acumula em memória; tudo é gravado junto, em transação, no submit
      const s = catalogoServicos.find(function (x) { return Number(x.id) === servicoId; });
      itensEmEdicao.push({ servicoId: s.id, servicoDescricao: s.descricao, quantidade: quantidade, valorUnitario: s.preco });
      renderItens();
      $('#itemQuantidade').val(1);
    }
  });

  $('#tabelaItens').on('click', '.btn-remover-item', function () {
    const indice = $(this).closest('tr').data('indice');
    const item = itensEmEdicao[indice];
    const ordemId = $('#ordemId').val();

    if (ordemId && item.id) {
      $.ajax({ url: API + '/ordens/' + ordemId + '/itens/' + item.id, type: 'DELETE' })
        .done(function (os) { itensEmEdicao = os.itens; renderItens(); })
        .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
    } else {
      itensEmEdicao.splice(indice, 1);
      renderItens();
    }
  });

  $('#formOrdem').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    const id = $('#ordemId').val();
    const $btn = $('#btnSalvarOrdem').prop('disabled', true);

    let dados = $(this).serialize();
    if (!id) {
      // Envia os itens junto do cabeçalho: o servidor grava tudo em uma transação
      itensEmEdicao.forEach(function (it) {
        dados += '&servicoId=' + encodeURIComponent(it.servicoId) +
                 '&quantidade=' + encodeURIComponent(it.quantidade);
      });
    }

    $.ajax({ url: id ? API + '/ordens/' + id : API + '/ordens', type: id ? 'PUT' : 'POST', data: dados })
      .done(function () {
        modalOrdem.hide();
        toast(id ? 'Ordem de serviço atualizada' : 'Ordem de serviço criada');
        carregarOrdens();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });

  // ------------------------------------------------------------ USUÁRIOS (ADMIN)

  const modalUsuario = new bootstrap.Modal('#modalUsuario');
  const modalRedefinirSenha = new bootstrap.Modal('#modalRedefinirSenha');
  const modalMinhaSenha = new bootstrap.Modal('#modalMinhaSenha');

  function carregarUsuarios() {
    const busca = $('#buscaUsuario').val().trim();
    $.getJSON(API + '/usuarios', busca ? { busca: busca } : {})
      .done(function (lista) {
        const $tb = $('#tabelaUsuarios tbody').empty();
        $('#totalUsuarios').text(lista.length + ' usuário(s)');
        if (!lista.length) {
          $tb.append('<tr><td colspan="7" class="text-center text-secondary py-4">Nenhum usuário encontrado.</td></tr>');
          return;
        }
        lista.forEach(function (u) {
          const situacao = !u.ativo
            ? '<span class="badge text-bg-secondary">Inativo</span>'
            : (u.bloqueado
                ? '<span class="badge text-bg-danger" title="Tentativas de senha erradas">Bloqueado</span>'
                : '<span class="badge text-bg-success">Ativo</span>');
          const euMesmo = Number(u.id) === Number(sessao.id);
          $tb.append(
            '<tr data-id="' + u.id + '">' +
              '<td class="text-secondary">' + u.id + '</td>' +
              '<td class="fw-medium nome">' + esc(u.nome) +
                (euMesmo ? ' <span class="badge text-bg-light border">você</span>' : '') + '</td>' +
              '<td>' + esc(u.email) + '</td>' +
              '<td>' + (u.perfil === 'ADMIN'
                ? '<span class="badge text-bg-dark">Administrador</span>'
                : '<span class="badge text-bg-light border">Atendente</span>') + '</td>' +
              '<td>' + situacao + '</td>' +
              '<td class="text-nowrap small text-secondary">' + (u.ultimoAcesso ? fmtData(u.ultimoAcesso) : 'nunca entrou') + '</td>' +
              '<td class="text-end text-nowrap">' +
                '<button class="btn btn-sm btn-outline-secondary btn-senha" title="Redefinir a senha"><i class="bi bi-key"></i></button> ' +
                '<button class="btn btn-sm btn-outline-primary btn-editar" title="Editar"><i class="bi bi-pencil"></i></button> ' +
                '<button class="btn btn-sm btn-outline-danger btn-excluir" title="Excluir"' +
                  (euMesmo ? ' disabled' : '') + '><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  }

  function abrirModalUsuario(u) {
    const $f = $('#formUsuario');
    $f[0].reset();
    $f.removeClass('was-validated');
    $('#usuarioId').val(u ? u.id : '');
    $('#tituloModalUsuario').text(u ? 'Editar usuário #' + u.id : 'Novo usuário');
    // A senha só é pedida na criação; depois ela se troca pelo botão da chave.
    $('#campoSenhaNovoUsuario').toggleClass('d-none', !!u);
    $('#usuarioSenha').prop('required', !u);
    if (u) {
      $('#usuarioNomeCampo').val(u.nome);
      $('#usuarioEmailCampo').val(u.email);
      $('#usuarioPerfilCampo').val(u.perfil);
      $('#usuarioAtivo').prop('checked', u.ativo);
    } else {
      $('#usuarioAtivo').prop('checked', true);
    }
    modalUsuario.show();
  }

  $('#btnNovoUsuario').on('click', function () { abrirModalUsuario(null); });
  $('#buscaUsuario').on('input', debounce(carregarUsuarios, 250));

  $('#tabelaUsuarios').on('click', '.btn-editar', function () {
    $.getJSON(API + '/usuarios/' + $(this).closest('tr').data('id'))
      .done(abrirModalUsuario)
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaUsuarios').on('click', '.btn-excluir', function () {
    const $tr = $(this).closest('tr');
    if (!window.confirm('Excluir o usuário "' + $tr.find('.nome').text().replace(' você', '') + '"?')) return;
    $.ajax({ url: API + '/usuarios/' + $tr.data('id'), type: 'DELETE' })
      .done(function () { toast('Usuário excluído'); carregarUsuarios(); })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
  });

  $('#tabelaUsuarios').on('click', '.btn-senha', function () {
    const $tr = $(this).closest('tr');
    $('#formRedefinirSenha')[0].reset();
    $('#formRedefinirSenha').removeClass('was-validated');
    $('#redefinirUsuarioId').val($tr.data('id'));
    $('#redefinirDescricao').text('Definindo uma nova senha para ' + $tr.find('.nome').text().replace(' você', '') + '.');
    modalRedefinirSenha.show();
  });

  $('#formUsuario').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    const id = $('#usuarioId').val();
    const $btn = $('#btnSalvarUsuario').prop('disabled', true);
    $.ajax({
      url: id ? API + '/usuarios/' + id : API + '/usuarios',
      type: id ? 'PUT' : 'POST',
      data: $(this).serialize() + '&ativo=' + $('#usuarioAtivo').is(':checked')
    })
      .done(function () {
        modalUsuario.hide();
        toast(id ? 'Usuário atualizado' : 'Usuário cadastrado');
        carregarUsuarios();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });

  $('#formRedefinirSenha').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    const id = $('#redefinirUsuarioId').val();
    const $btn = $('#btnSalvarRedefinicao').prop('disabled', true);
    $.ajax({ url: API + '/usuarios/' + id + '/senha', type: 'PUT', data: $(this).serialize() })
      .done(function () {
        modalRedefinirSenha.hide();
        toast('Senha redefinida. A pessoa precisa entrar de novo.');
        carregarUsuarios();
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });

  // ------------------------------------------------------------ trocar a própria senha

  $('#btnTrocarSenha').on('click', function () {
    $('#formMinhaSenha')[0].reset();
    $('#formMinhaSenha').removeClass('was-validated');
    modalMinhaSenha.show();
  });

  $('#formMinhaSenha').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    if ($('#novaSenha').val() !== $('#confirmaSenha').val()) {
      toast('A confirmação não confere com a nova senha.', 'danger');
      return;
    }
    const $btn = $('#btnSalvarMinhaSenha').prop('disabled', true);
    $.ajax({ url: API + '/auth/senha', type: 'POST',
             data: { senhaAtual: $('#senhaAtual').val(), novaSenha: $('#novaSenha').val() } })
      .done(function (nova) {
        sessao = nova;                    // a troca de senha abre uma sessão nova
        modalMinhaSenha.hide();
        toast('Senha alterada. As outras sessões foram encerradas.');
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); })
      .always(function () { $btn.prop('disabled', false); });
  });

  // ------------------------------------------------------------ início

  /* Nada de tela antes de saber quem está do outro lado: se não houver sessão,
     o servidor responde 401 e o navegador vai para o login. */
  $.getJSON(API + '/auth/sessao')
    .done(function (s) {
      sessao = s;
      aplicarSessao();
      restaurarMenu();
      irPara('dashboard');
    })
    .fail(function () {
      window.location.replace('/login.html');
    });

})(jQuery);
