package br.com.saneia.api.panel;

import java.security.Principal;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.saneia.api.alert.EmailLog;
import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.report.ReportService;
import br.com.saneia.api.report.ReportStatus;
import br.com.saneia.api.report.StatusUpdateRequest;

/**
 * Painel administrativo em Thymeleaf (/api/painel): visão total dos dados, estado dos chamados,
 * risco por área, equipamentos, usuários e alertas por e-mail. Acesso por login de administrador (sessão).
 */
@Controller
@RequestMapping("/painel")
@Hidden
public class PanelController {

    private final PanelService panel;
    private final ReportService reports;

    public PanelController(PanelService panel, ReportService reports) {
        this.panel = panel;
        this.reports = reports;
    }

    @ModelAttribute("usuarioLogado")
    public String loggedUser(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    @GetMapping("/login")
    public String login() {
        return "painel/login";
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("pagina", "inicio");
        model.addAttribute("d", panel.dashboard());
        model.addAttribute("categorias", panel.openByCategory());
        return "painel/dashboard";
    }

    @GetMapping("/areas")
    public String areas(Model model) {
        model.addAttribute("pagina", "areas");
        model.addAttribute("areas", panel.areas());
        return "painel/areas";
    }

    @GetMapping("/chamados")
    public String reports(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean excluidos, @RequestParam(defaultValue = "0") int page,
            Model model) {
        model.addAttribute("pagina", "chamados");
        model.addAttribute("chamados", panel.reports(status, q, excluidos, page));
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("excluidos", excluidos);
        return "painel/chamados";
    }

    @GetMapping("/chamados/{id}")
    public String report(@PathVariable Long id, Model model) {
        model.addAttribute("pagina", "chamados");
        model.addAttribute("c", panel.report(id));
        if (!model.containsAttribute("statusForm")) {
            model.addAttribute("statusForm", new PanelForms.StatusForm());
        }
        return "painel/chamado";
    }

    @PostMapping("/chamados/{id}/status")
    public String updateStatus(@PathVariable Long id, @Valid @ModelAttribute("statusForm") PanelForms.StatusForm form,
            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/painel/chamados/" + id;
        }
        try {
            reports.updateStatusFromPanel(id, new StatusUpdateRequest(ReportStatus.fromApi(form.getStatus()),
                    form.getResolution(), form.getProgressNote()));
            redirect.addFlashAttribute("sucesso", "Atendimento atualizado. O morador será notificado.");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/chamados/" + id;
    }

    @GetMapping("/equipamentos")
    public String sensors(Model model) {
        model.addAttribute("pagina", "equipamentos");
        model.addAttribute("equipamentos", panel.sensors());
        return "painel/equipamentos";
    }

    @GetMapping("/equipamentos/{id}")
    public String sensor(@PathVariable Long id, Model model) {
        model.addAttribute("pagina", "equipamentos");
        model.addAttribute("e", panel.sensor(id));
        return "painel/equipamento";
    }

    @GetMapping("/usuarios")
    public String users(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", "usuarios");
        model.addAttribute("usuarios", panel.users(page));
        if (!model.containsAttribute("novoUsuario")) {
            model.addAttribute("novoUsuario", new PanelForms.NewUser());
        }
        return "painel/usuarios";
    }

    @PostMapping("/usuarios")
    public String createUser(@Valid @ModelAttribute("novoUsuario") PanelForms.NewUser form, BindingResult binding,
            Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("pagina", "usuarios");
            model.addAttribute("usuarios", panel.users(0));
            return "painel/usuarios";
        }
        try {
            panel.createUser(form);
            redirect.addFlashAttribute("sucesso", "Conta criada para " + form.getEmail() + ".");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/usuarios";
    }

    @GetMapping("/alertas")
    public String alerts(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", "alertas");
        model.addAttribute("destinatarios", panel.recipients());
        model.addAttribute("envios", panel.emailLogs(page));
        model.addAttribute("smtp", panel.smtpConfigured());
        if (!model.containsAttribute("novoDestinatario")) {
            model.addAttribute("novoDestinatario", new PanelForms.NewRecipient());
        }
        return "painel/alertas";
    }

    @PostMapping("/alertas")
    public String addRecipient(@Valid @ModelAttribute("novoDestinatario") PanelForms.NewRecipient form,
            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/painel/alertas";
        }
        try {
            panel.addRecipient(form);
            redirect.addFlashAttribute("sucesso", form.getEmail() + " passa a receber os alertas de risco máximo.");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/alertas";
    }

    @PostMapping("/alertas/{id}/alternar")
    public String toggleRecipient(@PathVariable Long id, RedirectAttributes redirect) {
        panel.toggleRecipient(id);
        redirect.addFlashAttribute("sucesso", "Destinatário atualizado.");
        return "redirect:/painel/alertas";
    }

    @PostMapping("/alertas/{id}/remover")
    public String removeRecipient(@PathVariable Long id, RedirectAttributes redirect) {
        panel.removeRecipient(id);
        redirect.addFlashAttribute("sucesso", "Destinatário removido.");
        return "redirect:/painel/alertas";
    }

    @PostMapping("/alertas/teste")
    public String testEmail(@RequestParam String email, RedirectAttributes redirect) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            redirect.addFlashAttribute("erro", "Informe um e-mail válido para o teste.");
            return "redirect:/painel/alertas";
        }
        EmailLog result = panel.sendTest(email);
        switch (result.getStatus()) {
            case SENT -> redirect.addFlashAttribute("sucesso", "E-mail de teste enviado para " + email + ".");
            case FAILED -> redirect.addFlashAttribute("erro", "Falha no envio: " + result.getErrorMessage());
            default -> redirect.addFlashAttribute("erro", result.getErrorMessage());
        }
        return "redirect:/painel/alertas";
    }
}
