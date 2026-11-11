package br.com.saneia.api.sensor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Colunas do endereço estruturado em sn_sensors (todas nulas nos cadastros legados). */
@Embeddable
public class SensorPostalAddress {

    @Column(name = "postal_cep", length = 8)
    private String cep;

    @Column(name = "postal_street", length = 120)
    private String street;

    @Column(name = "postal_number", length = 10)
    private String number;

    @Column(name = "postal_neighborhood", length = 80)
    private String neighborhood;

    @Column(name = "postal_city", length = 80)
    private String city;

    @Column(name = "postal_state", length = 2)
    private String state;

    @Column(name = "postal_complement", length = 80)
    private String complement;

    protected SensorPostalAddress() {
    }

    static SensorPostalAddress of(PostalAddress address) {
        SensorPostalAddress value = new SensorPostalAddress();
        value.cep = address.cep();
        value.street = address.street();
        value.number = address.number();
        value.neighborhood = address.neighborhood();
        value.city = address.city();
        value.state = address.state();
        value.complement = address.complement();
        return value;
    }

    /** Nulo quando o cadastro é legado (só endereço livre). */
    PostalAddress toDto() {
        if (cep == null && street == null) {
            return null;
        }
        return new PostalAddress(cep, street, number, neighborhood, city, state, complement);
    }
}
