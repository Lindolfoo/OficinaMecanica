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


  // ------------------------------------------------------------ inicialização
  carregarClientes();

})(jQuery);
