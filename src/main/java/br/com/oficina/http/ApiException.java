package br.com.oficina.http;

/** Erro previsto da API: vira uma resposta JSON {"erro": mensagem} com o status HTTP informado. */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
