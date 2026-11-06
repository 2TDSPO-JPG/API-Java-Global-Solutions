package br.com.saneia.api.panel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import br.com.saneia.api.common.ApiException;

/** Erros do painel viram uma página amigável (as rotas REST continuam com o JSON do GlobalExceptionHandler). */
@ControllerAdvice(assignableTypes = PanelController.class)
public class PanelExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PanelExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public String handleApi(ApiException ex, Model model) {
        model.addAttribute("mensagem", ex.getMessage());
        model.addAttribute("codigo", ex.getStatus().value());
        return "painel/erro";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, Model model) {
        model.addAttribute("mensagem", "Filtro ou valor inválido.");
        model.addAttribute("codigo", 400);
        return "painel/erro";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception ex, Model model) {
        log.error("Erro no painel", ex);
        model.addAttribute("mensagem", "Erro inesperado. Tente novamente.");
        model.addAttribute("codigo", 500);
        return "painel/erro";
    }
}
