package br.com.saneia.api.panel;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import br.com.saneia.api.insight.OperationsAiService;
import br.com.saneia.api.report.ReportAdminService;
import br.com.saneia.api.report.ReportOutcome;
import br.com.saneia.api.report.ReportPriority;
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
    private final ReportAdminService reportAdmin;

    public PanelController(PanelService panel, ReportService reports, ReportAdminService reportAdmin) {
        this.panel = panel;
        this.reports = reports;
        this.reportAdmin = reportAdmin;
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
            @RequestParam(defaultValue = "false") boolean excluidos, @RequestParam(defaultValue = "false") boolean atrasados,
            @RequestParam(required = false) String prioridade, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", "chamados");
        model.addAttribute("chamados", panel.reports(status, q, excluidos, atrasados, prioridade, page));
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("excluidos", excluidos);
        model.addAttribute("atrasados", atrasados);
        model.addAttribute("prioridade", prioridade == null ? "" : prioridade);
        model.addAttribute("equipe", panel.staffOptions());
        return "painel/chamados";
    }

    @GetMapping("/chamados.csv")
    public ResponseEntity<byte[]> reportsCsv(@RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean excluidos,
            @RequestParam(defaultValue = "false") boolean atrasados, @RequestParam(required = false) String prioridade) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chamados-saneia.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(panel.reportsCsv(status, q, excluidos, atrasados, prioridade));
    }

    @PostMapping("/chamados/lote")
    public String batch(@RequestParam(name = "ids", required = false) List<Long> ids, @RequestParam String acao,
            @RequestParam(required = false) String resolution, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String assigneeId, @RequestParam(required = false) String priority,
            Principal principal, RedirectAttributes redirect) {
        try {
            ReportAdminService.BatchResult result = reportAdmin.batch(ids, acao, resolution,
                    StringUtils.hasText(outcome) ? ReportOutcome.fromApi(outcome) : null, parseId(assigneeId),
                    StringUtils.hasText(priority) ? ReportPriority.fromApi(priority) : null,
                    panel.actorName(principal == null ? null : principal.getName()));
            if (result.changed() > 0) {
                redirect.addFlashAttribute("sucesso", result.changed() + " chamado(s) atualizado(s) em lote.");
            }
            if (!result.errors().isEmpty()) {
                redirect.addFlashAttribute("erro", "Não alterados: " + String.join(" · ", result.errors()));
            }
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/chamados";
    }

    @GetMapping("/chamados/{id}")
    public String report(@PathVariable Long id, Model model) {
        model.addAttribute("pagina", "chamados");
        PanelViews.ReportDetail detail = panel.report(id);
        model.addAttribute("c", detail);
        if (!model.containsAttribute("statusForm")) {
            PanelForms.StatusForm form = new PanelForms.StatusForm();
            Object suggestion = model.getAttribute("sugestao");
            if (suggestion != null && "desfecho".equals(model.getAttribute("sugestaoTipo"))) {
                form.setResolution(suggestion.toString());
            } else if (suggestion != null) {
                form.setProgressNote(suggestion.toString());
            }
            model.addAttribute("statusForm", form);
        }
        if (!model.containsAttribute("editForm")) {
            PanelForms.EditForm edit = new PanelForms.EditForm();
            edit.setCategory(detail.row().category());
            edit.setDescription(detail.description());
            edit.setAddress(detail.row().address());
            edit.setNeighborhood(detail.row().neighborhood());
            edit.setPriority(detail.row().priority());
            edit.setAssigneeId(detail.assigneeId() == null ? "" : String.valueOf(detail.assigneeId()));
            edit.setSensorMode(detail.sensorLocked() ? (detail.sensorId() == null ? "none" : String.valueOf(detail.sensorId()))
                    : "auto");
            model.addAttribute("editForm", edit);
        }
        return "painel/chamado";
    }

    @PostMapping("/chamados/{id}/status")
    public String updateStatus(@PathVariable Long id, @Valid @ModelAttribute("statusForm") PanelForms.StatusForm form,
            BindingResult binding, Principal principal, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/painel/chamados/" + id;
        }
        try {
            reports.updateStatusFromPanel(id, new StatusUpdateRequest(ReportStatus.fromApi(form.getStatus()),
                    form.getResolution(), form.getProgressNote(),
                    StringUtils.hasText(form.getOutcome()) ? ReportOutcome.fromApi(form.getOutcome()) : null),
                    panel.actorName(principal == null ? null : principal.getName()));
            redirect.addFlashAttribute("sucesso", "Atendimento atualizado. O morador será notificado.");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/chamados/" + id;
    }

    @PostMapping("/chamados/{id}/editar")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("editForm") PanelForms.EditForm form,
            BindingResult binding, Principal principal, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/painel/chamados/" + id;
        }
        try {
            reportAdmin.edit(id, new ReportAdminService.Edit(form.getCategory(), form.getDescription(), form.getAddress(),
                    form.getNeighborhood(), ReportPriority.fromApi(form.getPriority()), parseId(form.getAssigneeId()),
                    form.getSensorMode()), panel.actorName(principal == null ? null : principal.getName()));
            redirect.addFlashAttribute("sucesso", "Chamado atualizado. As mudanças foram registradas no histórico.");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/chamados/" + id;
    }

    @PostMapping("/chamados/{id}/reabrir")
    public String reopen(@PathVariable Long id, @RequestParam String motivo, Principal principal,
            RedirectAttributes redirect) {
        try {
            reports.reopenFromPanel(id, motivo, panel.actorName(principal == null ? null : principal.getName()));
            redirect.addFlashAttribute("sucesso", "Chamado reaberto. O morador foi avisado.");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/chamados/" + id;
    }

    @PostMapping("/chamados/{id}/nota")
    public String internalNote(@PathVariable Long id, @RequestParam String nota, Principal principal,
            RedirectAttributes redirect) {
        try {
            reportAdmin.addInternalNote(id, nota, panel.actorName(principal == null ? null : principal.getName()));
            redirect.addFlashAttribute("sucesso", "Nota interna registrada (não aparece para o morador).");
        } catch (ApiException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/painel/chamados/" + id;
    }

    @PostMapping("/chamados/{id}/sugestao")
    public String suggest(@PathVariable Long id, @RequestParam(defaultValue = "andamento") String tipo,
            RedirectAttributes redirect) {
        OperationsAiService.AiText text = panel.suggestReply(id, tipo);
        redirect.addFlashAttribute("sugestao", text.text());
        redirect.addFlashAttribute("sugestaoTipo", "desfecho".equals(tipo) ? "desfecho" : "andamento");
        redirect.addFlashAttribute("sucesso", text.aiGenerated()
                ? "Texto sugerido pela IA. Revise antes de enviar ao morador."
                : "Texto-modelo preenchido (IA indisponível: configure GEMINI_API_KEY). Revise antes de enviar.");
        return "redirect:/painel/chamados/" + id;
    }

    @GetMapping("/mapa")
    public String map(Model model) {
        model.addAttribute("pagina", "mapa");
        model.addAttribute("pontos", panel.mapPoints());
        return "painel/mapa";
    }

    @PostMapping("/boletim")
    public String bulletin(RedirectAttributes redirect) {
        OperationsAiService.AiText text = panel.bulletin();
        redirect.addFlashAttribute("boletim", text.text());
        redirect.addFlashAttribute("boletimIa", text.aiGenerated());
        return "redirect:/painel";
    }

    private static Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Identificador inválido.");
        }
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
