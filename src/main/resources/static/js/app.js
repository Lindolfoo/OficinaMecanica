/* Oficina Mecânica — front-end em jQuery puro (sem frameworks SPA).
 * Cada módulo (Clientes, Veículos, Serviços, OS) segue o mesmo padrão:
 *   carregar()  -> GET lista e monta a tabela
 *   abrirModal() -> preenche o formulário (novo ou edição)
 *   submit       -> POST (novo) ou PUT (edição)
 *   excluir      -> DELETE após confirmação
 */
(function ($) {
  'use strict';

  const API = '/api';

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
                '<button class="btn btn-sm btn-outline-primary btn-editar" title="Editar"><i class="bi bi-pencil"></i></button> ' +
                '<button class="btn btn-sm btn-outline-danger btn-excluir" title="Excluir"><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>');
        });
      })
      .fail(function (xhr) { toast(erroDe(xhr), 'danger'); });
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

  // ------------------------------------------------------------ inicialização
  carregarClientes();

  const jaCarregada = { tabClientes: true };
  $('#abas button').on('shown.bs.tab', function (e) {
    const alvo = $(e.target).data('bs-target').substring(1);
    if (jaCarregada[alvo]) return;
    jaCarregada[alvo] = true;
    if (alvo === 'tabVeiculos') carregarVeiculos();
    if (alvo === 'tabServicos') carregarServicos();
  });

})(jQuery);
