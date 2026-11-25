package br.com.mindhaven.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Vínculo entre um paciente e um profissional que passou a atendê-lo. */
@Entity
@Table(name = "patient_professional")
@IdClass(PatientLinkEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class PatientLinkEntity {
    @Id
    @Column(name = "patient_id")
    private UUID patientId;

    @Id
    @Column(name = "professional_id")
    private UUID professionalId;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    public static class Key implements Serializable {
        private UUID patientId;
        private UUID professionalId;

        public Key() {
        }

        public Key(UUID patientId, UUID professionalId) {
            this.patientId = patientId;
            this.professionalId = professionalId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(patientId, key.patientId)
                    && Objects.equals(professionalId, key.professionalId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patientId, professionalId);
        }
    }
}
