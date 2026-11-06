package br.com.saneia.api.report;

import jakarta.validation.constraints.Size;

/** Complemento do morador (note) ou motivo da avaliação técnica (operadora). */
public record NoteRequest(@Size(max = 600, message = "Use até 600 caracteres.") String note) {
}
