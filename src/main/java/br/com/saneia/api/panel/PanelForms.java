package br.com.saneia.api.panel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Formulários do painel (JavaBeans para o binding do Thymeleaf com Bean Validation). */
public final class PanelForms {

    private PanelForms() {
    }

    public static class NewUser {
        @NotBlank(message = "Informe o nome.")
        @Size(min = 2, max = 120, message = "O nome deve ter de 2 a 120 caracteres.")
        private String name;

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 160)
        private String email;

        @NotBlank(message = "Informe a senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter de 8 a 100 caracteres.")
        private String password;

        @Pattern(regexp = "operator|admin|resident", message = "Escolha o papel.")
        private String role = "operator";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class NewRecipient {
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120)
        private String name;

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 160)
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class StatusForm {
        @Pattern(regexp = "received|in_progress|closed", message = "Escolha o status.")
        private String status;

        @Size(max = 600, message = "O desfecho deve ter até 600 caracteres.")
        private String resolution;

        @Size(max = 600, message = "A nota deve ter até 600 caracteres.")
        private String progressNote;

        @Pattern(regexp = "|resolved|stabilized|unfounded|forwarded", message = "Escolha o tipo de desfecho.")
        private String outcome = "resolved";

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getResolution() {
            return resolution;
        }

        public void setResolution(String resolution) {
            this.resolution = resolution;
        }

        public String getProgressNote() {
            return progressNote;
        }

        public void setProgressNote(String progressNote) {
            this.progressNote = progressNote;
        }
    }

    public static class EditForm {
        @NotBlank(message = "Escolha a categoria.")
        private String category;

        @NotBlank(message = "Informe a descrição.")
        @Size(max = 1000, message = "A descrição deve ter até 1000 caracteres.")
        private String description;

        @NotBlank(message = "Informe o endereço.")
        @Size(max = 200, message = "O endereço deve ter até 200 caracteres.")
        private String address;

        @NotBlank(message = "Informe o bairro.")
        @Size(max = 80, message = "O bairro deve ter até 80 caracteres.")
        private String neighborhood;

        @Pattern(regexp = "low|medium|high|critical", message = "Escolha a prioridade.")
        private String priority;

        private String assigneeId;

        private String sensorMode;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getNeighborhood() {
            return neighborhood;
        }

        public void setNeighborhood(String neighborhood) {
            this.neighborhood = neighborhood;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(String assigneeId) {
            this.assigneeId = assigneeId;
        }

        public String getSensorMode() {
            return sensorMode;
        }

        public void setSensorMode(String sensorMode) {
            this.sensorMode = sensorMode;
        }
    }
}
